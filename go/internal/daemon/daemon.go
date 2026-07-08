package daemon

import (
	"encoding/json"
	"fmt"
	"os"
	"time"

	"github.com/daslaller/krom-parser/go/internal/ipc"
	"github.com/daslaller/krom-parser/go/internal/plugin"
	"github.com/daslaller/krom-parser/go/internal/state"
	"github.com/daslaller/krom-parser/go/internal/treesitter"
)

// Daemon is the language-agnostic krom-parser process. See /api.md.
type Daemon struct {
	transport *ipc.Transport
	engine    *treesitter.Engine
	plugins   []*plugin.Plugin
	state     *state.Manager
}

func New(transport *ipc.Transport, engine *treesitter.Engine, plugins []*plugin.Plugin) *Daemon {
	return &Daemon{
		transport: transport,
		engine:    engine,
		plugins:   plugins,
		state:     state.NewManager(engine, plugins),
	}
}

func (d *Daemon) Run() error {
	if err := ipc.WriteReady(d.transport); err != nil {
		return err
	}

	for {
		req, err := ipc.ReadRequest(d.transport)
		if err != nil {
			return nil // EOF — clean shutdown
		}
		resp := d.handle(req)
		if err := ipc.WriteResponse(d.transport, resp); err != nil {
			return err
		}
	}
}

func (d *Daemon) handle(req *ipc.Request) ipc.Response {
	result, err := d.route(req.Method, req.Params)
	if err != nil {
		return ipc.Response{ID: req.ID, Error: &ipc.ErrorInfo{Code: -1, Message: err.Error()}}
	}
	return ipc.Response{ID: req.ID, Result: result}
}

func (d *Daemon) route(method string, params map[string]interface{}) (interface{}, error) {
	switch method {
	case "ping":
		return "pong", nil
	case "shutdown":
		go func() {
			time.Sleep(50 * time.Millisecond)
			os.Exit(0)
		}()
		return "ok", nil
	case "listLanguages":
		return d.listLanguages(), nil
	case "parseFile":
		return d.parseFile(params)
	case "updateFile":
		return d.updateFile(params)
	case "closeFile":
		return d.closeFile(params)
	case "getNodeAtPosition":
		return d.getNodeAtPosition(params)
	case "getStructure":
		return d.getStructure(params)
	case "getHighlights":
		return d.getHighlights(params)
	case "runQuery":
		return d.runQuery(params)
	default:
		return nil, fmt.Errorf("unknown method: %s", method)
	}
}

func (d *Daemon) listLanguages() []map[string]interface{} {
	loaded := map[string]bool{}
	for _, p := range d.plugins {
		loaded[p.Manifest.ID] = d.engine.HasLanguage(p.Manifest.ID)
	}
	var out []map[string]interface{}
	for _, p := range d.plugins {
		out = append(out, p.ToLanguageInfo(loaded[p.Manifest.ID]))
	}
	return out
}

func (d *Daemon) parseFile(params map[string]interface{}) (map[string]interface{}, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	content, _ := ipc.ParamString(params, "content")
	languageID, _ := ipc.ParamString(params, "languageId")

	state, err := d.state.ParseFile(fileID, languageID, content)
	if err != nil {
		return map[string]interface{}{
			"success": false,
			"error":   err.Error(),
		}, nil
	}
	return map[string]interface{}{
		"success": true,
		"version": state.Version,
	}, nil
}

func (d *Daemon) updateFile(params map[string]interface{}) (map[string]interface{}, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	content, _ := ipc.ParamString(params, "content")

	state, err := d.state.UpdateFile(fileID, content)
	if err != nil {
		return map[string]interface{}{
			"success": false,
			"error":   err.Error(),
		}, nil
	}
	return map[string]interface{}{
		"success": true,
		"version": state.Version,
	}, nil
}

func (d *Daemon) closeFile(params map[string]interface{}) (string, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	d.state.CloseFile(fileID)
	return "ok", nil
}

func (d *Daemon) getNodeAtPosition(params map[string]interface{}) (interface{}, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	line, _ := ipc.ParamInt(params, "line")
	column, _ := ipc.ParamInt(params, "column")
	node := d.state.GetNodeAtPosition(fileID, line, column)
	if node == nil {
		return nil, nil
	}
	return node, nil
}

func (d *Daemon) getStructure(params map[string]interface{}) ([]ipc.SymbolInfo, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	return d.state.GetStructure(fileID), nil
}

func (d *Daemon) getHighlights(params map[string]interface{}) (ipc.HighlightResult, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	return d.state.GetHighlights(fileID), nil
}

func (d *Daemon) runQuery(params map[string]interface{}) ([]ipc.NodeInfo, error) {
	fileID, _ := ipc.ParamString(params, "fileId")
	query, _ := ipc.ParamString(params, "query")
	return d.state.RunQuery(fileID, query), nil
}

// HandleRaw is used in tests with raw JSON body.
func (d *Daemon) HandleRaw(body []byte) ipc.Response {
	var req ipc.Request
	if err := json.Unmarshal(body, &req); err != nil {
		return ipc.Response{ID: -1, Error: &ipc.ErrorInfo{Code: -32700, Message: "parse error"}}
	}
	if req.Params == nil {
		req.Params = map[string]interface{}{}
	}
	return d.handle(&req)
}
