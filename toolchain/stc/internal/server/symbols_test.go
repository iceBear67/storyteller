package server

import (
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/tliron/glsp"
)

func TestStateSymbols(t *testing.T) {
	dataDir := filepath.Join(t.TempDir(), "data")
	writeScript := func(namespace, name, source string) string {
		t.Helper()
		directory := filepath.Join(dataDir, namespace, "dialog")
		if err := os.MkdirAll(directory, 0o755); err != nil {
			t.Fatal(err)
		}
		path := filepath.Join(directory, name)
		if err := os.WriteFile(path, []byte(source), 0o644); err != nil {
			t.Fatal(err)
		}
		return path
	}
	sourcePath := writeScript("alpha", "start.txt", "[update score 1; update global stage 2; update flag true; update name hello]")
	writeScript("beta", "quest.txt", "[update score 3.5; update visited true; update flag \"text\"; delete removed; delete global gone]")

	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	sourceURI := fileURI(sourcePath)
	openDocument(t, handler, sourceURI,
		"[update score 1; update global stage 2; update flag true; update name hello; update fresh 5]")

	raw := send(t, handler, "workspace/executeCommand", fmt.Sprintf(
		`{"command":"storyteller.stateSymbols","arguments":[%q]}`, sourceURI,
	))
	result, ok := raw.(stateSymbolsResult)
	if !ok {
		t.Fatalf("unexpected state symbols result %T", raw)
	}
	if actual := symbolTypes(result.Player); actual != "flag:any,fresh:number,name:string,removed:any,score:number,visited:boolean" {
		t.Fatalf("unexpected player symbols %s", actual)
	}
	if actual := symbolTypes(result.Global); actual != "gone:any,stage:number" {
		t.Fatalf("unexpected global symbols %s", actual)
	}
}

func symbolTypes(symbols []stateSymbol) string {
	values := make([]string, len(symbols))
	for index, symbol := range symbols {
		values[index] = symbol.Name + ":" + symbol.Type
	}
	return strings.Join(values, ",")
}

func TestStateSymbolsRequireDialogDocument(t *testing.T) {
	s := New()
	handler := s.Handler()
	send(t, handler, "initialize", `{"capabilities":{}}`)
	_, validMethod, validParams, err := handler.Handle(&glsp.Context{
		Method: "workspace/executeCommand",
		Params: json.RawMessage(`{"command":"storyteller.stateSymbols","arguments":["file:///tmp/start.txt"]}`),
		Notify: func(string, any) {},
	})
	if !validMethod || !validParams {
		t.Fatalf("invalid request %v %v", validMethod, validParams)
	}
	if err == nil {
		t.Fatal("expected an error for a document outside a dialog directory")
	}
}
