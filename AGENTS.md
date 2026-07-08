# AGENTS.md

## Cursor Cloud specific instructions

krom-parser is a Kotlin/JVM daemon that speaks JSON over stdin/stdout using
LSP-style `Content-Length` framing (methods: `ping`, `parseFile`, `updateFile`,
`getStructure`, `runQuery`, `shutdown`, ...). It is the tree-sitter backend the
Krom editor will spawn. Standard Gradle commands apply; the notes below are the
non-obvious parts.

### Toolchain (already installed in the VM snapshot)

- JDK `23` (Temurin) at `/opt/jdk23` and Gradle `8.12` at `/opt/gradle-8.12`,
  both on `PATH` with `JAVA_HOME=/opt/jdk23` via `~/.bashrc`.
- The repo ships only `gradle/wrapper/gradle-wrapper.properties` — the wrapper
  scripts and `gradle-wrapper.jar` are **not** in the repo, so use the system
  `gradle` command (pinned to the same 8.12). Gradle locates the JDK 23 toolchain
  required by `jvmToolchain(23)` via `org.gradle.java.installations.paths=/opt/jdk23`
  in `~/.gradle/gradle.properties`.

### Commands

- Build: `gradle build`.
- Test: `gradle test` (no test sources exist yet, so this is a no-op).
- Fat JAR: `gradle fatJar` → `build/libs/krom-parser-0.1.0-all.jar`.
- Run (dev): `gradle run`.

### Non-obvious caveats

- `gradle run` does **not** reliably forward piped stdin to the app, so it only
  prints the `ready` message before hitting EOF. To exercise the stdio IPC
  protocol, run the fat JAR directly:
  `java -jar build/libs/krom-parser-0.1.0-all.jar`.
- Protocol smoke test: send a framed request
  `Content-Length: 24\r\n\r\n{"id":1,"method":"ping"}` on stdin and expect
  `{"id":1,"result":"pong"}` (preceded by a `{"id":0,"result":"krom-parser ready"}`
  banner) on stdout.
- Grammar loading is still a TODO in `Main.kt`, so `parseFile` returns
  `Unknown language` until grammars are registered. The daemon still starts and
  answers `ping`/lifecycle requests without any grammars.
