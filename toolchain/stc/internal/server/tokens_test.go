package server

import (
	"testing"

	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

func TestCharColumn(t *testing.T) {
	line := "A😀: hi"
	tests := []struct {
		offset int
		want   int
	}{
		{0, 0},
		{1, 1},
		{5, 3},
		{6, 4},
		{7, 5},
		{100, 7},
	}
	for _, test := range tests {
		if got := charColumn(line, test.offset); got != test.want {
			t.Fatalf("offset %d: expected %d, got %d", test.offset, test.want, got)
		}
	}
}

func TestDiagnosticRange(t *testing.T) {
	line := "A😀: hi"
	rangeValue := diagnosticRange(3, line, dialog.Span{Start: 5, End: 6})
	if rangeValue.Start.Line != 3 || rangeValue.End.Line != 3 {
		t.Fatalf("unexpected lines %+v", rangeValue)
	}
	if rangeValue.Start.Character != 3 || rangeValue.End.Character != 4 {
		t.Fatalf("unexpected characters %+v", rangeValue)
	}
}

func TestEncodeTokens(t *testing.T) {
	script := dialog.Analyze("A: hi", "")
	data := encodeTokens(script, nil)
	want := []protocol.UInteger{
		0, 0, 1, protocol.UInteger(dialog.TokenProperty), 0,
		0, 1, 1, protocol.UInteger(dialog.TokenOperator), 0,
		0, 2, 2, protocol.UInteger(dialog.TokenString), 0,
	}
	if len(data) != len(want) {
		t.Fatalf("unexpected data length %d: %v", len(data), data)
	}
	for i := range want {
		if data[i] != want[i] {
			t.Fatalf("index %d: expected %d, got %d (%v)", i, want[i], data[i], data)
		}
	}
}

func TestEncodeTokensRange(t *testing.T) {
	script := dialog.Analyze("[say hi] @a", "")
	filter := protocol.Range{
		Start: protocol.Position{Line: 0, Character: 9},
		End:   protocol.Position{Line: 0, Character: 11},
	}
	data := encodeTokens(script, &filter)
	want := []protocol.UInteger{
		0, 9, 1, protocol.UInteger(dialog.TokenOperator), 0,
		0, 1, 1, protocol.UInteger(dialog.TokenParameter), 0,
	}
	if len(data) != len(want) {
		t.Fatalf("unexpected data length %d: %v", len(data), data)
	}
	for i := range want {
		if data[i] != want[i] {
			t.Fatalf("index %d: expected %d, got %d (%v)", i, want[i], data[i], data)
		}
	}
}

func TestScriptIDFromURI(t *testing.T) {
	tests := []struct {
		uri   string
		want  string
		found bool
	}{
		{"file:///game/data/storyteller/dialog/chapter/one.txt", "chapter/one", true},
		{"file:///game/data/mymod/dialog/example.txt", "example", true},
		{"file:///game/data/storyteller/dialog/", "", false},
		{"file:///game/other/example.txt", "", false},
		{"untitled:Untitled-1", "", false},
	}
	for _, test := range tests {
		got, found := scriptIDFromURI(test.uri)
		if got != test.want || found != test.found {
			t.Fatalf("%q: expected (%q, %v), got (%q, %v)", test.uri, test.want, test.found, got, found)
		}
	}
}

func TestApplyChanges(t *testing.T) {
	text := "Alice: hi\nBob: bye"
	start := protocol.Position{Line: 0, Character: 7}
	end := protocol.Position{Line: 0, Character: 9}
	changed := applyChanges(text, []any{
		protocol.TextDocumentContentChangeEvent{Range: &protocol.Range{Start: start, End: end}, Text: "hello"},
	})
	if changed != "Alice: hello\nBob: bye" {
		t.Fatalf("unexpected text %q", changed)
	}
	changed = applyChanges(changed, []any{
		protocol.TextDocumentContentChangeEventWhole{Text: "replaced"},
	})
	if changed != "replaced" {
		t.Fatalf("unexpected text %q", changed)
	}
}
