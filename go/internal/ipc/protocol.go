package ipc

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"strconv"
	"strings"
	"sync"
)

// Transport reads/writes Content-Length framed JSON over stdio (LSP wire format).
type Transport struct {
	in  io.Reader
	out io.Writer
	mu  sync.Mutex
}

func NewTransport(in io.Reader, out io.Writer) *Transport {
	return &Transport{in: in, out: out}
}

func (t *Transport) Read() ([]byte, error) {
	reader := bufio.NewReader(t.in)
	contentLength := -1

	for {
		line, err := reader.ReadString('\n')
		if err != nil {
			if err == io.EOF {
				return nil, io.EOF
			}
			return nil, err
		}
		line = strings.TrimRight(line, "\r\n")
		if line == "" {
			if contentLength < 0 {
				return nil, fmt.Errorf("missing Content-Length header")
			}
			break
		}
		if strings.HasPrefix(strings.ToLower(line), "content-length:") {
			n, err := strconv.Atoi(strings.TrimSpace(line[15:]))
			if err != nil {
				return nil, err
			}
			contentLength = n
		}
	}

	body := make([]byte, contentLength)
	_, err := io.ReadFull(reader, body)
	return body, err
}

func (t *Transport) Write(v any) error {
	body, err := json.Marshal(v)
	if err != nil {
		return err
	}
	header := fmt.Sprintf("Content-Length: %d\r\n\r\n", len(body))
	t.mu.Lock()
	defer t.mu.Unlock()
	if _, err := io.Copy(t.out, strings.NewReader(header)); err != nil {
		return err
	}
	_, err = t.out.Write(body)
	return err
}

// Request is a JSON-RPC style request from the editor.
type Request struct {
	ID     int                    `json:"id"`
	Method string                 `json:"method"`
	Params map[string]interface{} `json:"params"`
}

// Response is a JSON-RPC style response to the editor.
type Response struct {
	ID     int         `json:"id"`
	Result interface{} `json:"result,omitempty"`
	Error  *ErrorInfo  `json:"error,omitempty"`
}

type ErrorInfo struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
}

// Shared result types — see /api.md

type NodeInfo struct {
	Type        string     `json:"type"`
	StartLine   int        `json:"startLine"`
	StartColumn int        `json:"startColumn"`
	EndLine     int        `json:"endLine"`
	EndColumn   int        `json:"endColumn"`
	Children    []NodeInfo `json:"children"`
}

type SymbolInfo struct {
	Name      string       `json:"name"`
	Kind      string       `json:"kind"`
	StartLine int          `json:"startLine"`
	EndLine   int          `json:"endLine"`
	Children  []SymbolInfo `json:"children"`
}

type HighlightSpan struct {
	StartByte int    `json:"startByte"`
	EndByte   int    `json:"endByte"`
	Capture   string `json:"capture"`
}

type HighlightResult struct {
	Spans []HighlightSpan `json:"spans"`
}

type LanguageInfo struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	Version string   `json:"version"`
	Queries []string `json:"queries"`
	Loaded  bool     `json:"loaded"`
}

func ParamString(params map[string]interface{}, key string) (string, error) {
	v, ok := params[key]
	if !ok {
		return "", fmt.Errorf("missing param: %s", key)
	}
	s, ok := v.(string)
	if !ok {
		return "", fmt.Errorf("param %s must be string", key)
	}
	return s, nil
}

func ParamInt(params map[string]interface{}, key string) (int, error) {
	v, ok := params[key]
	if !ok {
		return 0, fmt.Errorf("missing param: %s", key)
	}
	switch n := v.(type) {
	case float64:
		return int(n), nil
	case int:
		return n, nil
	default:
		return 0, fmt.Errorf("param %s must be int", key)
	}
}

func EncodeResult(v interface{}) json.RawMessage {
	b, _ := json.Marshal(v)
	return b
}

func RawJSON(v interface{}) interface{} {
	var buf bytes.Buffer
	enc := json.NewEncoder(&buf)
	_ = enc.Encode(v)
	var out interface{}
	_ = json.Unmarshal(buf.Bytes(), &out)
	return out
}
