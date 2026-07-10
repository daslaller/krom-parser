# krom-parser API

Tree-sitter structural parsing daemon for the [Krom](https://github.com/daslaller/krom) editor.

krom-parser is **language-agnostic**. It speaks a fixed JSON protocol over stdio. Language support is provided by **plugins** loaded at startup from a plugin directory. The daemon itself never embeds grammar knowledge.

Implementations:

| Path | Runtime | Status |
|------|---------|--------|
| `kotlin/` | JVM (ktreesitter) | Reference implementation |
| `go/` | Native binary (go-tree-sitter) | Primary distribution target |

Both implementations MUST conform to this document.

---

## Wire format

Identical to LSP: **Content-Length** framed JSON over stdin/stdout.

```
Content-Length: 42\r\n
\r\n
{"id":1,"method":"ping","params":{}}
```

- **stdout** — protocol messages only
- **stderr** — logging (never parsed by the editor)

### Envelope

**Request** (editor → daemon):

```json
{ "id": 1, "method": "parseFile", "params": { ... } }
```

**Response** (daemon → editor):

```json
{ "id": 1, "result": { ... } }
```

**Error**:

```json
{ "id": 1, "error": { "code": -1, "message": "Unknown language: rust" } }
```

**Ready notification** (sent once on startup, `id: 0`):

```json
{ "id": 0, "result": "krom-parser ready" }
```

Standard JSON-RPC error codes apply where noted (`-32700` parse error).

---

## Daemon lifecycle

```
1. Editor spawns: krom-parser --plugin-dir /path/to/plugins
2. Daemon loads all plugins from plugin-dir
3. Daemon writes ready notification to stdout
4. Editor sends requests; daemon responds
5. Editor sends shutdown OR closes stdin → daemon exits 0
```

### CLI flags

| Flag | Default | Description |
|------|---------|-------------|
| `--plugin-dir` | `plugins` | Directory containing language plugin folders |

---

## Protocol methods

### `ping`

Health check.

**Params:** `{}`  
**Result:** `"pong"`

---

### `shutdown`

Clean exit. Daemon MUST flush and exit within 100 ms.

**Params:** `{}`  
**Result:** `"ok"`

---

### `listLanguages`

Returns all loaded plugins.

**Params:** `{}`  
**Result:**

```json
[
  {
    "id": "python",
    "name": "Python",
    "version": "0.23.0",
    "queries": ["highlights", "structure"]
  }
]
```

---

### `parseFile`

Open and parse a document.

**Params:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `fileId` | string | yes | Opaque document id (usually file URI or path) |
| `languageId` | string | yes | Plugin id (e.g. `"python"`) |
| `content` | string | yes | Full document text |

**Result:**

```json
{ "success": true, "version": 1 }
```

**Error result** (unknown language):

```json
{ "success": false, "error": "Unknown language: rust" }
```

---

### `updateFile`

Incrementally re-parse an open document.

**Params:**

| Field | Type | Required |
|-------|------|----------|
| `fileId` | string | yes |
| `content` | string | yes |

**Result:** Same shape as `parseFile`.

---

### `closeFile`

Drop document state.

**Params:** `{ "fileId": "..." }`  
**Result:** `"ok"`

---

### `getNodeAtPosition`

Return the smallest named AST node at a position.

**Params:**

| Field | Type | Required |
|-------|------|----------|
| `fileId` | string | yes |
| `line` | int | yes | 0-based line |
| `column` | int | yes | 0-based UTF-16 column |

**Result:** `NodeInfo` or `null`

```json
{
  "type": "identifier",
  "startLine": 3,
  "startColumn": 4,
  "endLine": 3,
  "endColumn": 10,
  "children": []
}
```

---

### `getStructure`

Document outline (classes, functions, etc.).

Uses the plugin's `structure.scm` query when present; otherwise falls back to walking the AST with `symbols.json` mappings.

**Params:** `{ "fileId": "..." }`  
**Result:** `SymbolInfo[]`

```json
[
  {
    "name": "MyClass",
    "kind": "class",
    "startLine": 0,
    "endLine": 12,
    "children": [
      {
        "name": "greet",
        "kind": "method",
        "startLine": 2,
        "endLine": 4,
        "children": []
      }
    ]
  }
]
```

---

### `getHighlights`

Syntax highlight spans for the document.

Uses the plugin's `highlights.scm` query.

**Params:** `{ "fileId": "..." }`  
**Result:**

```json
{
  "spans": [
    { "startByte": 0, "endByte": 5, "capture": "keyword" },
    { "startByte": 6, "endByte": 8, "capture": "identifier" }
  ]
}
```

Capture names map to editor theme keys (see [Capture naming](#capture-naming)).

---

### `runQuery`

Run an arbitrary tree-sitter query against an open document.

**Params:**

| Field | Type | Required |
|-------|------|----------|
| `fileId` | string | yes |
| `query` | string | yes | tree-sitter query source |

**Result:** `NodeInfo[]`

---

## Shared types

### `NodeInfo`

| Field | Type |
|-------|------|
| `type` | string — tree-sitter node type |
| `startLine` | int |
| `startColumn` | int |
| `endLine` | int |
| `endColumn` | int |
| `children` | `NodeInfo[]` |

### `SymbolInfo`

| Field | Type |
|-------|------|
| `name` | string |
| `kind` | string — see [Symbol kinds](#symbol-kinds) |
| `startLine` | int |
| `endLine` | int |
| `children` | `SymbolInfo[]` |

### Symbol kinds

`class`, `interface`, `enum`, `struct`, `function`, `method`, `field`, `variable`, `module`, `namespace`, `type`

### Capture naming

Highlight captures SHOULD use tree-sitter convention names so themes are portable:

`keyword`, `string`, `comment`, `number`, `type`, `function`, `property`, `operator`, `punctuation`, `variable`, `constant`

Compound captures use dots: `function.builtin`, `punctuation.special`.

---

## Language plugins

A plugin is a **directory** inside `--plugin-dir`. The daemon loads every subdirectory that contains a valid `manifest.json`.

### Plugin layout

```
plugins/
  python/
    manifest.json       # required
    highlights.scm      # required
    structure.scm       # optional — outline query
    symbols.json        # optional — AST node type → symbol kind fallback
    lib/
      linux-x64/
        parser.so       # platform-specific native grammar
      win-x64/
        parser.dll
      darwin-arm64/
        parser.dylib
```

### `manifest.json`

```json
{
  "id": "python",
  "name": "Python",
  "version": "0.23.0",
  "parser": {
    "entry": "tree_sitter_python",
    "library": "lib/${platform}/parser.so"
  },
  "queries": {
    "highlights": "highlights.scm",
    "structure": "structure.scm"
  },
  "symbols": "symbols.json"
}
```

| Field | Required | Description |
|-------|----------|-------------|
| `id` | yes | Language id used in `parseFile.languageId`. Lowercase, no spaces. |
| `name` | yes | Human-readable name |
| `version` | yes | Grammar version (semver) |
| `parser.entry` | yes | Exported C symbol name (e.g. `tree_sitter_python`) |
| `parser.library` | yes | Path relative to plugin root; `${platform}` is resolved at load time |
| `queries.highlights` | yes | Path to highlight query |
| `queries.structure` | no | Path to structure/outline query |
| `symbols` | no | Path to symbol kind mapping file |

### Platform resolution

`${platform}` expands to:

| OS / arch | Value |
|-----------|-------|
| Linux x86_64 | `linux-x64` |
| Linux aarch64 | `linux-arm64` |
| Windows x86_64 | `win-x64` |
| macOS x86_64 | `darwin-x64` |
| macOS arm64 | `darwin-arm64` |

### Native grammar library

Each plugin MUST ship a tree-sitter grammar compiled to a shared library. The library MUST export a function named in `parser.entry` returning `const TSLanguage *`.

Build grammars with the tree-sitter CLI:

```bash
tree-sitter build --output lib/linux-x64/parser.so
```

The daemon loads the library dynamically at startup. **The daemon binary does not embed any grammar.**

### `highlights.scm`

Standard tree-sitter highlight query. Example:

```scm
(function_definition
  name: (identifier) @function)

(keyword) @keyword
(string) @string
(comment) @comment
```

### `structure.scm`

Query for document outline. Captures:

| Capture | Meaning |
|---------|---------|
| `@symbol` | The definition node (used for range + kind via `symbols.json`) |
| `@name` | The symbol name text |

Example:

```scm
(class_definition
  name: (identifier) @name) @symbol

(function_definition
  name: (identifier) @name) @symbol
```

### `symbols.json`

Maps tree-sitter node types to editor symbol kinds. Used by `getStructure` fallback and to classify `@symbol` captures.

```json
{
  "class_definition": "class",
  "function_definition": "function",
  "method_definition": "method",
  "enum_definition": "enum",
  "interface_declaration": "interface",
  "struct_item": "struct",
  "field_definition": "field",
  "lexical_declaration": "variable"
}
```

---

## Implementing a new language plugin

1. **Obtain or build a tree-sitter grammar** for the language (e.g. `tree-sitter-dart`).
2. **Compile** the grammar to a shared library for each target platform.
3. **Write `highlights.scm`** — copy from the grammar repo's `queries/highlights.scm` or author one.
4. **Write `structure.scm`** (optional) — query for definitions you want in the outline.
5. **Write `symbols.json`** (optional) — map node types to symbol kinds.
6. **Write `manifest.json`** — wire paths together.
7. **Place** the folder in the plugin directory.
8. **Verify** with `listLanguages` and `parseFile`.

### Checklist

- [ ] `manifest.json` validates
- [ ] Native library loads (`listLanguages` includes your id)
- [ ] `parseFile` succeeds on a sample file
- [ ] `getHighlights` returns non-empty spans
- [ ] `getStructure` returns expected symbols
- [ ] `getNodeAtPosition` returns correct node at cursor

---

## Editor integration (Krom)

Krom spawns krom-parser as a subprocess (same as LSP servers):

```dart
final process = await Process.start('krom-parser', ['--plugin-dir', pluginPath]);
// Use Content-Length JSON framing — identical to lsp_client transport
```

Recommended client package: [`clients/dart/`](../clients/dart/) (`parser_client` on pub) — same Content-Length transport as `dart_lsp_client`, implements the krom-parser methods above.

**Do not** route LSP semantic features through krom-parser. Use LSP for diagnostics, completion, rename, format. Use krom-parser for syntax structure only.

---

## Versioning

Protocol version: **1.0**

Breaking changes increment the major version and MUST be documented here. Both `kotlin/` and `go/` implementations MUST stay in sync with this file.
