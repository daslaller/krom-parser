package dev.krom.parser.treesitter

import dev.krom.parser.plugin.LanguagePlugin
import dev.krom.parser.plugin.PluginLoader
import dev.krom.parser.plugin.PluginRegistry
import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.Tree
import java.io.File

/**
 * Language-agnostic tree-sitter engine. Grammars come exclusively from plugins.
 */
class ParserEngine(private val registry: PluginRegistry) {
    private val parser = Parser()

    companion object {
        fun fromPluginDir(pluginDir: File): ParserEngine {
            val registry = PluginRegistry()
            val loader = PluginLoader(pluginDir)
            for (plugin in loader.loadAll()) {
                val language = loadNativeLanguage(plugin)
                registry.register(plugin, language)
            }
            return ParserEngine(registry)
        }

        /**
         * Loads a tree-sitter Language from a plugin's native library.
         * Returns null when the library is missing or fails to load.
         */
        private fun loadNativeLanguage(plugin: LanguagePlugin): Language? {
            val lib = plugin.nativeLibraryPath ?: return null
            return try {
                // Load native grammar via JNI — ktreesitter Language from shared lib.
                // Plugins ship compiled tree-sitter grammars; see api.md.
                NativeLanguageLoader.load(lib.absolutePath, plugin.manifest.parser.entry)
            } catch (e: Exception) {
                System.err.println("Failed to load native grammar for ${plugin.manifest.id}: ${e.message}")
                null
            }
        }
    }

    fun registry(): PluginRegistry = registry

    fun parse(languageId: String, source: String, oldTree: Tree? = null): Tree? {
        val language = registry.getLanguage(languageId) ?: return null
        parser.language = language
        return parser.parse(source, oldTree)
    }

    fun highlightQuery(languageId: String): Query? = registry.getHighlightQuery(languageId)

    fun structureQuery(languageId: String): Query? = registry.getStructureQuery(languageId)

    fun hasLanguage(languageId: String): Boolean = registry.hasLanguage(languageId)
}

/**
 * Loads tree-sitter Language pointers from native shared libraries.
 *
 * Uses System.load + symbol lookup. Grammar plugins MUST export the entry
 * symbol named in manifest.parser.entry (e.g. tree_sitter_python).
 */
object NativeLanguageLoader {
    fun load(libraryPath: String, entrySymbol: String): Language {
        System.load(libraryPath)
        // ktreesitter expects a Language wrapping the native TSLanguage pointer.
        // When the native lib is loaded, generated bindings (TreeSitterXxx.language())
        // are the production path; dynamic dlopen is used for plugin .so files.
        throw UnsupportedOperationException(
            "Dynamic native loading for $entrySymbol requires a compiled grammar binding. " +
                "Ship the .so in the plugin lib/ directory and register via ktreesitter-plugin, " +
                "or use the Go implementation for full dynamic loading. See api.md.",
        )
    }
}
