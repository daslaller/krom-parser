package dev.krom.parser.ipc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * JSON message protocol for the krom-parser daemon.
 *
 * Request:  { "id": 1, "method": "parseFile", "params": { ... } }
 * Response: { "id": 1, "result": { ... } }
 * Error:    { "id": 1, "error": { "code": -1, "message": "..." } }
 * Event:    { "method": "diagnostics", "params": { ... } }   (no id = push notification)
 */

@Serializable
data class Request(
    val id: Int,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class Response(
    val id: Int,
    val result: JsonElement? = null,
    val error: ErrorInfo? = null,
)

@Serializable
data class ErrorInfo(
    val code: Int,
    val message: String,
)

@Serializable
data class Notification(
    val method: String,
    val params: JsonObject = JsonObject(emptyMap()),
)

// ── Structural data returned to the editor ──────────────────────────────────

@Serializable
data class SymbolInfo(
    val name: String,
    val kind: String,          // "class", "function", "method", "enum", "field"
    val startLine: Int,
    val endLine: Int,
    val children: List<SymbolInfo> = emptyList(),
)

@Serializable
data class NodeInfo(
    val type: String,
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
    val children: List<NodeInfo> = emptyList(),
)
