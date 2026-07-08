package plugin

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
)

// Manifest describes a language plugin — see /api.md.
type Manifest struct {
	ID      string       `json:"id"`
	Name    string       `json:"name"`
	Version string       `json:"version"`
	Parser  ParserConfig `json:"parser"`
	Queries QueryConfig  `json:"queries"`
	Symbols string       `json:"symbols,omitempty"`
}

type ParserConfig struct {
	Entry   string `json:"entry"`
	Library string `json:"library"`
}

type QueryConfig struct {
	Highlights string `json:"highlights"`
	Structure  string `json:"structure,omitempty"`
}

// Plugin is a loaded language plugin with resolved paths and query sources.
type Plugin struct {
	Manifest        Manifest
	RootDir         string
	HighlightsSCM   string
	StructureSCM    string
	SymbolKinds     map[string]string
	NativeLibrary   string
	NativeLibExists bool
}

// ResolvePlatform returns the ${platform} token for the current OS/arch.
func ResolvePlatform() string {
	switch runtime.GOOS {
	case "linux":
		if runtime.GOARCH == "arm64" {
			return "linux-arm64"
		}
		return "linux-x64"
	case "windows":
		return "win-x64"
	case "darwin":
		if runtime.GOARCH == "arm64" {
			return "darwin-arm64"
		}
		return "darwin-x64"
	default:
		return "linux-x64"
	}
}

// LoadAll scans pluginDir for language plugin subdirectories.
func LoadAll(pluginDir string) ([]*Plugin, error) {
	entries, err := os.ReadDir(pluginDir)
	if err != nil {
		return nil, fmt.Errorf("plugin directory not found: %s", pluginDir)
	}

	var plugins []*Plugin
	for _, e := range entries {
		if !e.IsDir() || strings.HasPrefix(e.Name(), "_") {
			continue
		}
		p, err := Load(filepath.Join(pluginDir, e.Name()))
		if err != nil {
			fmt.Fprintf(os.Stderr, "Skipping %s: %v\n", e.Name(), err)
			continue
		}
		plugins = append(plugins, p)
	}
	return plugins, nil
}

// Load reads a single plugin directory.
func Load(dir string) (*Plugin, error) {
	manifestPath := filepath.Join(dir, "manifest.json")
	data, err := os.ReadFile(manifestPath)
	if err != nil {
		return nil, fmt.Errorf("no manifest.json")
	}

	var manifest Manifest
	if err := json.Unmarshal(data, &manifest); err != nil {
		return nil, err
	}

	highlightsPath := filepath.Join(dir, manifest.Queries.Highlights)
	highlights, err := os.ReadFile(highlightsPath)
	if err != nil {
		return nil, fmt.Errorf("missing %s", manifest.Queries.Highlights)
	}

	var structureSCM string
	if manifest.Queries.Structure != "" {
		structPath := filepath.Join(dir, manifest.Queries.Structure)
		if b, err := os.ReadFile(structPath); err == nil {
			structureSCM = string(b)
		}
	}

	symbolKinds := map[string]string{}
	if manifest.Symbols != "" {
		symbolsPath := filepath.Join(dir, manifest.Symbols)
		if b, err := os.ReadFile(symbolsPath); err == nil {
			_ = json.Unmarshal(b, &symbolKinds)
		}
	}

	platform := ResolvePlatform()
	libRel := strings.ReplaceAll(manifest.Parser.Library, "${platform}", platform)
	nativeLib := filepath.Join(dir, libRel)
	_, statErr := os.Stat(nativeLib)

	p := &Plugin{
		Manifest:        manifest,
		RootDir:         dir,
		HighlightsSCM:   string(highlights),
		StructureSCM:    structureSCM,
		SymbolKinds:     symbolKinds,
		NativeLibrary:   nativeLib,
		NativeLibExists: statErr == nil,
	}

	if !p.NativeLibExists {
		fmt.Fprintf(os.Stderr, "Warning: %s — native library not found at %s\n",
			manifest.ID, libRel)
	} else {
		fmt.Fprintf(os.Stderr, "Loaded plugin: %s (%s)\n", manifest.ID, manifest.Name)
	}

	return p, nil
}

// LanguageInfo returns API metadata for listLanguages.
func (p *Plugin) LanguageInfo(loaded bool) ipcLanguageInfo {
	queries := []string{"highlights"}
	if p.StructureSCM != "" {
		queries = append(queries, "structure")
	}
	return ipcLanguageInfo{
		ID:      p.Manifest.ID,
		Name:    p.Manifest.Name,
		Version: p.Manifest.Version,
		Queries: queries,
		Loaded:  loaded,
	}
}

// ipcLanguageInfo avoids import cycle with ipc package.
type ipcLanguageInfo struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	Version string   `json:"version"`
	Queries []string `json:"queries"`
	Loaded  bool     `json:"loaded"`
}

func (p *Plugin) ToLanguageInfo(loaded bool) map[string]interface{} {
	info := p.LanguageInfo(loaded)
	return map[string]interface{}{
		"id":      info.ID,
		"name":    info.Name,
		"version": info.Version,
		"queries": info.Queries,
		"loaded":  info.Loaded,
	}
}
