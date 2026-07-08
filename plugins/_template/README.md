# Language plugin template

Copy this directory to create a new language plugin:

```bash
cp -r plugins/_template plugins/mylang
# Edit manifest.json, add highlights.scm, build native grammar to lib/
```

See [api.md](../api.md) for the full plugin specification.

## Build native grammar

```bash
# From your tree-sitter grammar repo:
tree-sitter build --output lib/linux-x64/parser.so
```

Place the compiled library at the path declared in `manifest.json` → `parser.library`.
