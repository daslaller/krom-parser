# AGENTS.md

## Cursor Cloud specific instructions

krom-parser is a **language-agnostic** tree-sitter structural-parsing daemon for
the Krom editor. It speaks JSON over stdin/stdout using LSP-style `Content-Length`
framing (methods: `ping`, `shutdown`, `listLanguages`, `parseFile`, `updateFile`,
`getStructure`, `getHighlights`, `runQuery`, ...). Grammar support is loaded from a
plugin directory at startup; the daemon embeds no grammar. `api.md` is the protocol
source of truth.

The monorepo holds two implementations plus the plugins:

- `go/`      — native (go-tree-sitter), **primary distribution target**.
- `kotlin/`  — JVM (ktreesitter), reference implementation.
- `plugins/` — language plugins (currently `python`, `_template`).

### Toolchain (already installed in the VM snapshot)

- Go `1.23.6` at `/usr/local/go-1.23/bin` (on `PATH` **before** the system `go`,
  which is 1.22 and too old for `go/go.mod`'s `go 1.23` directive).
  `GOTOOLCHAIN=local` is exported so builds do not try to fetch a toolchain.
- JDK `23` (Temurin) at `/opt/jdk23` (`JAVA_HOME`) and Gradle `8.12` at
  `/opt/gradle-8.12`, both on `PATH` via `~/.bashrc`. The repo ships only
  `kotlin/gradle/wrapper/gradle-wrapper.properties` (no wrapper jar/scripts), so
  use the system `gradle`. `~/.gradle/gradle.properties` points the `jvmToolchain(23)`
  requirement at `/opt/jdk23` via `org.gradle.java.installations.paths`.

### Commands (run from the implementation subdir)

- Go build: `cd go && go build -o krom-parser ./cmd/krom-parser`.
- Go test: `cd go && go test ./...`.
- Kotlin build / fat JAR: `cd kotlin && gradle build` / `gradle fatJar`
  (→ `kotlin/build/libs/krom-parser-0.1.0-all.jar`).
- Kotlin test: `cd kotlin && gradle test` (no test sources yet → no-op).
- Run (dev): pass a plugin dir, e.g.
  `./go/krom-parser --plugin-dir plugins` or
  `java -jar kotlin/build/libs/krom-parser-0.1.0-all.jar --plugin-dir plugins`.

### Non-obvious caveats

- Protocol smoke test: write `Content-Length`-framed JSON on stdin and read framed
  JSON on stdout (the byte length in the header must match the JSON exactly). On
  startup the daemon emits `{"id":0,"result":"krom-parser ready"}`; then
  `{"id":1,"method":"ping"}` → `pong`, `{"id":2,"method":"listLanguages"}` lists the
  python plugin, `{"id":3,"method":"shutdown"}` → `"ok"` and exit 0. `gradle run`
  does **not** reliably forward piped stdin — run the built Go binary or the fat JAR
  directly.
- Plugins load their metadata + `.scm` queries, but the shipped `python` plugin has
  **no** native grammar library (`lib/linux-x64/parser.so`), so `listLanguages`
  reports it with `loaded:false` and `parseFile`/highlight/structure cannot actually
  parse until a grammar `.so` is built (tree-sitter CLI). `ping`/lifecycle still work.
- Pre-existing issues at HEAD, unrelated to the environment (do **not** "fix" these
  as setup work):
  - Kotlin `kotlin/src/main/kotlin/dev/krom/parser/state/FileStateManager.kt`
    `runQuery` returns a `Sequence<NodeInfo>` where `List<NodeInfo>` is declared
    (needs a trailing `.toList()`), which fails `gradle build` / `gradle fatJar`.
    The Go build is clean.
  - Go `internal/treesitter/parse_integration_test.go` hardcodes
    `/workspace/krom-parser/plugins` (absent in this VM) and also needs a compiled
    python grammar, so that single test fails; the rest of `go test ./...` passes.
