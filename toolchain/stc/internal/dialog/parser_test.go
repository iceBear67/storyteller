package dialog

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func analyze(source string) *Script {
	return Analyze(source, "")
}

func diagnosticsOf(script *Script) []string {
	messages := make([]string, 0, len(script.Diagnostics))
	for _, item := range script.Diagnostics {
		messages = append(messages, item.Message)
	}
	return messages
}

func requireDiagnostic(t *testing.T, script *Script, message string) {
	t.Helper()
	for _, candidate := range diagnosticsOf(script) {
		if candidate == message {
			return
		}
	}
	t.Fatalf("expected diagnostic %q, got %v", message, diagnosticsOf(script))
}

func requireNoDiagnostics(t *testing.T, script *Script) {
	t.Helper()
	if len(script.Diagnostics) != 0 {
		t.Fatalf("expected no diagnostics, got %v", diagnosticsOf(script))
	}
}

func spanTextOf(script *Script, line int, span Span) string {
	text := script.Lines[line].Text
	return text[span.Start:span.End]
}

func TestParseErrors(t *testing.T) {
	tests := []struct {
		name    string
		source  string
		message string
	}{
		{"divider without name", "---", "Divider is missing a dialog name"},
		{"divider with whitespace", "--- Chapter One", "Invalid dialog name: Chapter One"},
		{"divider with colon", "--- chapter:one", "Invalid dialog name: chapter:one"},
		{"condition without action", "(done)", "Condition is not followed by an action"},
		{"unclosed condition", "(done Alice: okay", "Condition is missing a closing parenthesis"},
		{"empty condition", "() Alice: okay", "Condition is empty"},
		{"unclosed inline command", "Alice: Score: $score", "Inline command is missing a closing $"},
		{"empty inline command", "Alice: Score: $$", "Command is empty"},
		{"trailing content", "[say a] leftover", "Unexpected content after command action: leftover"},
		{"empty command block", "[]", "Command is empty"},
		{"empty command sequence", "[say a;;say b]", "Command is empty"},
		{"unclosed command block", "[say a", "Command action is missing a closing bracket"},
		{"invalid selector empty", "[say a] @", "Invalid selector: @"},
		{"invalid selector whitespace", "[say a] @a b", "Invalid selector: @a b"},
		{"empty speaker", ": hello", "Speaker is empty"},
		{"wait in click command", "Alice: hello [wait 20]", "wait cannot be used in a click command"},
		{"flow command in inline command", "hello $expect a b$", "Flow command cannot be used in an inline command: expect a b"},
		{"negative wait", "[wait -1]", "Invalid wait timeout: -1"},
		{"wait word", "[wait x]", "Invalid wait timeout: x"},
		{"wait extra", "[wait 20 extra]", "Invalid wait timeout: 20 extra"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			requireDiagnostic(t, analyze(test.source), test.message)
		})
	}
}

func TestCommentLines(t *testing.T) {
	for _, source := range []string{"# this is a comment", "#", "   # indented"} {
		script := analyze(source)
		requireNoDiagnostics(t, script)
		if script.Lines[0].Kind != KindComment {
			t.Fatalf("expected comment line, got %v", script.Lines[0].Kind)
		}
	}
}

func TestDialogue(t *testing.T) {
	script := analyze("Alice: Hello, world")
	requireNoDiagnostics(t, script)
	line := &script.Lines[0]
	if line.Kind != KindDialogue {
		t.Fatalf("expected dialogue line, got %v", line.Kind)
	}
	if got := spanTextOf(script, 0, *line.Dialogue.Speaker); got != "Alice" {
		t.Fatalf("unexpected speaker %q", got)
	}
	if len(line.Dialogue.Text) != 1 || line.Dialogue.Text[0].Kind != SegmentLiteral {
		t.Fatalf("unexpected text segments %+v", line.Dialogue.Text)
	}
	if got := spanTextOf(script, 0, line.Dialogue.Text[0].Span); got != "Hello, world" {
		t.Fatalf("unexpected text %q", got)
	}
}

func TestDialogueWithoutSpeaker(t *testing.T) {
	script := analyze("It is raining heavily")
	requireNoDiagnostics(t, script)
	line := &script.Lines[0]
	if line.Dialogue.Speaker != nil {
		t.Fatalf("expected no speaker, got %q", spanTextOf(script, 0, *line.Dialogue.Speaker))
	}
}

func TestCondition(t *testing.T) {
	script := analyze("(flag.done && (count > 1)) Alice: okay")
	requireNoDiagnostics(t, script)
	line := &script.Lines[0]
	if got := spanTextOf(script, 0, line.Condition.Expr); got != "flag.done && (count > 1)" {
		t.Fatalf("unexpected condition %q", got)
	}
	if got := spanTextOf(script, 0, *line.Dialogue.Speaker); got != "Alice" {
		t.Fatalf("unexpected speaker %q", got)
	}
}

func TestColorCodes(t *testing.T) {
	script := analyze("Alice: red&ctext&rend")
	requireNoDiagnostics(t, script)
	segments := script.Lines[0].Dialogue.Text
	if len(segments) != 5 {
		t.Fatalf("unexpected segments %+v", segments)
	}
	if got := spanTextOf(script, 0, segments[1].Span); got != "&c" {
		t.Fatalf("unexpected color %q", got)
	}
	if got := spanTextOf(script, 0, segments[3].Span); got != "&r" {
		t.Fatalf("unexpected color %q", got)
	}
}

func TestUnknownColorStaysLiteral(t *testing.T) {
	script := analyze("Alice: Tom & Jerry &z")
	requireNoDiagnostics(t, script)
	segments := script.Lines[0].Dialogue.Text
	if len(segments) != 1 || segments[0].Kind != SegmentLiteral {
		t.Fatalf("unexpected segments %+v", segments)
	}
	if got := spanTextOf(script, 0, segments[0].Span); got != "Tom & Jerry &z" {
		t.Fatalf("unexpected text %q", got)
	}
}

func TestInlineCommand(t *testing.T) {
	script := analyze("Alice: Score: $score; echo hi$ points")
	requireNoDiagnostics(t, script)
	segments := script.Lines[0].Dialogue.Text
	if len(segments) != 3 || segments[1].Kind != SegmentInlineCommand {
		t.Fatalf("unexpected segments %+v", segments)
	}
	commands := segments[1].Commands
	if len(commands) != 2 {
		t.Fatalf("unexpected commands %+v", commands)
	}
	if got := spanTextOf(script, 0, commands[0].Span); got != "score" {
		t.Fatalf("unexpected command %q", got)
	}
	if got := spanTextOf(script, 0, commands[1].Span); got != "echo hi" {
		t.Fatalf("unexpected command %q", got)
	}
}

func TestInlineGetIsAllowed(t *testing.T) {
	script := analyze("hello $get key$")
	requireNoDiagnostics(t, script)
	script = analyze("Alice: hello $storyteller:get key$")
	requireNoDiagnostics(t, script)
}

func TestClickCommands(t *testing.T) {
	tests := []struct {
		source   string
		text     string
		selector string
	}{
		{"Alice: click me [say hi; say bye]", "click me", "@s"},
		{"Alice: click me [say hi] @a", "click me", "@a"},
		{"Alice: go [execute as @e[type=zombie] run say hi] @s", "go", "@s"},
		{"Alice: go [say hi] @e[type=zombie]", "go", "@e[type=zombie]"},
		{"Alice: [say hi]", "", "@s"},
		{"It is raining heavily [say hi] @a", "It is raining heavily", "@a"},
	}
	for _, test := range tests {
		script := analyze(test.source)
		requireNoDiagnostics(t, script)
		dialogue := script.Lines[0].Dialogue
		if dialogue.Click == nil {
			t.Fatalf("%q: expected a click command", test.source)
		}
		text := ""
		if len(dialogue.Text) > 0 {
			text = spanTextOf(script, 0, Span{Start: dialogue.Text[0].Span.Start, End: dialogue.Text[len(dialogue.Text)-1].Span.End})
		}
		if text != test.text {
			t.Fatalf("%q: unexpected text %q", test.source, text)
		}
		selector := "@s"
		if dialogue.Click.Selector != nil {
			selector = spanTextOf(script, 0, *dialogue.Click.Selector)
		}
		if selector != test.selector {
			t.Fatalf("%q: unexpected selector %q", test.source, selector)
		}
	}
}

func TestBracketsInTextStayLiteral(t *testing.T) {
	for _, source := range []string{"Alice: he said [laugh] once", "Alice: hm]"} {
		script := analyze(source)
		requireNoDiagnostics(t, script)
		if script.Lines[0].Dialogue.Click != nil {
			t.Fatalf("%q: expected no click command", source)
		}
	}
}

func TestEscapes(t *testing.T) {
	script := analyze("Alice: id \\#100 # comment")
	requireNoDiagnostics(t, script)
	segments := script.Lines[0].Dialogue.Text
	if got := spanTextOf(script, 0, segments[0].Span); got != "id \\#100" {
		t.Fatalf("unexpected text %q", got)
	}
	if script.Lines[0].Comment == nil {
		t.Fatal("expected an inline comment")
	}

	script = analyze("Alice: costs \\$5")
	requireNoDiagnostics(t, script)
	segments = script.Lines[0].Dialogue.Text
	if len(segments) != 1 {
		t.Fatalf("unexpected segments %+v", segments)
	}
	if got := spanTextOf(script, 0, segments[0].Span); got != "costs \\$5" {
		t.Fatalf("unexpected text %q", got)
	}

	script = analyze("\\# this is not a comment")
	requireNoDiagnostics(t, script)
	if script.Lines[0].Kind != KindDialogue {
		t.Fatalf("expected dialogue line, got %v", script.Lines[0].Kind)
	}
}

func TestCommandActions(t *testing.T) {
	script := analyze("[say a; say b] @e # description")
	requireNoDiagnostics(t, script)
	action := script.Lines[0].Command
	if action == nil {
		t.Fatal("expected a command action")
	}
	if len(action.Commands) != 2 {
		t.Fatalf("unexpected commands %+v", action.Commands)
	}
	if got := spanTextOf(script, 0, action.Selector.Span); got != "@e" {
		t.Fatalf("unexpected selector %q", got)
	}
	if script.Lines[0].Comment == nil {
		t.Fatal("expected a trailing comment")
	}
}

func TestEscapedSemicolon(t *testing.T) {
	script := analyze("[say a\\;b]")
	requireNoDiagnostics(t, script)
	action := script.Lines[0].Command
	if len(action.Commands) != 1 {
		t.Fatalf("unexpected commands %+v", action.Commands)
	}
	if got := spanTextOf(script, 0, action.Commands[0].Span); got != "say a\\;b" {
		t.Fatalf("unexpected command %q", got)
	}
}

func TestWaitCommands(t *testing.T) {
	for _, source := range []string{"[wait]", "[wait 20]", "[wait +20]", "(flag) [wait 20]"} {
		requireNoDiagnostics(t, analyze(source))
	}
}

func TestDuplicateDialogNames(t *testing.T) {
	script := analyze("--- ChapterOne\n--- ChapterOne")
	requireDiagnostic(t, script, "Duplicate dialog name: ChapterOne")

	script = Analyze("A: hello\n--- main", "main")
	requireDiagnostic(t, script, "Duplicate dialog name: main")
}

func TestDialogReferences(t *testing.T) {
	tests := []struct {
		source  string
		message string
	}{
		{"--- target\n[goto target]", ""},
		{"--- target\n[goto missing]", "Unknown dialog: missing"},
		{"--- target\n[goto other:missing]", ""},
		{"--- target\n[goto :target]", ""},
		{"--- target\n[expect target missing]", "Unknown dialog: missing"},
		{"--- target\n[goto]", "goto requires a dialog reference"},
		{"--- target\n[goto target extra]", "goto accepts exactly one dialog reference"},
		{"[expect]", "expect requires at least one dialog reference"},
		{"[goto empty:]", "Dialog reference has an empty dialog name: empty:"},
	}
	for _, test := range tests {
		script := Analyze(test.source, "main")
		if test.message == "" {
			requireNoDiagnostics(t, script)
			continue
		}
		requireDiagnostic(t, script, test.message)
	}
}

func TestStateKeys(t *testing.T) {
	script := Analyze(
		"[update score 1; update global stage \"true\"; update ratio 1.5]\n"+
			"(score) Alice: hello [update visited true]\n"+
			"Bob: inline $get score$ [update clickOnly 1.5; delete removed]\n",
		"start",
	)
	expected := []StateKey{
		{Name: "score", Type: StateKeyInteger},
		{Name: "stage", Global: true, Type: StateKeyBoolean},
		{Name: "ratio", Type: StateKeyDecimal},
		{Name: "visited", Type: StateKeyBoolean},
		{Name: "clickOnly", Type: StateKeyDecimal},
		{Name: "removed", Type: StateKeyUnknown},
	}
	if len(script.StateKeys) != len(expected) {
		t.Fatalf("unexpected state keys %+v", script.StateKeys)
	}
	for index, key := range expected {
		if script.StateKeys[index] != key {
			t.Fatalf("unexpected state key at %d: %+v", index, script.StateKeys[index])
		}
	}
}

func TestStateKeyTypes(t *testing.T) {
	tests := []struct {
		value    string
		expected StateKeyType
	}{
		{"true", StateKeyBoolean},
		{"false", StateKeyBoolean},
		{"  true  ", StateKeyBoolean},
		{`"true"`, StateKeyBoolean},
		{`'false'`, StateKeyBoolean},
		{"1", StateKeyInteger},
		{"-12", StateKeyInteger},
		{"+7", StateKeyInteger},
		{`"12"`, StateKeyInteger},
		{"1.5", StateKeyDecimal},
		{".5", StateKeyDecimal},
		{"1e3", StateKeyDecimal},
		{"-2.0E-2", StateKeyDecimal},
		{"hello", StateKeyString},
		{"hello world", StateKeyString},
		{`"hello"`, StateKeyString},
		{`"1 2"`, StateKeyString},
		{`""`, StateKeyString},
	}
	for _, test := range tests {
		if actual := classifyUpdateValue(test.value); actual != test.expected {
			t.Fatalf("unexpected type for %q: %v", test.value, actual)
		}
	}
}

func TestStateKeysRequireValue(t *testing.T) {
	script := Analyze("[update missing]", "start")
	if len(script.StateKeys) != 0 {
		t.Fatalf("unexpected state keys %+v", script.StateKeys)
	}
	requireDiagnostic(t, script, "update requires a value")
}

func TestStateKeysSkipReservedKeys(t *testing.T) {
	script := Analyze("[update dialogSuspended true; update ordinary 1; delete dialogScriptChecksum]", "start")
	if len(script.StateKeys) != 1 || script.StateKeys[0].Name != "ordinary" {
		t.Fatalf("unexpected state keys %+v", script.StateKeys)
	}
}

func TestDialogReferencesWithoutScriptID(t *testing.T) {
	for _, source := range []string{
		"--- target\n[goto target]",
		"--- target\n[goto missing]",
		"--- target\n[goto other:missing]",
	} {
		requireNoDiagnostics(t, analyze(source))
	}
}

func TestFlowCommandArguments(t *testing.T) {
	tests := []struct {
		source  string
		message string
	}{
		{"[update key]", "update requires a value"},
		{"[update]", "update requires a key"},
		{"[get]", "get requires a key"},
		{"[get a b]", "get accepts exactly one key"},
		{"[delete global]", "delete requires a key"},
		{"[update dialogStack 1]", "Key is reserved: dialogStack"},
		{"[update bad/key 1]", "Invalid key name: bad/key"},
		{"[check]", "check requires an expression"},
		{"[return foo]", `return only accepts "all"`},
		{"[return all extra]", `return only accepts "all"`},
	}
	for _, test := range tests {
		requireDiagnostic(t, analyze(test.source), test.message)
	}
	for _, source := range []string{
		"[update key value with spaces]",
		"[update global visits 1]",
		"[get global visits]",
		"[delete key]",
		"[check flag == false]",
		"[return]",
		"[return all]",
		"[update key-name +1]",
	} {
		requireNoDiagnostics(t, analyze(source))
	}
}

func TestRepeatable(t *testing.T) {
	requireDiagnostic(t, analyze("[repeatable; say hi]"), "repeatable can only be used in a click command")
	requireDiagnostic(t, analyze("Alice: hi [repeatable]"), "repeatable must be followed by a command")
	requireNoDiagnostics(t, analyze("Alice: hi [repeatable; say hi]"))
	requireNoDiagnostics(t, analyze("hello $repeatable$"))
}

func TestTokenLayout(t *testing.T) {
	source := strings.Join([]string{
		"# header",
		"--- ChapterOne",
		"(flag && count > 1) Alice: &cred text $get key$ [say hi] @a # note",
		"[update global visits 1]",
		"[expect ChapterOne decorated:mixed]",
		"Alice: plain text",
	}, "\n")
	script := analyze(source)
	for lineIndex := range script.Lines {
		previousEnd := -1
		for _, token := range script.Tokens[lineIndex] {
			if token.Span.Start < previousEnd {
				t.Fatalf("line %d: overlapping token at %d", lineIndex, token.Span.Start)
			}
			if token.Span.Start < 0 || token.Span.End > len(script.Lines[lineIndex].Text) || token.Span.Start >= token.Span.End {
				t.Fatalf("line %d: invalid token span %+v", lineIndex, token.Span)
			}
			previousEnd = token.Span.End
		}
	}
}

func TestHighlightTokens(t *testing.T) {
	tests := []struct {
		source string
		want   []string
	}{
		{"# comment", []string{"comment:# comment"}},
		{"--- ChapterOne", []string{"operator:---", "type:ChapterOne"}},
		{"[say hi] @a # c", []string{
			"operator:[", "function:say", "string:hi", "operator:]", "operator:@", "parameter:a", "comment:# c",
		}},
		{"Alice: hi", []string{"property:Alice", "operator::", "string:hi"}},
		{"(flag) Alice: hi", []string{
			"operator:(", "operator:)", "property:Alice", "operator::", "string:hi",
		}},
		{"Alice: &cred $get x$ [say hi] @a", []string{
			"property:Alice", "operator::",
			"operator:&c", "string:red ",
			"operator:$", "keyword:get", "property:x", "operator:$",
			"operator:[", "function:say", "string:hi", "operator:]", "operator:@", "parameter:a",
		}},
		{"Alice: Tom & Jerry &z", []string{"property:Alice", "operator::", "string:Tom & Jerry &z"}},
		{"[wait 20]", []string{"operator:[", "keyword:wait", "number:20", "operator:]"}},
	}
	for _, test := range tests {
		script := analyze(test.source)
		names := TokenTypeNames()
		var got []string
		for _, token := range script.Tokens[0] {
			got = append(got, names[token.Type]+":"+spanTextOf(script, 0, token.Span))
		}
		if strings.Join(got, "\n") != strings.Join(test.want, "\n") {
			t.Fatalf("%q:\nexpected %v\ngot      %v", test.source, test.want, got)
		}
	}
}

func TestExampleScript(t *testing.T) {
	path := filepath.Join("..", "..", "..", "run", "saves", "New World", "datapacks",
		"storyteller-example", "data", "storyteller", "dialog", "example.txt")
	source, err := os.ReadFile(path)
	if err != nil {
		t.Skip("example script is not available")
	}
	script := Analyze(string(source), "example")
	requireNoDiagnostics(t, script)
}

func TestLineNumbers(t *testing.T) {
	script := analyze("# comment\n[]")
	if len(script.Diagnostics) != 1 {
		t.Fatalf("unexpected diagnostics %v", diagnosticsOf(script))
	}
	if script.Diagnostics[0].Line != 1 {
		t.Fatalf("unexpected line %d", script.Diagnostics[0].Line)
	}
}

func TestCRLFLines(t *testing.T) {
	script := analyze("Alice: hello\r\n--- next\r\nBob: hi")
	requireNoDiagnostics(t, script)
	if len(script.Lines) != 3 {
		t.Fatalf("unexpected line count %d", len(script.Lines))
	}
	if script.Lines[2].Dialogue.Speaker == nil || spanTextOf(script, 2, *script.Lines[2].Dialogue.Speaker) != "Bob" {
		t.Fatalf("unexpected second dialogue")
	}
}
