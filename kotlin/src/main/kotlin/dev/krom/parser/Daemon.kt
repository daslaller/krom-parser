package dev.krom.parser

import dev.krom.parser.ipc.*
import dev.krom.parser.state.FileStateManager
import dev.krom.parser.treesitter.ParserEngine
import kotlinx.serialization.json.*

/**
 * Language-agnostic krom-parser daemon.
 * See /api.md for protocol specification.
 */
class Daemon(
    private val transport: Transport,
    private val engine: ParserEngine,
) {
    private val stateManager = FileStateManager(engine, engine.registry())
    private val json = Json { ignoreUnknownKeys = true }

    fun run() {
        transport.write(
            json.encodeToString(
                Response.serializer(),
                Response(id = 0, result = JsonPrimitive("krom-parser ready")),
            ),
        )

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
        "ping" -> JsonPrimitive("pong")
        "shutdown" -> JsonPrimitive("ok").also {
            Thread { Thread.sleep(50); System.exit(0) }.start()
        }
        "listLanguages" -> json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(LanguageInfo.serializer()),
            engine.registry().listLanguages().map { m ->
                LanguageInfo(
                    id = m["id"] as String,
                    name = m["name"] as String,
                    version = m["version"] as String,
                    queries = m["queries"] as List<String>,
                    loaded = m["loaded"] as Boolean,
                )
            },
        )
        "parseFile" -> handleParseFile(params)
        "updateFile" -> handleUpdateFile(params)
        "closeFile" -> handleCloseFile(params)
        "getNodeAtPosition" -> handleGetNodeAtPosition(params)
        "getStructure" -> handleGetStructure(params)
        "getHighlights" -> handleGetHighlights(params)
        "runQuery" -> handleRunQuery(params)
        else -> throw IllegalArgumentException("Unknown method: $method")
    }

    private fun handleParseFile(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val content = params.string("content")
        val languageId = params.string("languageId")

        val state = stateManager.parseFile(fileId, content, languageId)
            ?: return JsonObject(
                mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("Unknown language: $languageId"),
                ),
            )

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "version" to JsonPrimitive(state.version),
            ),
        )
    }

    private fun handleUpdateFile(params: JsonObject): JsonElement {
        val fileId = params.string("fileId")
        val content = params.string("content")

        val state = stateManager.updateFile(fileId, content)
            ?: return JsonObject(
                mapOf(
                    "success" to JsonPrimitive(false),
                    "error" to JsonPrimitive("File not open: $fileId"),
                ),
            )

        return JsonObject(
            mapOf(
                "success" to JsonPrimitive(true),
                "version" to JsonPrimitive(state.version),
            ),
        )
    }

    private fun handleCloseFile(params: JsonObject): JsonElement {
        stateManager.closeFile(params.string("fileId"))
        return JsonPrimitive("ok")
    }

    private fun handleGetNodeAtPosition(params: JsonObject): JsonElement {
        val node = stateManager.getNodeAtPosition(
            params.string("fileId"),
            params.int("line"),
            params.int("column"),
        ) ?: return JsonNull
        return json.encodeToJsonElement(NodeInfo.serializer(), node)
    }

    private fun handleGetStructure(params: JsonObject): JsonElement {
        val symbols = stateManager.getStructure(params.string("fileId"))
        return json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(SymbolInfo.serializer()),
            symbols,
        )
    }

    private fun handleGetHighlights(params: JsonObject): JsonElement {
        val result = stateManager.getHighlights(params.string("fileId"))
        return json.encodeToJsonElement(HighlightResult.serializer(), result)
    }

    private fun handleRunQuery(params: JsonObject): JsonElement {
        val nodes = stateManager.runQuery(params.string("fileId"), params.string("query"))
        return json.encodeToJsonElement(
            kotlinx.serialization.builtins.ListSerializer(NodeInfo.serializer()),
            nodes,
        )
    }

    private fun JsonObject.string(key: String): String =
        (this[key] as? JsonPrimitive)?.content
            ?: throw IllegalArgumentException("Missing param: $key")

    private fun JsonObject.int(key: String): Int =
        (this[key] as? JsonPrimitive)?.int
            ?: throw IllegalArgumentException("Missing param: $key")
}
