package dev.krom.parser.state

import io.github.treesitter.ktreesitter.Tree

/**
 * Holds the current parse state for a single file.
 */
data class FileState(
    val fileId: String,
    val languageId: String,
    var content: String,
    var tree: Tree,
    var version: Int = 1,
)
