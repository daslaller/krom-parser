# AGENTS.md

## Cursor Cloud specific instructions

krom-parser is a language-agnostic tree-sitter structural-parsing daemon that
speaks JSON over stdin/stdout using LSP-style `Content-Length` framing (methods:
`ping`, `parseFile`, `updateFile`, `getStructure`, `runQuery`, `shutdown`, ...).
It is the backend the Krom editor spawns alongside LSP servers. `api.md` is the
protocol/plugin source of truth.

The repo now holds **two implementations** plus shared plugins:

- `go/` — go-tree-sitter implementation, the README's **primary distribution**.
- `kotlin/` — ktreesitter (JVM) implementation.
- `plugins/` — language plugins (`python`, `_template`). Each has `.scm` query
  files + `manifest.json`; native grammars live at `lib/${platform}/parser.so`.

### Toolchain (already installed in the VM snapshot)

- **Go**: system `go` 1.22 is on `PATH`; `go/go.mod` requires `go 1.23`, so builds
  auto-download the `go1.23.6` toolchain (cached in the snapshot). cgo is required
  (`engine.go` uses `dlopen`), so a C compiler (`gcc`/`clang`) must be present — it
  is. Build with `CGO_ENABLED=1` (the default).
- **JVM**: JDK `23` (Temurin) at `/opt/jdk23` and Gradle `8.12` at `/opt/gradle-8.12`,
  both on `PATH` with `JAVA_HOME=/opt/jdk23` via `~/.bashrc`. The repo ships only
  `kotlin/gradle/wrapper/gradle-wrapper.properties` (no wrapper jar/scripts), so use
  the system `gradle`. Gradle finds the JDK 23 toolchain required by `jvmToolchain(23)`
  via `org.gradle.java.installations.paths=/opt/jdk23` in `~/.gradle/gradle.properties`.

### Commands

Go (from `go/`):

- Build: `go build -o krom-parser ./cmd/krom-parser`.
- Vet/Test: `go vet ./...`, `go test ./...`.
- Run (dev): `./krom-parser --plugin-dir ../plugins`.

Kotlin (from `kotlin/`):

- Build: `gradle build`. Fat JAR: `gradle fatJar` → `build/libs/krom-parser-0.1.0-all.jar`.
- Run the fat JAR directly (see caveat): `java -jar build/libs/krom-parser-0.1.0-all.jar`.

### Non-obvious caveats

- **Protocol smoke test** (works for the Go daemon today): pipe a framed request and
  expect a `ready` banner then `pong`:
  `printf 'Content-Length: 24\r\n\r\n{"id":1,"method":"ping"}' | ./krom-parser --plugin-dir ../plugins`
  → stdout `{"id":0,"result":"krom-parser ready"}` then `{"id":1,"result":"pong"}`
  (startup logs go to stderr).
- **No native grammar is committed.** `plugins/python` has query files but no
  `lib/linux-x64/parser.so`, so the daemon logs "native library not found" and loads
  0 grammars; `parseFile` returns an unknown-language error until a grammar `.so` is
  built and dropped in. The daemon still starts and answers `ping`/lifecycle requests.
- `gradle run` does not reliably forward piped stdin, so run the fat JAR directly to
  exercise the stdio protocol.
- Pre-existing failures unrelated to the environment (do NOT "fix" as setup work):
  - **Kotlin does not compile as committed.** `kotlin/.../state/FileStateManager.kt`
    `runQuery` returns `q.matches(...).flatMap { ... }` (a `Sequence`) but is declared
    `List<NodeInfo>` — it needs a `.toList()`. `gradle build`/`fatJar` fail on this
    type error even though the JDK 23 + Gradle 8.12 toolchain is correct (all deps
    resolve and everything else compiles first).
  - The Go `TestPythonPluginParse` integration test fails here: it hardcodes
    `/workspace/krom-parser/plugins` (this checkout is at `/agent/repos/krom-parser`)
    and also requires the missing native python grammar. `go test ./internal/daemon`
    passes.
