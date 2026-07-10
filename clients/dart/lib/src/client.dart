import 'dart:async';

import 'transport.dart';
import 'types.dart';

export 'transport.dart';
export 'types.dart';

/// krom-parser daemon client — see krom-parser/api.md.
class ParserClient {
  ParserClient._(this._transport) {
    _transport.messages.listen(_route);
  }

  final ParserTransport _transport;
  int _nextId = 1;
  final Map<int, Completer<Map<String, dynamic>?>> _pending = {};
  final Completer<void> _ready = Completer<void>();
  final Set<String> _loadedLanguageIds = {};

  static Future<ParserClient> start({required List<String> command}) async {
    final transport = await ParserTransport.start(command);
    final client = ParserClient._(transport);
    await client._ready.future.timeout(
      const Duration(seconds: 5),
      onTimeout: () => throw TimeoutException('krom-parser ready timeout'),
    );
    return client;
  }

  bool hasLanguage(String languageId) => _loadedLanguageIds.contains(languageId);

  Future<String> ping() async {
    final response = await _request('ping', {});
    final result = response?['result'];
    return result is String ? result : '';
  }

  Future<List<ParserLanguageInfo>> listLanguages() async {
    final response = await _request('listLanguages', {});
    final result = response?['result'];
    if (result is! List) return const [];

    final languages = result
        .map((l) => ParserLanguageInfo.fromJson(l as Map<String, dynamic>))
        .toList();

    _loadedLanguageIds
      ..clear()
      ..addAll(
        languages.where((l) => l.loaded).map((l) => l.id),
      );

    return languages;
  }

  Future<ParserParseResult> parseFile({
    required String fileId,
    required String languageId,
    required String content,
  }) async {
    final response = await _request('parseFile', {
      'fileId': fileId,
      'languageId': languageId,
      'content': content,
    });
    final result = response?['result'];
    if (result is Map<String, dynamic>) {
      return ParserParseResult.fromJson(result);
    }
    final error = response?['error'] as Map<String, dynamic>?;
    return ParserParseResult(
      success: false,
      error: error?['message'] as String? ?? 'parseFile failed',
    );
  }

  Future<ParserParseResult> updateFile({
    required String fileId,
    required String content,
  }) async {
    final response = await _request('updateFile', {
      'fileId': fileId,
      'content': content,
    });
    final result = response?['result'];
    if (result is Map<String, dynamic>) {
      return ParserParseResult.fromJson(result);
    }
    final error = response?['error'] as Map<String, dynamic>?;
    return ParserParseResult(
      success: false,
      error: error?['message'] as String? ?? 'updateFile failed',
    );
  }

  Future<void> closeFile({required String fileId}) async {
    await _request('closeFile', {'fileId': fileId});
  }

  Future<ParserNodeInfo?> getNodeAtPosition({
    required String fileId,
    required int line,
    required int column,
  }) async {
    final response = await _request('getNodeAtPosition', {
      'fileId': fileId,
      'line': line,
      'column': column,
    });
    final result = response?['result'];
    if (result == null) return null;
    if (result is! Map<String, dynamic>) return null;
    return ParserNodeInfo.fromJson(result);
  }

  Future<List<ParserSymbolInfo>> getStructure({required String fileId}) async {
    final response = await _request('getStructure', {'fileId': fileId});
    final result = response?['result'];
    if (result is! List) return const [];
    return result
        .map((s) => ParserSymbolInfo.fromJson(s as Map<String, dynamic>))
        .toList();
  }

  Future<ParserHighlightResult> getHighlights({required String fileId}) async {
    final response = await _request('getHighlights', {'fileId': fileId});
    final result = response?['result'];
    if (result is Map<String, dynamic>) {
      return ParserHighlightResult.fromJson(result);
    }
    return const ParserHighlightResult(spans: []);
  }

  Future<List<ParserNodeInfo>> runQuery({
    required String fileId,
    required String query,
  }) async {
    final response = await _request('runQuery', {
      'fileId': fileId,
      'query': query,
    });
    final result = response?['result'];
    if (result is! List) return const [];
    return result
        .map((n) => ParserNodeInfo.fromJson(n as Map<String, dynamic>))
        .toList();
  }

  Future<void> shutdown() async {
    await _request('shutdown', {});
    for (final c in _pending.values) {
      if (!c.isCompleted) c.complete(null);
    }
    _pending.clear();
    await _transport.dispose();
  }

  Future<Map<String, dynamic>?> _request(
    String method,
    Map<String, dynamic> params,
  ) {
    final id = _nextId++;
    final completer = Completer<Map<String, dynamic>?>();
    _pending[id] = completer;
    _transport.send({'id': id, 'method': method, 'params': params});
    return completer.future.timeout(
      const Duration(seconds: 10),
      onTimeout: () {
        _pending.remove(id);
        return null;
      },
    );
  }

  void _route(Map<String, dynamic> msg) {
    final id = msg['id'];
    if (id == 0 && !_ready.isCompleted) {
      _ready.complete();
      return;
    }

    if (id != null && _pending.containsKey(id)) {
      _pending.remove(id)?.complete(msg);
    }
  }
}
