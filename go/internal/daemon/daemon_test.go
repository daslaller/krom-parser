package daemon

import (
	"bytes"
	"encoding/json"
	"fmt"
	"testing"

	"github.com/daslaller/krom-parser/go/internal/ipc"
	"github.com/daslaller/krom-parser/go/internal/treesitter"
)

func TestPing(t *testing.T) {
	engine := treesitter.NewEngine()
	d := New(nil, engine, nil)

	body, _ := json.Marshal(ipc.Request{ID: 1, Method: "ping", Params: map[string]interface{}{}})
	resp := d.HandleRaw(body)
	if resp.Error != nil {
		t.Fatalf("error: %v", resp.Error)
	}
	if resp.Result != "pong" {
		t.Fatalf("expected pong, got %v", resp.Result)
	}
}

func TestListLanguagesEmpty(t *testing.T) {
	engine := treesitter.NewEngine()
	d := New(nil, engine, nil)

	body, _ := json.Marshal(ipc.Request{ID: 2, Method: "listLanguages", Params: map[string]interface{}{}})
	resp := d.HandleRaw(body)
	if resp.Error != nil {
		t.Fatalf("error: %v", resp.Error)
	}
	arr, ok := resp.Result.([]map[string]interface{})
	if !ok {
		if raw, ok := resp.Result.([]interface{}); ok && len(raw) == 0 {
			return
		}
		t.Fatalf("unexpected result: %T %v", resp.Result, resp.Result)
	}
	if len(arr) != 0 {
		t.Fatalf("expected empty, got %d", len(arr))
	}
}

func TestTransportFraming(t *testing.T) {
	var in bytes.Buffer
	var out bytes.Buffer
	transport := ipc.NewTransport(&in, &out)

	msg := `{"id":1,"method":"ping"}`
	fmt.Fprintf(&in, "Content-Length: %d\r\n\r\n%s", len(msg), msg)

	body, err := transport.Read()
	if err != nil {
		t.Fatal(err)
	}
	if string(body) != msg {
		t.Fatalf("got %q", body)
	}
}
