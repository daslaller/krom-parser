# parser_client

Official Dart client for the [krom-parser](../api.md) daemon.

Spawns `krom-parser` as a subprocess and speaks the Content-Length JSON protocol
defined in `api.md` (not LSP — similar framing, different methods).

## Usage

```yaml
dependencies:
  parser_client:
    git:
      url: https://github.com/daslaller/krom-parser.git
      path: clients/dart
```

```dart
import 'package:parser_client/parser_client.dart';

final client = await ParserClient.start(
  command: ['/path/to/krom-parser', '--plugin-dir', '/path/to/plugins'],
);

await client.openDocument(
  path: '/project/lib/main.dart',
  languageId: 'dart',
  content: source,
);
```

## Develop / test

```bash
cd clients/dart
dart pub get
dart analyze
dart test
```

## Versioning

Keep this package in sync with `api.md` in the parent repo. Breaking protocol
changes should land in the same PR as client updates.
