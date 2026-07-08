package dev.krom.parser

import dev.krom.parser.ipc.Transport
import dev.krom.parser.treesitter.ParserEngine

/**
 * Entry point for the krom-parser daemon.
 *
 * Usage: java -jar krom-parser.jar [--grammar-dir <path>]
 *
 * The daemon communicates over stdin/stdout using Content-Length framed JSON messages.
 * Language grammars are loaded from the grammar directory at startup.
 *
 * Krom (the editor) spawns this as a subprocess and talks to it the same way
 * it talks to LSP servers — just a different protocol on the same wire format.
 */
fun main(args: Array<String>) {
    val grammarDir = parseGrammarDir(args)

    // Redirect stderr for logging (stdout is reserved for IPC)
    System.err.println("krom-parser starting...")

    val engine = ParserEngine()

    // TODO: Load grammars from grammarDir
    // For each grammar .dll/.so in the directory:
    //   val language = Language(<native pointer>)
    //   val highlightScm = File(grammarDir, "$name/highlights.scm").readText()
    //   engine.registerLanguage(name, language, highlightScm)
    //
    // The ktreesitter-plugin generates language classes like TreeSitterDart,
    // TreeSitterPython etc. Those get registered here once compiled.

    System.err.println("krom-parser ready, listening on stdio")

    val transport = Transport(System.`in`, System.out)
    val daemon = Daemon(transport, engine)
    daemon.run()
}

private fun parseGrammarDir(args: Array<String>): String {
    val idx = args.indexOf("--grammar-dir")
    return if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else "grammars"
}
