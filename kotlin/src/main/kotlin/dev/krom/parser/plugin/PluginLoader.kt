package dev.krom.parser.plugin

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Query
import java.io.File

/**
 * Resolves platform string for ${platform} in manifest paths.
 */
fun resolvePlatform(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        os.contains("linux") && (arch == "amd64" || arch == "x86_64") -> "linux-x64"
        os.contains("linux") && arch.contains("aarch64") -> "linux-arm64"
        os.contains("win") && (arch == "amd64" || arch == "x86_64") -> "win-x64"
        os.contains("mac") && arch.contains("aarch64") -> "darwin-arm64"
        os.contains("mac") -> "darwin-x64"
        else -> "linux-x64"
    }
}

/**
 * Loads language plugins from a directory. Each subdirectory with manifest.json
 * is a plugin. The daemon core is language-agnostic — all grammar knowledge
 * lives in plugins.
 */
class PluginLoader(private val pluginDir: File) {

    fun loadAll(): List<LanguagePlugin> {
        if (!pluginDir.isDirectory) {
            System.err.println("Plugin directory not found: ${pluginDir.absolutePath}")
            return emptyList()
        }

        return pluginDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith("_") }
            ?.mapNotNull { loadPlugin(it) }
            ?: emptyList()
    }

    fun loadPlugin(dir: File): LanguagePlugin? {
        val manifestFile = File(dir, "manifest.json")
        if (!manifestFile.exists()) {
            System.err.println("Skipping ${dir.name}: no manifest.json")
            return null
        }

        return try {
            val manifest = ManifestParser.parse(manifestFile.readText())
            val highlightsFile = File(dir, manifest.queries.highlights)
            if (!highlightsFile.exists()) {
                System.err.println("Skipping ${manifest.id}: missing ${manifest.queries.highlights}")
                return null
            }

            val structureScm = manifest.queries.structure?.let { path ->
                val f = File(dir, path)
                if (f.exists()) f.readText() else null
            }

            val symbolKinds = manifest.symbols?.let { path ->
                val f = File(dir, path)
                if (f.exists()) parseSymbolKinds(f.readText()) else emptyMap()
            } ?: emptyMap()

            val platform = resolvePlatform()
            val libPath = manifest.parser.library.replace("\${platform}", platform)
            val nativeLib = File(dir, libPath)

            LanguagePlugin(
                manifest = manifest,
                rootDir = dir,
                highlightsScm = highlightsFile.readText(),
                structureScm = structureScm,
                symbolKinds = symbolKinds,
                nativeLibraryPath = if (nativeLib.exists()) nativeLib else null,
            ).also {
                if (it.nativeLibraryPath == null) {
                    System.err.println(
                        "Warning: ${manifest.id} — native library not found at $libPath " +
                            "(plugin registered for queries only until library is built)",
                    )
                } else {
                    System.err.println("Loaded plugin: ${manifest.id} (${manifest.name})")
                }
            }
        } catch (e: Exception) {
            System.err.println("Failed to load plugin ${dir.name}: ${e.message}")
            null
        }
    }

    private fun parseSymbolKinds(json: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val trimmed = json.trim().removePrefix("{").removeSuffix("}")
        for (part in trimmed.split(",")) {
            val kv = part.split(":")
            if (kv.size == 2) {
                val key = kv[0].trim().trim('"')
                val value = kv[1].trim().trim('"')
                map[key] = value
            }
        }
        return map
    }
}

/**
 * Registry of loaded plugins and their tree-sitter resources.
 */
class PluginRegistry {
    private val plugins = mutableMapOf<String, LanguagePlugin>()
    private val languages = mutableMapOf<String, Language>()
    private val highlightQueries = mutableMapOf<String, Query>()
    private val structureQueries = mutableMapOf<String, Query>()

    fun register(plugin: LanguagePlugin, language: Language? = null) {
        plugins[plugin.manifest.id] = plugin
        if (language != null) {
            languages[plugin.manifest.id] = language
            highlightQueries[plugin.manifest.id] = language.query(plugin.highlightsScm)
            plugin.structureScm?.let { scm ->
                structureQueries[plugin.manifest.id] = language.query(scm)
            }
        }
    }

    fun getPlugin(id: String): LanguagePlugin? = plugins[id]
    fun getLanguage(id: String): Language? = languages[id]
    fun getHighlightQuery(id: String): Query? = highlightQueries[id]
    fun getStructureQuery(id: String): Query? = structureQueries[id]
    fun hasLanguage(id: String): Boolean = id in languages
    fun languageIds(): List<String> = plugins.keys.toList()

    fun listLanguages(): List<Map<String, Any>> = plugins.values.map { p ->
        buildMap {
            put("id", p.manifest.id)
            put("name", p.manifest.name)
            put("version", p.manifest.version)
            put(
                "queries",
                buildList {
                    add("highlights")
                    if (p.structureScm != null) add("structure")
                },
            )
            put("loaded", hasLanguage(p.manifest.id))
        }
    }
}
