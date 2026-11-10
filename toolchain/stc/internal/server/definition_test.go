package server

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"testing"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"
)

func openDocument(t *testing.T, handler *protocol.Handler, uri, text string) {
	t.Helper()
	params := fmt.Sprintf(`{"textDocument":{"uri":%q,"languageId":"storyteller-dialog","version":1,"text":%q}}`, uri, text)
	_, validMethod, validParams, err := handler.Handle(&glsp.Context{
		Method: "textDocument/didOpen",
		Params: json.RawMessage(params),
		Notify: func(string, any) {},
	})
	if !validMethod || !validParams || err != nil {
		t.Fatalf("didOpen failed: %v %v %v", validMethod, validParams, err)
	}
}

func definitionAt(t *testing.T, handler *protocol.Handler, uri string, line, character int) any {
	t.Helper()
	return send(t, handler, "textDocument/definition", fmt.Sprintf(
		`{"textDocument":{"uri":%q},"position":{"line":%d,"character":%d}}`,
		uri, line, character,
	))
}

func definitionWorkspace(t *testing.T) (string, string) {
	t.Helper()
	dialogDir := filepath.Join(t.TempDir(), "data", "test", "dialog")
	if err := os.MkdirAll(dialogDir, 0o755); err != nil {
		t.Fatal(err)
	}
	sourcePath := filepath.Join(dialogDir, "start.txt")
	targetPath := filepath.Join(dialogDir, "other.txt")
	if err := os.WriteFile(targetPath, []byte("--- branch\nAlice: hi\n"), 0o644); err != nil {
		t.Fatal(err)
	}
	return sourcePath, targetPath
}

func TestDefinitionLocalDialog(t *testing.T) {
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	uri := "file:///game/data/storyteller/dialog/chapter/test.txt"
	openDocument(t, handler, uri, "[goto done]\n--- done\nAlice: hi\n")

	raw := definitionAt(t, handler, uri, 0, 7)
	locations, ok := raw.([]protocol.Location)
	if !ok {
		t.Fatalf("unexpected definition result %T", raw)
	}
	if len(locations) != 1 {
		t.Fatalf("unexpected locations %+v", locations)
	}
	if string(locations[0].URI) != uri {
		t.Fatalf("unexpected uri %q", locations[0].URI)
	}
	if locations[0].Range.Start.Line != 1 || locations[0].Range.Start.Character != 4 {
		t.Fatalf("unexpected start %+v", locations[0].Range.Start)
	}
	if locations[0].Range.End.Character != 8 {
		t.Fatalf("unexpected end %+v", locations[0].Range.End)
	}
}

func TestDefinitionCrossScriptDialog(t *testing.T) {
	sourcePath, targetPath := definitionWorkspace(t)
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, "[goto other:branch]\n")

	raw := definitionAt(t, handler, sourceURI, 0, 10)
	locations, ok := raw.([]protocol.Location)
	if !ok {
		t.Fatalf("unexpected definition result %T", raw)
	}
	if len(locations) != 1 {
		t.Fatalf("unexpected locations %+v", locations)
	}
	if string(locations[0].URI) != fileURI(targetPath) {
		t.Fatalf("unexpected uri %q", locations[0].URI)
	}
	if locations[0].Range.Start.Line != 0 || locations[0].Range.Start.Character != 4 {
		t.Fatalf("unexpected start %+v", locations[0].Range.Start)
	}
	if locations[0].Range.End.Character != 10 {
		t.Fatalf("unexpected end %+v", locations[0].Range.End)
	}
}

func TestDefinitionScriptDefaultDialog(t *testing.T) {
	sourcePath, targetPath := definitionWorkspace(t)
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, "[goto other:other]\n")

	raw := definitionAt(t, handler, sourceURI, 0, 10)
	locations, ok := raw.([]protocol.Location)
	if !ok {
		t.Fatalf("unexpected definition result %T", raw)
	}
	if len(locations) != 1 {
		t.Fatalf("unexpected locations %+v", locations)
	}
	if string(locations[0].URI) != fileURI(targetPath) {
		t.Fatalf("unexpected uri %q", locations[0].URI)
	}
	if locations[0].Range.Start.Line != 0 || locations[0].Range.Start.Character != 0 {
		t.Fatalf("unexpected start %+v", locations[0].Range.Start)
	}
	if locations[0].Range.End.Line != 0 || locations[0].Range.End.Character != 0 {
		t.Fatalf("unexpected end %+v", locations[0].Range.End)
	}
}

func TestDefinitionUnknownDialog(t *testing.T) {
	sourcePath, _ := definitionWorkspace(t)
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, "[goto other:missing]\n")

	if raw := definitionAt(t, handler, sourceURI, 0, 10); raw != nil {
		t.Fatalf("unexpected definition %+v", raw)
	}
}
