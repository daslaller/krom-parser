package treesitter

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/daslaller/krom-parser/go/internal/plugin"
)

// FromPluginDir creates an engine with all plugins from the given directory.
func FromPluginDir(pluginDir string) (*Engine, []*plugin.Plugin, error) {
	plugins, err := plugin.LoadAll(pluginDir)
	if err != nil {
		return nil, nil, err
	}

	engine := NewEngine()
	for _, p := range plugins {
		if !p.NativeLibExists {
			continue
		}
		lang, err := LoadLanguageFromPlugin(p.NativeLibrary, p.Manifest.Parser.Entry)
		if err != nil {
			fmt.Fprintf(os.Stderr, "Failed to load grammar for %s: %v\n", p.Manifest.ID, err)
			continue
		}
		engine.RegisterLanguage(p.Manifest.ID, lang, p.HighlightsSCM, p.StructureSCM)
	}

	return engine, plugins, nil
}

// AbsPluginDir resolves a plugin directory path.
func AbsPluginDir(path string) string {
	abs, err := filepath.Abs(path)
	if err != nil {
		return path
	}
	return abs
}
