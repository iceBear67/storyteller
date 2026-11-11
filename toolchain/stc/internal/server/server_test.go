package server

import (
	"encoding/json"
	"testing"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

func send(t *testing.T, handler *protocol.Handler, method string, params string) any {
	t.Helper()
	result, validMethod, validParams, err := handler.Handle(&glsp.Context{
		Method: method,
		Params: json.RawMessage(params),
		Notify: func(string, any) {},
	})
	if !validMethod {
		t.Fatalf("%s: unknown method", method)
	}
	if !validParams {
		t.Fatalf("%s: invalid params", method)
	}
	if err != nil {
		t.Fatalf("%s: %v", method, err)
	}
	return result
}

func TestInitializeCapabilities(t *testing.T) {
	s := New()
	result := send(t, s.Handler(), "initialize", `{"capabilities":{}}`)
	initializeResult, ok := result.(protocol.InitializeResult)
	if !ok {
		t.Fatalf("unexpected initialize result %T", result)
	}
	options, ok := initializeResult.Capabilities.SemanticTokensProvider.(*protocol.SemanticTokensOptions)
	if !ok {
		t.Fatalf("unexpected semantic tokens provider %T", initializeResult.Capabilities.SemanticTokensProvider)
	}
	if len(options.Legend.TokenTypes) != len(dialog.TokenTypeNames()) {
		t.Fatalf("unexpected token legend %+v", options.Legend.TokenTypes)
	}
	if initializeResult.Capabilities.TextDocumentSync == nil {
		t.Fatal("expected text document sync options")
	}
	if initializeResult.Capabilities.DefinitionProvider != true {
		t.Fatal("expected definition provider")
	}
	if initializeResult.Capabilities.ExecuteCommandProvider == nil ||
		len(initializeResult.Capabilities.ExecuteCommandProvider.Commands) != 1 ||
		initializeResult.Capabilities.ExecuteCommandProvider.Commands[0] != stateSymbolsCommand {
		t.Fatalf("unexpected execute command provider %+v", initializeResult.Capabilities.ExecuteCommandProvider)
	}
}

func TestDiagnosticsAndTokens(t *testing.T) {
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)

	var published *protocol.PublishDiagnosticsParams
	_, validMethod, validParams, err := handler.Handle(&glsp.Context{
		Method: "textDocument/didOpen",
		Params: json.RawMessage(`{
			"textDocument": {
				"uri": "file:///game/data/storyteller/dialog/chapter/test.txt",
				"languageId": "storyteller-dialog",
				"version": 1,
				"text": "Alice: hi\n[]"
			}
		}`),
		Notify: func(method string, params any) {
			if method == protocol.ServerTextDocumentPublishDiagnostics {
				value, ok := params.(protocol.PublishDiagnosticsParams)
				if !ok {
					t.Fatalf("unexpected diagnostics params %T", params)
				}
				published = &value
			}
		},
	})
	if !validMethod || !validParams || err != nil {
		t.Fatalf("didOpen failed: %v %v %v", validMethod, validParams, err)
	}
	if published == nil {
		t.Fatal("expected diagnostics to be published")
	}
	if len(published.Diagnostics) != 1 {
		t.Fatalf("unexpected diagnostics %+v", published.Diagnostics)
	}
	if published.Diagnostics[0].Message != "Command is empty" {
		t.Fatalf("unexpected message %q", published.Diagnostics[0].Message)
	}
	if published.Diagnostics[0].Range.Start.Line != 1 {
		t.Fatalf("unexpected range %+v", published.Diagnostics[0].Range)
	}

	raw := send(t, handler, "textDocument/semanticTokens/full", `{
		"textDocument": {"uri": "file:///game/data/storyteller/dialog/chapter/test.txt"}
	}`)
	tokens, ok := raw.(*protocol.SemanticTokens)
	if !ok {
		t.Fatalf("unexpected semantic tokens result %T", raw)
	}
	if len(tokens.Data) != 25 {
		t.Fatalf("unexpected token data %v", tokens.Data)
	}
}
