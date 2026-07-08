package dev.krom.parser.ipc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/**
 * JSON message protocol for the krom-parser daemon.
 * See /api.md at repo root for the full specification.
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

// ── Structural data returned to the editor ──────────────────────────────────

@Serializable
data class SymbolInfo(
    val name: String,
    val kind: String,
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

@Serializable
data class HighlightSpan(
    val startByte: Int,
    val endByte: Int,
    val capture: String,
)

@Serializable
data class HighlightResult(
    val spans: List<HighlightSpan>,
)

@Serializable
data class LanguageInfo(
    val id: String,
    val name: String,
    val version: String,
    val queries: List<String>,
    val loaded: Boolean = false,
)
