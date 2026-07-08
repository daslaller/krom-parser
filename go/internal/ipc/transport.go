package ipc

import (
	"encoding/json"
	"io"
)

// ReadRequest decodes a framed request from the transport.
func ReadRequest(t *Transport) (*Request, error) {
	body, err := t.Read()
	if err != nil {
		return nil, err
	}
	var req Request
	if err := json.Unmarshal(body, &req); err != nil {
		return nil, err
	}
	if req.Params == nil {
		req.Params = map[string]interface{}{}
	}
	return &req, nil
}

// WriteResponse encodes and writes a response.
func WriteResponse(t *Transport, resp Response) error {
	return t.Write(resp)
}

// WriteReady sends the startup ready notification.
func WriteReady(t *Transport) error {
	return t.Write(Response{ID: 0, Result: "krom-parser ready"})
}

// NewStdioTransport creates a transport over os.Stdin/os.Stdout.
func NewStdioTransport(in io.Reader, out io.Writer) *Transport {
	return NewTransport(in, out)
}
