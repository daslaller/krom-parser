package dev.krom.parser

import dev.krom.parser.ipc.Transport
import dev.krom.parser.treesitter.ParserEngine
import java.io.File

/**
 * Entry point for the Kotlin krom-parser daemon.
 *
 * Usage: java -jar krom-parser.jar [--plugin-dir <path>]
 *
 * See /api.md for protocol and plugin specification.
 */
fun main(args: Array<String>) {
    val pluginDir = parsePluginDir(args)

    System.err.println("krom-parser (kotlin) starting...")
    System.err.println("Plugin directory: ${pluginDir.absolutePath}")

    val engine = ParserEngine.fromPluginDir(pluginDir)

    val loaded = engine.registry().languageIds().count { engine.hasLanguage(it) }
    val total = engine.registry().languageIds().size
    System.err.println("Plugins: $total found, $loaded with native grammar loaded")
    System.err.println("krom-parser ready, listening on stdio")

    val transport = Transport(System.`in`, System.out)
    Daemon(transport, engine).run()
}

private fun parsePluginDir(args: Array<String>): File {
    val idx = args.indexOf("--plugin-dir")
    val path = if (idx >= 0 && idx + 1 < args.size) args[idx + 1] else "../plugins"
    return File(path).absoluteFile
}
