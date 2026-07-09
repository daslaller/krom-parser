package treesitter

/*
#cgo linux LDFLAGS: -ldl
#cgo darwin LDFLAGS: -ldl
#include <dlfcn.h>
#include <stdlib.h>

typedef const void *(*ts_language_fn)(void);

static const void *load_language(const char* lib_path, const char* sym_name) {
    void* handle = dlopen(lib_path, RTLD_LAZY | RTLD_GLOBAL);
    if (!handle) return NULL;
    ts_language_fn fn = (ts_language_fn)dlsym(handle, sym_name);
    if (!fn) return NULL;
    return fn();
}
*/
import "C"
import (
	"fmt"
	"os"
	"unsafe"

	sitter "github.com/tree-sitter/go-tree-sitter"
)

// LoadLanguage dynamically loads a tree-sitter grammar from a shared library.
// The exported symbol must be a function returning const TSLanguage* (e.g. tree_sitter_python).
func LoadLanguage(libraryPath, entrySymbol string) (*sitter.Language, error) {
	cPath := C.CString(libraryPath)
	cSym := C.CString(entrySymbol)
	defer C.free(unsafe.Pointer(cPath))
	defer C.free(unsafe.Pointer(cSym))

	ptr := C.load_language(cPath, cSym)
	if ptr == nil {
		return nil, fmt.Errorf("failed to load %s from %s", entrySymbol, libraryPath)
	}

	return sitter.NewLanguage(unsafe.Pointer(ptr)), nil
}

// Engine is the language-agnostic tree-sitter engine.
type Engine struct {
	parser    *sitter.Parser
	languages map[string]*sitter.Language
	queries   map[string]*pluginQueries
}

type pluginQueries struct {
	highlights string
	structure  string
}

// NewEngine creates an empty engine.
func NewEngine() *Engine {
	return &Engine{
		parser:    sitter.NewParser(),
		languages: make(map[string]*sitter.Language),
		queries:   make(map[string]*pluginQueries),
	}
}

// RegisterLanguage adds a language and its query sources.
func (e *Engine) RegisterLanguage(id string, lang *sitter.Language, highlights, structure string) {
	e.languages[id] = lang
	e.queries[id] = &pluginQueries{highlights: highlights, structure: structure}
}

// HasLanguage reports whether a native grammar is loaded for id.
func (e *Engine) HasLanguage(id string) bool {
	_, ok := e.languages[id]
	return ok
}

// Parse parses source for the given language.
func (e *Engine) Parse(languageID string, source []byte, oldTree *sitter.Tree) (*sitter.Tree, error) {
	lang, ok := e.languages[languageID]
	if !ok {
		return nil, fmt.Errorf("unknown language: %s", languageID)
	}
	if err := e.parser.SetLanguage(lang); err != nil {
		return nil, fmt.Errorf("incompatible grammar for %s: %w", languageID, err)
	}
	tree := e.parser.Parse(source, oldTree)
	if tree == nil {
		return nil, fmt.Errorf("parse failed for %s", languageID)
	}
	return tree, nil
}

// HighlightQuery returns the highlights.scm source for a language.
func (e *Engine) HighlightQuery(languageID string) string {
	if q, ok := e.queries[languageID]; ok {
		return q.highlights
	}
	return ""
}

// StructureQuery returns the structure.scm source for a language.
func (e *Engine) StructureQuery(languageID string) string {
	if q, ok := e.queries[languageID]; ok {
		return q.structure
	}
	return ""
}

// Close releases parser resources.
func (e *Engine) Close() {
	if e.parser != nil {
		e.parser.Close()
	}
}

// LoadLanguageFromPlugin attempts to load a grammar from a plugin's native library.
func LoadLanguageFromPlugin(libraryPath, entrySymbol string) (*sitter.Language, error) {
	if _, err := os.Stat(libraryPath); err != nil {
		return nil, err
	}
	return LoadLanguage(libraryPath, entrySymbol)
}
