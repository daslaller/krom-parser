import 'package:parser_client/parser_client.dart';
import 'package:test/test.dart';

void main() {
  group('ParserHighlightSpan', () {
    test('parses from JSON', () {
      final span = ParserHighlightSpan.fromJson({
        'startByte': 0,
        'endByte': 5,
        'capture': 'keyword',
      });
      expect(span.startByte, 0);
      expect(span.endByte, 5);
      expect(span.capture, 'keyword');
    });
  });

  group('ParserHighlightResult', () {
    test('parses spans list', () {
      final result = ParserHighlightResult.fromJson({
        'spans': [
          {'startByte': 0, 'endByte': 2, 'capture': 'keyword'},
        ],
      });
      expect(result.spans.length, 1);
      expect(result.spans.first.capture, 'keyword');
    });
  });

  group('ParserClient integration', () {
    test('start fails when parser binary is unavailable', () async {
      expect(
        () => ParserClient.start(
          command: ['nonexistent-krom-parser-xyz'],
        ),
        throwsA(anything),
      );
    });
  });
}
