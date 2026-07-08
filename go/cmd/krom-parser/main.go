package main

import (
	"flag"
	"fmt"
	"os"

	"github.com/daslaller/krom-parser/go/internal/daemon"
	"github.com/daslaller/krom-parser/go/internal/ipc"
	"github.com/daslaller/krom-parser/go/internal/treesitter"
)

func main() {
	pluginDir := flag.String("plugin-dir", "../plugins", "directory containing language plugins")
	flag.Parse()

	absDir := treesitter.AbsPluginDir(*pluginDir)

	fmt.Fprintf(os.Stderr, "krom-parser (go) starting...\n")
	fmt.Fprintf(os.Stderr, "Plugin directory: %s\n", absDir)

	engine, plugins, err := treesitter.FromPluginDir(absDir)
	if err != nil {
		fmt.Fprintf(os.Stderr, "Warning: %v\n", err)
		engine = treesitter.NewEngine()
		plugins = nil
	}
	defer engine.Close()

	loaded := 0
	for _, p := range plugins {
		if engine.HasLanguage(p.Manifest.ID) {
			loaded++
		}
	}
	fmt.Fprintf(os.Stderr, "Plugins: %d found, %d with native grammar loaded\n", len(plugins), loaded)
	fmt.Fprintf(os.Stderr, "krom-parser ready, listening on stdio\n")

	transport := ipc.NewTransport(os.Stdin, os.Stdout)
	d := daemon.New(transport, engine, plugins)
	if err := d.Run(); err != nil {
		fmt.Fprintf(os.Stderr, "daemon error: %v\n", err)
		os.Exit(1)
	}
}
