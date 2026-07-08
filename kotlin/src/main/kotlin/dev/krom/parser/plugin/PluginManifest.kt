package dev.krom.parser.plugin

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Language plugin manifest — see /api.md at repo root.
 */
@Serializable
data class PluginManifest(
    val id: String,
    val name: String,
    val version: String,
    val parser: ParserConfig,
    val queries: QueryConfig,
    val symbols: String? = null,
)

@Serializable
data class ParserConfig(
    val entry: String,
    val library: String,
)

@Serializable
data class QueryConfig(
    val highlights: String,
    val structure: String? = null,
)

/**
 * A loaded language plugin with resolved file paths and query sources.
 */
data class LanguagePlugin(
    val manifest: PluginManifest,
    val rootDir: java.io.File,
    val highlightsScm: String,
    val structureScm: String?,
    val symbolKinds: Map<String, String>,
    val nativeLibraryPath: java.io.File?,
)

object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): PluginManifest = json.decodeFromString(PluginManifest.serializer(), text)
}
