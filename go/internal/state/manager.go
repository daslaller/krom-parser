package state

import (
	"sort"

	sitter "github.com/tree-sitter/go-tree-sitter"

	"github.com/daslaller/krom-parser/go/internal/ipc"
	"github.com/daslaller/krom-parser/go/internal/plugin"
	"github.com/daslaller/krom-parser/go/internal/treesitter"
)

// FileState holds parse state for one open document.
type FileState struct {
	FileID     string
	LanguageID string
	Content    []byte
	Tree       *sitter.Tree
	Version    int
}

// Manager manages parse states for all open files.
type Manager struct {
	engine  *treesitter.Engine
	plugins map[string]*plugin.Plugin
	files   map[string]*FileState
}

func NewManager(engine *treesitter.Engine, plugins []*plugin.Plugin) *Manager {
	pm := make(map[string]*plugin.Plugin)
	for _, p := range plugins {
		pm[p.Manifest.ID] = p
	}
	return &Manager{engine: engine, plugins: pm, files: make(map[string]*FileState)}
}

func (m *Manager) ParseFile(fileID, languageID string, content string) (*FileState, error) {
	if !m.engine.HasLanguage(languageID) {
		return nil, fmtUnknownLanguage(languageID)
	}
	src := []byte(content)
	tree, err := m.engine.Parse(languageID, src, nil)
	if err != nil {
		return nil, err
	}
	state := &FileState{
		FileID: fileID, LanguageID: languageID,
		Content: src, Tree: tree, Version: 1,
	}
	m.files[fileID] = state
	return state, nil
}

func (m *Manager) UpdateFile(fileID, content string) (*FileState, error) {
	existing, ok := m.files[fileID]
	if !ok {
		return nil, fmtFileNotOpen(fileID)
	}
	src := []byte(content)
	tree, err := m.engine.Parse(existing.LanguageID, src, existing.Tree)
	if err != nil {
		return nil, err
	}
	existing.Tree.Close()
	existing.Content = src
	existing.Tree = tree
	existing.Version++
	return existing, nil
}

func (m *Manager) CloseFile(fileID string) {
	if state, ok := m.files[fileID]; ok {
		state.Tree.Close()
		delete(m.files, fileID)
	}
}

func (m *Manager) GetNodeAtPosition(fileID string, line, column int) *ipc.NodeInfo {
	state, ok := m.files[fileID]
	if !ok {
		return nil
	}
	root := state.Tree.RootNode()
	point := sitter.NewPoint(uint(line), uint(column))
	node := root.NamedDescendantForPointRange(point, point)
	if node == nil {
		return nil
	}
	return nodeToInfo(node, true)
}

func (m *Manager) GetStructure(fileID string) []ipc.SymbolInfo {
	state, ok := m.files[fileID]
	if !ok {
		return nil
	}
	structSCM := m.engine.StructureQuery(state.LanguageID)
	if structSCM != "" {
		return m.extractFromStructureQuery(state, structSCM)
	}
	return m.extractSymbolsFallback(state)
}

func (m *Manager) GetHighlights(fileID string) ipc.HighlightResult {
	state, ok := m.files[fileID]
	if !ok {
		return ipc.HighlightResult{Spans: []ipc.HighlightSpan{}}
	}
	highlightSCM := m.engine.HighlightQuery(state.LanguageID)
	if highlightSCM == "" {
		return ipc.HighlightResult{Spans: []ipc.HighlightSpan{}}
	}

	lang := state.Tree.Language()
	query, err := sitter.NewQuery(lang, highlightSCM)
	if err != nil {
		return ipc.HighlightResult{Spans: []ipc.HighlightSpan{}}
	}
	defer query.Close()

	var spans []ipc.HighlightSpan
	cursor := sitter.NewQueryCursor()
	defer cursor.Close()

	captures := cursor.Captures(query, state.Tree.RootNode(), state.Content)
	names := query.CaptureNames()
	for {
		match, captureIndex := captures.Next()
		if match == nil {
			break
		}
		capture := match.Captures[captureIndex]
		captureName := ""
		if int(capture.Index) < len(names) {
			captureName = names[capture.Index]
		}
		node := capture.Node
		spans = append(spans, ipc.HighlightSpan{
			StartByte: int(node.StartByte()),
			EndByte:   int(node.EndByte()),
			Capture:   captureName,
		})
	}

	sort.Slice(spans, func(i, j int) bool { return spans[i].StartByte < spans[j].StartByte })
	return ipc.HighlightResult{Spans: spans}
}

func (m *Manager) RunQuery(fileID, querySource string) []ipc.NodeInfo {
	state, ok := m.files[fileID]
	if !ok {
		return nil
	}
	lang := state.Tree.Language()
	query, err := sitter.NewQuery(lang, querySource)
	if err != nil {
		return nil
	}
	defer query.Close()

	var nodes []ipc.NodeInfo
	cursor := sitter.NewQueryCursor()
	defer cursor.Close()

	matches := cursor.Matches(query, state.Tree.RootNode(), state.Content)
	for match := matches.Next(); match != nil; match = matches.Next() {
		for _, capture := range match.Captures {
			nodes = append(nodes, *nodeToInfo(&capture.Node, true))
		}
	}
	return nodes
}

func (m *Manager) extractFromStructureQuery(state *FileState, scm string) []ipc.SymbolInfo {
	lang := state.Tree.Language()
	query, err := sitter.NewQuery(lang, scm)
	if err != nil {
		return nil
	}
	defer query.Close()

	p := m.plugins[state.LanguageID]
	var symbols []ipc.SymbolInfo
	cursor := sitter.NewQueryCursor()
	defer cursor.Close()

	matches := cursor.Matches(query, state.Tree.RootNode(), state.Content)
	names := query.CaptureNames()

	for match := matches.Next(); match != nil; match = matches.Next() {
		var symbolNode *sitter.Node
		var name string
		for _, capture := range match.Captures {
			if int(capture.Index) >= len(names) {
				continue
			}
			switch names[capture.Index] {
			case "symbol":
				n := capture.Node
				symbolNode = &n
			case "name":
				name = capture.Node.Utf8Text(state.Content)
			}
		}
		if symbolNode != nil {
			kind := "variable"
			if p != nil {
				if k, ok := p.SymbolKinds[symbolNode.Kind()]; ok {
					kind = k
				}
			}
			symbols = append(symbols, ipc.SymbolInfo{
				Name:      nameOrDefault(name, symbolNode.Kind()),
				Kind:      kind,
				StartLine: int(symbolNode.StartPosition().Row),
				EndLine:   int(symbolNode.EndPosition().Row),
			})
		}
	}
	return symbols
}

func (m *Manager) extractSymbolsFallback(state *FileState) []ipc.SymbolInfo {
	p := m.plugins[state.LanguageID]
	if p == nil {
		return nil
	}
	return walkSymbols(state.Tree.RootNode(), state.Content, p.SymbolKinds, 0)
}

func walkSymbols(node *sitter.Node, content []byte, kinds map[string]string, depth int) []ipc.SymbolInfo {
	var symbols []ipc.SymbolInfo
	for i := uint(0); i < node.NamedChildCount(); i++ {
		child := node.NamedChild(i)
		kind, ok := kinds[child.Kind()]
		if !ok {
			continue
		}
		name := extractName(child, content)
		var children []ipc.SymbolInfo
		if depth < 2 {
			children = walkSymbols(child, content, kinds, depth+1)
		}
		symbols = append(symbols, ipc.SymbolInfo{
			Name:      nameOrDefault(name, child.Kind()),
			Kind:      kind,
			StartLine: int(child.StartPosition().Row),
			EndLine:   int(child.EndPosition().Row),
			Children:  children,
		})
	}
	return symbols
}

func extractName(node *sitter.Node, content []byte) string {
	if nameNode := node.ChildByFieldName("name"); nameNode != nil {
		return nameNode.Utf8Text(content)
	}
	for i := uint(0); i < node.NamedChildCount(); i++ {
		child := node.NamedChild(i)
		if child.Kind() == "identifier" || child.Kind() == "type_identifier" {
			return child.Utf8Text(content)
		}
	}
	return ""
}

func nodeToInfo(node *sitter.Node, shallow bool) *ipc.NodeInfo {
	if node == nil {
		return nil
	}
	info := &ipc.NodeInfo{
		Type:        node.Kind(),
		StartLine:   int(node.StartPosition().Row),
		StartColumn: int(node.StartPosition().Column),
		EndLine:     int(node.EndPosition().Row),
		EndColumn:   int(node.EndPosition().Column),
		Children:    []ipc.NodeInfo{},
	}
	if !shallow {
		for i := uint(0); i < node.NamedChildCount(); i++ {
			child := node.NamedChild(i)
			if child != nil {
				info.Children = append(info.Children, *nodeToInfo(child, true))
			}
		}
	}
	return info
}

func nameOrDefault(name, fallback string) string {
	if name != "" {
		return name
	}
	return fallback
}

type languageError string

func (e languageError) Error() string { return string(e) }

func fmtUnknownLanguage(id string) error {
	return languageError("unknown language: " + id)
}

func fmtFileNotOpen(id string) error {
	return languageError("file not open: " + id)
}
