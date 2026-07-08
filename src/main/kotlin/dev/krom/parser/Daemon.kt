package dev.krom.parser

import dev.krom.parser.ipc.*
import dev.krom.parser.state.FileStateManager
import dev.krom.parser.treesitter.ParserEngine
import kotlinx.serialization.json.*

/**
 * The krom-parser daemon.
 *
 * Reads JSON-RPC style messages from stdin, routes them to the appropriate
 * handler, and writes responses to stdout. All tree-sitter operations
 * happen here — the editor never touches native code.
 */
class Daemon(
    private val transport: Transport,
    private val engine: ParserEngine,
) {
    private val stateManager = FileStateManager(engine)
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Main loop — reads messages until stdin closes.
     */
    fun run() {
        // Announce ready
        transport.write(json.encodeToString(
            Response.serializer(),
            Response(id = 0, result = JsonPrimitive("krom-parser ready"))
        ))

        while (true) {
            val raw = transport.read() ?: break
            val response = handleMessage(raw)
            if (response != null) {
                transport.write(json.encodeToString(Response.serializer(), response))
            }
        }
    }

    private fun handleMessage(raw: String): Response? {
        val request = try {
            json.decodeFromString(Request.serializer(), raw)
        } catch (e: Exception) {
            return Response(id = -1, error = ErrorInfo(-32700, "Parse error: ${e.message}"))
        }

        return try {
            val result = route(request.method, request.params)
            Response(id = request.id, result = result)
        } catch (e: Exception) {
            Response(id = request.id, error = ErrorInfo(-1, e.message ?: "Unknown error"))
        }
    }

    private fun route(method: String, params: JsonObject): JsonElement = when (method) {
        "parseFile" -> handleParseFile(params)
        "updateFile" -> handleUpdateFile(params)
        "closeFile" -> handleCloseFile(params)
        "getNodeAtPosition" -> handleGetNodeAtPosition(params)
        "getStructure" -> handleGetStructure(params)
        "runQuery" -> handleRunQuery(params)
        "ping" -> JsonPrimitive("pong")
        "shutdown" -> {
            // Clean exit
            JsonPrimitive("ok").also {
                Thread { Thread.sleep(50); System.exit(0) }.start()
            }
        }
        else -> throw IllegalArgumentException("Unknown method: $method")
    }

    // ── Handlers ────────────────────────────────────────────────────────────

    private fun handleParseFile(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val content = params.string("content")
        val languageId = params.string("languageId")

        val state = stateManager.parseFile(fileId, content, languageId)
            ?: return JsonObject(mapOf("success" to JsonPrimitive(false), "error" to JsonPrimitive("Unknown language: $languageId")))

        return JsonObject(mapOf(
            "success" to JsonPrimitive(true),
            "version" to JsonPrimitive(state.version),
        ))
    }

    private fun handleUpdateFile(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val content = params.string("content")

        val state = stateManager.updateFile(fileId, content)
            ?: return JsonObject(mapOf("success" to JsonPrimitive(false), "error" to JsonPrimitive("File not open: $fileId")))

        return JsonObject(mapOf(
            "success" to JsonPrimitive(true),
            "version" to JsonPrimitive(state.version),
        ))
    }

    private fun handleCloseFile(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        stateManager.closeFile(fileId)
        return JsonPrimitive("ok")
    }

    private fun handleGetNodeAtPosition(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val line = params.int("line")
        val column = params.int("column")

        val node = stateManager.getNodeAtPosition(fileId, line, column)
            ?: return JsonNull

        return json.encodeToJsonElement(NodeInfo.serializer(), node)
    }

    private fun handleGetStructure(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val symbols = stateManager.getStructure(fileId)
        return json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(SymbolInfo.serializer()),
            symbols,
        )
    }

    private fun handleRunQuery(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val query = params.string("query")
        val nodes = stateManager.runQuery(fileId, query)
        return json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(NodeInfo.serializer()),
            nodes,
        )
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.content ?: throw IllegalArgumentException("Missing param: $key")

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.int ?: throw IllegalArgumentException("Missing param: $key")
}
