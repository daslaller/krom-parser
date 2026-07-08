package dev.krom.parser.treesitter

import io.github.treesitter.ktreesitter.Language
import io.github.treesitter.ktreesitter.Parser
import io.github.treesitter.ktreesitter.Query
import io.github.treesitter.ktreesitter.Tree

/**
 * Thin wrapper around the official kotlin-tree-sitter bindings.
 *
 * Holds a parser instance and registered languages.
 * All the heavy lifting is done by ktreesitter — this just provides
 * a convenient API for the daemon's use case.
 */
class ParserEngine {
    private val parser = Parser()
    private val languages = mutableMapOf<String, Language>()
    private val highlightQueries = mutableMapOf<String, Query>()

    /**
     * Registers a language with its grammar.
     *
     * @param languageId Identifier like "dart", "python", "rust"
     * @param language The tree-sitter Language instance
     * @param highlightScm Optional highlights.scm query source for syntax highlighting
     */
    fun registerLanguage(languageId: String, language: Language, highlightScm: String? = null) {
        languages[languageId] = language
        if (highlightScm != null) {
            highlightQueries[languageId] = language.query(highlightScm)
        }
    }

    /**
     * Parses source code for the given language.
     * Returns null if the language is not registered.
     */
    fun parse(languageId: String, source: String, oldTree: Tree? = null): Tree? {
        val language = languages[languageId] ?: return null
        parser.language = language
        return parser.parse(source, oldTree)
    }

    /**
     * Returns the highlight query for a language, if registered.
     */
    fun highlightQuery(languageId: String): Query? = highlightQueries[languageId]

    fun hasLanguage(languageId: String): Boolean = languageId in languages
}
