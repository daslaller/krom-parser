# krom-parser

Tree-sitter structural parsing daemon for the [Krom](https://github.com/daslaller/krom) editor.

krom-parser is **language-agnostic**. It loads language support from **plugins** at startup and speaks a fixed JSON protocol over stdio (same wire format as LSP).

## Repository layout

```
krom-parser/
  api.md          # Protocol + plugin specification (source of truth)
  plugins/        # Language plugin directory (or point --plugin-dir elsewhere)
  clients/
    dart/         # Official Dart client (parser_client package)
  kotlin/         # JVM implementation (ktreesitter)
  go/             # Native implementation (go-tree-sitter) — primary distribution
```

## Quick start (Go)

```bash
cd go
go build -o krom-parser ./cmd/krom-parser
./krom-parser --plugin-dir ../plugins
```

Smoke test:

```bash
printf 'Content-Length: 27\r\n\r\n{"id":1,"method":"ping"}' | ./krom-parser
```

## Quick start (Kotlin)

```bash
cd kotlin
gradle fatJar
java -jar build/libs/krom-parser-0.1.0-all.jar --plugin-dir ../plugins
```

## Adding a language

See [api.md](api.md) for the plugin contract. Copy `plugins/_template/` and add your grammar's native library + query files.

## Editor integration

Krom spawns krom-parser as a subprocess alongside LSP servers:

| Daemon | Role |
|--------|------|
| LSP server | Diagnostics, completion, rename, format |
| krom-parser | Syntax tree, highlights, outline, queries |

Both use Content-Length JSON over stdio. The official Dart client lives at
[`clients/dart/`](clients/dart/) (`parser_client` package). LSP integration uses
the separate [`dart_lsp_client`](https://github.com/daslaller/dart_lsp_client)
package.

## Branches

| Branch | Purpose |
|--------|---------|
| `main` | Both implementations + api.md + plugins |
| `kotlin` | Kotlin implementation track |
| `go` | Go implementation track |
