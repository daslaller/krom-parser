package dev.krom.parser.state

import dev.krom.parser.ipc.NodeInfo
import dev.krom.parser.ipc.SymbolInfo
import dev.krom.parser.treesitter.ParserEngine
import io.github.treesitter.ktreesitter.InputEdit
import io.github.treesitter.ktreesitter.Node
import io.github.treesitter.ktreesitter.Point

/**
 * Manages parse states for all open files.
 *
 * Handles parsing, incremental re-parsing, and structural queries.
 */
class FileStateManager(private val engine: ParserEngine) {

    private val files = mutableMapOf<String, FileState>()

    /**
     * Parses a file for the first time.
     */
    fun parseFile(fileId: String, content: String, languageId: String): FileState? {
        val tree = engine.parse(languageId, content) ?: return null
        val state = FileState(fileId, languageId, content, tree)
        files[fileId] = state
        return state
    }

    /**
     * Re-parses a file with new content.
     * Passes the old tree for incremental parsing.
     */
    fun updateFile(fileId: String, content: String): FileState? {
        val existing = files[fileId] ?: return null
        val newTree = engine.parse(existing.languageId, content, existing.tree) ?: return null
        existing.content = content
        existing.tree = newTree
        existing.version++
        return existing
    }

    /**
     * Closes a file and removes its state.
     */
    fun closeFile(fileId: String) {
        files.remove(fileId)
    }

    /**
     * Returns the AST node at a given position.
     */
    fun getNodeAtPosition(fileId: String, line: Int, column: Int): NodeInfo? {
        val state = files[fileId] ?: return null
        val point = Point(line.toUInt(), column.toUInt())
        val node = state.tree.rootNode.namedDescendant(point, point) ?: return null
        return nodeToInfo(node, shallow = true)
    }

    /**
     * Extracts top-level structure (classes, functions, methods) from the parse tree.
     */
    fun getStructure(fileId: String): List<SymbolInfo> {
        val state = files[fileId] ?: return emptyList()
        return extractSymbols(state.tree.rootNode, state.content)
    }

    /**
     * Runs a tree-sitter query and returns matching nodes.
     */
    fun runQuery(fileId: String, querySource: String): List<NodeInfo> {
        val state = files[fileId] ?: return emptyList()
        val query = engine.highlightQuery(state.languageId) ?: return emptyList()

        // For custom queries, create a temporary query
        val lang = state.tree.language
        val q = lang.query(querySource)
        return q.matches(state.tree.rootNode).flatMap { match ->
            match.captures.map { capture ->
                nodeToInfo(capture.node, shallow = true)
            }
        }.toList()
    }

    fun hasFile(fileId: String): Boolean = fileId in files

    // ── Internal ────────────────────────────────────────────────────────────

    private fun extractSymbols(node: Node, source: String, depth: Int = 0): List<SymbolInfo> {
        val symbols = mutableListOf<SymbolInfo>()

        for (child in node.namedChildren) {
            val kind = classifyNode(child.type) ?: continue

            val name = extractName(child, source) ?: child.type
            val children = if (depth < 2) extractSymbols(child, source, depth + 1) else emptyList()

            symbols.add(
                SymbolInfo(
                    name = name,
                    kind = kind,
                    startLine = child.startPoint.row.toInt(),
                    endLine = child.endPoint.row.toInt(),
                    children = children,
                )
            )
        }

        return symbols
    }

    /**
     * Extracts the name of a symbol node by looking for an identifier child
     * or reading the "name" field.
     */
    private fun extractName(node: Node, source: String): String? {
        // Try the "name" field first — most grammars use this
        val nameNode = node.childByFieldName("name")
        if (nameNode != null) {
            return nameNode.text()?.toString()
        }
        // Fallback: find first identifier child
        for (child in node.namedChildren) {
            if (child.type == "identifier" || child.type == "type_identifier") {
                return child.text()?.toString()
            }
        }
        return null
    }

    /**
     * Maps tree-sitter node types to editor symbol kinds.
     * Covers common patterns across languages.
     */
    private fun classifyNode(type: String): String? = when (type) {
        "class_definition", "class_declaration", "class_body" -> "class"
        "enum_declaration", "enum_definition" -> "enum"
        "function_definition", "function_declaration", "function_signature" -> "function"
        "method_definition", "method_declaration", "method_signature" -> "method"
        "constructor_declaration", "constructor_definition" -> "method"
        "interface_declaration" -> "interface"
        "struct_declaration", "struct_definition", "struct_item" -> "struct"
        "field_declaration", "field_definition" -> "field"
        "variable_declaration", "const_declaration", "lexical_declaration" -> "variable"
        else -> null
    }

    private fun nodeToInfo(node: Node, shallow: Boolean = false): NodeInfo {
        val children = if (!shallow) {
            node.namedChildren.map { nodeToInfo(it, shallow = true) }
        } else {
            emptyList()
        }

        return NodeInfo(
            type = node.type,
            startLine = node.startPoint.row.toInt(),
            startColumn = node.startPoint.column.toInt(),
            endLine = node.endPoint.row.toInt(),
            endColumn = node.endPoint.column.toInt(),
            children = children,
        )
    }
}
