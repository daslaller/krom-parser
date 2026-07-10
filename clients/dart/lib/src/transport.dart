import 'dart:async';
import 'dart:convert';
import 'dart:io';

/// Content-Length framed JSON transport over stdin/stdout (same wire as LSP).
class ParserTransport {
  ParserTransport._(this._process) {
    _startReading();
  }

  final Process _process;
  final StreamController<Map<String, dynamic>> _incoming =
      StreamController.broadcast();
  final _buffer = <int>[];

  Stream<Map<String, dynamic>> get messages => _incoming.stream;

  static Future<ParserTransport> start(List<String> command) async {
    final process = await Process.start(
      command.first,
      command.skip(1).toList(),
      runInShell: false,
    );
    return ParserTransport._(process);
  }

  void send(Map<String, dynamic> message) {
    final body = utf8.encode(jsonEncode(message));
    final header = utf8.encode('Content-Length: ${body.length}\r\n\r\n');
    _process.stdin.add(header);
    _process.stdin.add(body);
  }

  Future<void> dispose() async {
    await _process.stdin.close();
    _process.kill();
    if (!_incoming.isClosed) await _incoming.close();
  }

  void _startReading() {
    _process.stdout.listen(
      _onData,
      onError: (Object e) {
        if (!_incoming.isClosed) _incoming.addError(e);
      },
      onDone: () {
        if (!_incoming.isClosed) _incoming.close();
      },
    );
    _process.stderr.drain<void>();
  }

  void _onData(List<int> data) {
    _buffer.addAll(data);
    _pump();
  }

  void _pump() {
    while (true) {
      final sep = _findSep();
      if (sep == -1) return;

      final headerStr = utf8.decode(_buffer.sublist(0, sep));
      final length = _parseLength(headerStr);
      if (length == null) {
        _buffer.removeRange(0, sep + 4);
        continue;
      }

      final bodyStart = sep + 4;
      if (_buffer.length < bodyStart + length) return;

      final bodyBytes = _buffer.sublist(bodyStart, bodyStart + length);
      _buffer.removeRange(0, bodyStart + length);

      try {
        final msg = jsonDecode(utf8.decode(bodyBytes)) as Map<String, dynamic>;
        if (!_incoming.isClosed) _incoming.add(msg);
      } catch (_) {
        // Malformed JSON — ignore.
      }
    }
  }

  int _findSep() {
    for (var i = 0; i < _buffer.length - 3; i++) {
      if (_buffer[i] == 13 &&
          _buffer[i + 1] == 10 &&
          _buffer[i + 2] == 13 &&
          _buffer[i + 3] == 10) {
        return i;
      }
    }
    return -1;
  }

  int? _parseLength(String header) {
    for (final line in header.split('\r\n')) {
      if (line.toLowerCase().startsWith('content-length:')) {
        return int.tryParse(line.split(':').last.trim());
      }
    }
    return null;
  }
}
