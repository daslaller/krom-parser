package treesitter

import (
	"testing"
)

func TestPythonPluginParse(t *testing.T) {
	engine, _, err := FromPluginDir("/workspace/krom-parser/plugins")
	if err != nil {
		t.Fatalf("load plugins: %v", err)
	}
	defer engine.Close()

	if !engine.HasLanguage("python") {
		t.Fatal("python grammar not loaded")
	}

	tree, err := engine.Parse("python", []byte("def hello():\n  return 1\n"), nil)
	if err != nil {
		t.Fatalf("parse: %v", err)
	}
	defer tree.Close()

	root := tree.RootNode()
	if root.Kind() != "module" {
		t.Fatalf("expected module root, got %q", root.Kind())
	}
}
