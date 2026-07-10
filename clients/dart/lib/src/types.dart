class ParserLanguageInfo {
  const ParserLanguageInfo({
    required this.id,
    required this.name,
    required this.version,
    required this.queries,
    this.loaded = false,
  });

  final String id;
  final String name;
  final String version;
  final List<String> queries;
  final bool loaded;

  factory ParserLanguageInfo.fromJson(Map<String, dynamic> json) {
    return ParserLanguageInfo(
      id: json['id'] as String,
      name: json['name'] as String,
      version: json['version'] as String,
      queries: (json['queries'] as List?)?.cast<String>() ?? const [],
      loaded: json['loaded'] as bool? ?? false,
    );
  }
}

class ParserParseResult {
  const ParserParseResult({required this.success, this.error, this.version});

  final bool success;
  final String? error;
  final int? version;

  factory ParserParseResult.fromJson(Map<String, dynamic> json) {
    return ParserParseResult(
      success: json['success'] as bool? ?? false,
      error: json['error'] as String?,
      version: json['version'] as int?,
    );
  }
}

class ParserHighlightSpan {
  const ParserHighlightSpan({
    required this.startByte,
    required this.endByte,
    required this.capture,
  });

  final int startByte;
  final int endByte;
  final String capture;

  factory ParserHighlightSpan.fromJson(Map<String, dynamic> json) {
    return ParserHighlightSpan(
      startByte: json['startByte'] as int,
      endByte: json['endByte'] as int,
      capture: json['capture'] as String,
    );
  }
}

class ParserHighlightResult {
  const ParserHighlightResult({required this.spans});

  final List<ParserHighlightSpan> spans;

  factory ParserHighlightResult.fromJson(Map<String, dynamic> json) {
    final raw = json['spans'] as List? ?? const [];
    return ParserHighlightResult(
      spans: raw
          .map((s) => ParserHighlightSpan.fromJson(s as Map<String, dynamic>))
          .toList(),
    );
  }
}

class ParserNodeInfo {
  const ParserNodeInfo({
    required this.type,
    required this.startLine,
    required this.startColumn,
    required this.endLine,
    required this.endColumn,
    this.children = const [],
  });

  final String type;
  final int startLine;
  final int startColumn;
  final int endLine;
  final int endColumn;
  final List<ParserNodeInfo> children;

  factory ParserNodeInfo.fromJson(Map<String, dynamic> json) {
    return ParserNodeInfo(
      type: json['type'] as String,
      startLine: json['startLine'] as int,
      startColumn: json['startColumn'] as int,
      endLine: json['endLine'] as int,
      endColumn: json['endColumn'] as int,
      children: (json['children'] as List?)
              ?.map((c) => ParserNodeInfo.fromJson(c as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}

class ParserSymbolInfo {
  const ParserSymbolInfo({
    required this.name,
    required this.kind,
    required this.startLine,
    required this.endLine,
    this.children = const [],
  });

  final String name;
  final String kind;
  final int startLine;
  final int endLine;
  final List<ParserSymbolInfo> children;

  factory ParserSymbolInfo.fromJson(Map<String, dynamic> json) {
    return ParserSymbolInfo(
      name: json['name'] as String,
      kind: json['kind'] as String,
      startLine: json['startLine'] as int,
      endLine: json['endLine'] as int,
      children: (json['children'] as List?)
              ?.map((c) => ParserSymbolInfo.fromJson(c as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}
