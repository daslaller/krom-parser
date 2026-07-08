package dev.krom.parser.state

import dev.krom.parser.ipc.HighlightResult
import dev.krom.parser.ipc.HighlightSpan
import dev.krom.parser.ipc.NodeInfo
import dev.krom.parser.ipc.SymbolInfo
import dev.krom.parser.plugin.PluginRegistry
import dev.krom.parser.treesitter.ParserEngine
import io.github.treesitter.ktreesitter.Node
import io.github.treesitter.ktreesitter.Point

/**
 * Manages parse states for all open files.
 * Language-specific behaviour comes from plugins via [PluginRegistry].
 */
class FileStateManager(
    private val engine: ParserEngine,
    private val registry: PluginRegistry,
) {

    private val files = mutableMapOf<String, FileState>()

    fun parseFile(fileId: String, content: String, languageId: String): FileState? {
        if (!engine.hasLanguage(languageId)) return null
        val tree = engine.parse(languageId, content) ?: return null
        val state = FileState(fileId, languageId, content, tree)
        files[fileId] = state
        return state
    }

    fun updateFile(fileId: String, content: String): FileState? {
        val existing = files[fileId] ?: return null
        val newTree = engine.parse(existing.languageId, content, existing.tree) ?: return null
        existing.content = content
        existing.tree = newTree
        existing.version++
        return existing
    }

    fun closeFile(fileId: String) {
        files.remove(fileId)
    }

    fun getNodeAtPosition(fileId: String, line: Int, column: Int): NodeInfo? {
        val state = files[fileId] ?: return null
        val point = Point(line.toUInt(), column.toUInt())
        val node = state.tree.rootNode.namedDescendant(point, point) ?: return null
        return nodeToInfo(node, shallow = true)
    }

    fun getStructure(fileId: String): List<SymbolInfo> {
        val state = files[fileId] ?: return emptyList()
        val structureQuery = engine.structureQuery(state.languageId)
        if (structureQuery != null) {
            return extractFromStructureQuery(state, structureQuery)
        }
        return extractSymbolsFallback(state.tree.rootNode, state.content, state.languageId)
    }

    fun getHighlights(fileId: String): HighlightResult {
        val state = files[fileId] ?: return HighlightResult(emptyList())
        val query = engine.highlightQuery(state.languageId) ?: return HighlightResult(emptyList())
        val spans = mutableListOf<HighlightSpan>()
        for (match in query.matches(state.tree.rootNode)) {
            for (capture in match.captures) {
                spans.add(
                    HighlightSpan(
                        startByte = capture.node.startByte.toInt(),
                        endByte = capture.node.endByte.toInt(),
                        capture = capture.name,
                    ),
                )
            }
        }
        spans.sortBy { it.startByte }
        return HighlightResult(spans)
    }

    fun runQuery(fileId: String, querySource: String): List<NodeInfo> {
        val state = files[fileId] ?: return emptyList()
        val lang = state.tree.language
        val q = lang.query(querySource)
        return q.matches(state.tree.rootNode).flatMap { match ->
            match.captures.map { capture -> nodeToInfo(capture.node, shallow = true) }
        }
    }

    // ── Structure extraction ────────────────────────────────────────────────

    private fun extractFromStructureQuery(
        state: FileState,
        query: io.github.treesitter.ktreesitter.Query,
    ): List<SymbolInfo> {
        val plugin = registry.getPlugin(state.languageId) ?: return emptyList()
        val symbols = mutableListOf<SymbolInfo>()

        for (match in query.matches(state.tree.rootNode)) {
            var symbolNode: Node? = null
            var name: String? = null

            for (capture in match.captures) {
                when {
                    capture.name == "symbol" -> symbolNode = capture.node
                    capture.name == "name" -> name = capture.node.text()?.toString()
                }
            }

            if (symbolNode != null) {
                val kind = plugin.symbolKinds[symbolNode.type] ?: "variable"
                symbols.add(
                    SymbolInfo(
                        name = name ?: symbolNode.type,
                        kind = kind,
                        startLine = symbolNode.startPoint.row.toInt(),
                        endLine = symbolNode.endPoint.row.toInt(),
                    ),
                )
            }
        }
        return symbols
    }

    private fun extractSymbolsFallback(
        node: Node,
        source: String,
        languageId: String,
        depth: Int = 0,
    ): List<SymbolInfo> {
        val plugin = registry.getPlugin(languageId) ?: return emptyList()
        val symbols = mutableListOf<SymbolInfo>()

        for (child in node.namedChildren) {
            val kind = plugin.symbolKinds[child.type] ?: continue
            val name = extractName(child) ?: child.type
            val children =
                if (depth < 2) extractSymbolsFallback(child, source, languageId, depth + 1)
                else emptyList()

            symbols.add(
                SymbolInfo(
                    name = name,
                    kind = kind,
                    startLine = child.startPoint.row.toInt(),
                    endLine = child.endPoint.row.toInt(),
                    children = children,
                ),
            )
        }
        return symbols
    }

    private fun extractName(node: Node): String? {
        val nameNode = node.childByFieldName("name")
        if (nameNode != null) return nameNode.text()?.toString()
        for (child in node.namedChildren) {
            if (child.type == "identifier" || child.type == "type_identifier") {
                return child.text()?.toString()
            }
        }
        return null
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
