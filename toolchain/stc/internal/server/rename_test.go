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

func renameWorkspace(t *testing.T, source string) (string, string) {
	t.Helper()
	dialogDir := filepath.Join(t.TempDir(), "data", "test", "dialog")
	if err := os.MkdirAll(dialogDir, 0o755); err != nil {
		t.Fatal(err)
	}
	sourcePath := filepath.Join(dialogDir, "start.txt")
	if err := os.WriteFile(sourcePath, []byte(source), 0o644); err != nil {
		t.Fatal(err)
	}
	return sourcePath, fileURI(sourcePath)
}

func requireEdit(t *testing.T, edits []protocol.TextEdit, line, start, end int, newText string) {
	t.Helper()
	for _, edit := range edits {
		if int(edit.Range.Start.Line) == line &&
			int(edit.Range.Start.Character) == start &&
			int(edit.Range.End.Line) == line &&
			int(edit.Range.End.Character) == end &&
			edit.NewText == newText {
			return
		}
	}
	t.Fatalf("missing edit %d:%d-%d -> %q in %+v", line, start, end, newText, edits)
}

func sendRename(t *testing.T, handler *protocol.Handler, uri string, line, character int, newName string) any {
	t.Helper()
	return send(t, handler, "textDocument/rename", fmt.Sprintf(
		`{"textDocument":{"uri":%q},"position":{"line":%d,"character":%d},"newName":%q}`,
		uri, line, character, newName,
	))
}

func sendRenameError(t *testing.T, handler *protocol.Handler, uri string, line, character int, newName string) {
	t.Helper()
	params := fmt.Sprintf(
		`{"textDocument":{"uri":%q},"position":{"line":%d,"character":%d},"newName":%q}`,
		uri, line, character, newName,
	)
	_, validMethod, validParams, err := handler.Handle(&glsp.Context{
		Method: "textDocument/rename",
		Params: json.RawMessage(params),
		Notify: func(string, any) {},
	})
	if !validMethod || !validParams {
		t.Fatalf("rename: invalid request %v %v", validMethod, validParams)
	}
	if err == nil {
		t.Fatal("expected rename error")
	}
}

func TestPrepareRename(t *testing.T) {
	sourcePath, sourceURI := renameWorkspace(t, "[goto done]\n--- done\nAlice: hi\n")
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	openDocument(t, handler, sourceURI, "[goto done]\n--- done\nAlice: hi\n")

	raw := send(t, handler, "textDocument/prepareRename", fmt.Sprintf(
		`{"textDocument":{"uri":%q},"position":{"line":0,"character":7}}`,
		sourceURI,
	))
	result, ok := raw.(protocol.RangeWithPlaceholder)
	if !ok {
		t.Fatalf("unexpected prepareRename result %T for %s", raw, sourcePath)
	}
	if result.Placeholder != "done" {
		t.Fatalf("unexpected placeholder %q", result.Placeholder)
	}
	if result.Range.Start.Line != 0 || result.Range.Start.Character != 6 {
		t.Fatalf("unexpected start %+v", result.Range.Start)
	}
	if result.Range.End.Character != 10 {
		t.Fatalf("unexpected end %+v", result.Range.End)
	}
}

func TestRenameLocalDialogFromReference(t *testing.T) {
	_, sourceURI := renameWorkspace(t, "[goto done]\n--- done\nAlice: hi\n")
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	openDocument(t, handler, sourceURI, "[goto done]\n--- done\nAlice: hi\n")

	raw := sendRename(t, handler, sourceURI, 0, 7, "finished")
	edit, ok := raw.(*protocol.WorkspaceEdit)
	if !ok {
		t.Fatalf("unexpected rename result %T", raw)
	}
	edits := edit.Changes[protocol.DocumentUri(sourceURI)]
	requireEdit(t, edits, 0, 6, 10, "finished")
	requireEdit(t, edits, 1, 4, 8, "finished")
}

func TestRenameLocalDialogFromDivider(t *testing.T) {
	_, sourceURI := renameWorkspace(t, "[goto done]\n--- done\nAlice: hi\n")
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	openDocument(t, handler, sourceURI, "[goto done]\n--- done\nAlice: hi\n")

	raw := sendRename(t, handler, sourceURI, 1, 5, "finished")
	edit, ok := raw.(*protocol.WorkspaceEdit)
	if !ok {
		t.Fatalf("unexpected rename result %T", raw)
	}
	edits := edit.Changes[protocol.DocumentUri(sourceURI)]
	requireEdit(t, edits, 0, 6, 10, "finished")
	requireEdit(t, edits, 1, 4, 8, "finished")
}

func TestRenameCrossScriptDialog(t *testing.T) {
	sourcePath, targetPath := definitionWorkspace(t)
	source := "[goto other:branch]\n"
	if err := os.WriteFile(sourcePath, []byte(source), 0o644); err != nil {
		t.Fatal(err)
	}
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, source)

	raw := sendRename(t, handler, sourceURI, 0, 13, "section")
	edit, ok := raw.(*protocol.WorkspaceEdit)
	if !ok {
		t.Fatalf("unexpected rename result %T", raw)
	}
	requireEdit(t, edit.Changes[protocol.DocumentUri(sourceURI)], 0, 12, 18, "section")
	requireEdit(t, edit.Changes[protocol.DocumentUri(fileURI(targetPath))], 0, 4, 10, "section")
}

func TestRenameRejectsInvalidName(t *testing.T) {
	_, sourceURI := renameWorkspace(t, "[goto done]\n--- done\nAlice: hi\n")
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	openDocument(t, handler, sourceURI, "[goto done]\n--- done\nAlice: hi\n")
	sendRenameError(t, handler, sourceURI, 0, 7, "bad name")
}

func TestRenameRejectsDefaultDialog(t *testing.T) {
	sourcePath, _ := definitionWorkspace(t)
	source := "[goto other:other]\n"
	if err := os.WriteFile(sourcePath, []byte(source), 0o644); err != nil {
		t.Fatal(err)
	}
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, source)
	sendRenameError(t, handler, sourceURI, 0, 13, "renamed")
}

func TestRenameRejectsUnknownDialog(t *testing.T) {
	sourcePath, _ := definitionWorkspace(t)
	source := "[goto other:missing]\n"
	if err := os.WriteFile(sourcePath, []byte(source), 0o644); err != nil {
		t.Fatal(err)
	}
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI, source)
	sendRenameError(t, handler, sourceURI, 0, 13, "renamed")
}
