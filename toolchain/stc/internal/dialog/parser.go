package dialog

import (
	"math"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf8"
)

type Severity int

const (
	SeverityError Severity = iota
	SeverityWarning
)

type Span struct {
	Start int
	End   int
}

func (s Span) IsEmpty() bool { return s.Start >= s.End }

type LineKind int

const (
	KindBlank LineKind = iota
	KindComment
	KindDivider
	KindCommand
	KindDialogue
	KindIncomplete
)

type Diagnostic struct {
	Line     int
	Span     Span
	Severity Severity
	Message  string
}

type TokenType int

const (
	TokenComment TokenType = iota
	TokenKeyword
	TokenString
	TokenVariable
	TokenProperty
	TokenFunction
	TokenParameter
	TokenOperator
	TokenTypeName
	TokenModifier
	TokenNumber
)

func TokenTypeNames() []string {
	return []string{
		"comment",
		"keyword",
		"string",
		"variable",
		"property",
		"function",
		"parameter",
		"operator",
		"type",
		"modifier",
		"number",
	}
}

type Token struct {
	Span Span
	Type TokenType
}

type Script struct {
	Lines       []Line
	Diagnostics []Diagnostic
	Tokens      [][]Token
	ScriptID    string
	HasScriptID bool
	DialogNames map[string]int
	DialogRefs  []DialogRef
	StateKeys   []StateKey
}

type DialogRef struct {
	Line int
	Span Span
	Text string
}

type StateKeyType int

const (
	StateKeyBoolean StateKeyType = iota
	StateKeyInteger
	StateKeyDecimal
	StateKeyString
	StateKeyUnknown
)

func (t StateKeyType) TypeName() string {
	switch t {
	case StateKeyBoolean:
		return "boolean"
	case StateKeyInteger, StateKeyDecimal:
		return "number"
	case StateKeyString:
		return "string"
	default:
		return "any"
	}
}

type StateKey struct {
	Name   string
	Global bool
	Type   StateKeyType
}

type Line struct {
	Number    int
	Text      string
	Kind      LineKind
	Comment   *Span
	Condition *Condition
	Divider   *Divider
	Command   *CommandAction
	Dialogue  *DialogueAction
}

type Condition struct {
	Open   Span
	Expr   Span
	Close  Span
	Closed bool
}

type Divider struct {
	Dashes   Span
	Name     Span
	NameText string
}

type CommandAction struct {
	Open     Span
	Commands []Command
	Close    Span
	Closed   bool
	Selector *Selector
}

type Selector struct {
	Span Span
}

type Command struct {
	Span      Span
	FirstWord Span
}

type DialogueAction struct {
	Speaker *Span
	Colon   *Span
	Body    Span
	Text    []TextSegment
	Click   *ClickCommand
}

type ClickCommand struct {
	Open     Span
	Commands []Command
	Close    Span
	Selector *Span
}

type TextSegmentKind int

const (
	SegmentLiteral TextSegmentKind = iota
	SegmentColor
	SegmentInlineCommand
)

type TextSegment struct {
	Kind     TextSegmentKind
	Span     Span
	Commands []Command
}

func Analyze(text, scriptID string) *Script {
	s := &Script{
		Lines:       splitLines(text),
		ScriptID:    scriptID,
		HasScriptID: scriptID != "",
		DialogNames: make(map[string]int),
	}
	for i := range s.Lines {
		s.parseLine(i)
	}
	s.checkScript()
	s.Tokens = make([][]Token, len(s.Lines))
	for i := range s.Lines {
		s.Tokens[i] = highlightLine(&s.Lines[i])
	}
	sort.SliceStable(s.Diagnostics, func(i, j int) bool {
		a, b := s.Diagnostics[i], s.Diagnostics[j]
		if a.Line != b.Line {
			return a.Line < b.Line
		}
		if a.Span.Start != b.Span.Start {
			return a.Span.Start < b.Span.Start
		}
		return a.Span.End < b.Span.End
	})
	return s
}

func splitLines(text string) []Line {
	lines := make([]Line, 0, 16)
	start := 0
	for i := 0; i < len(text); i++ {
		switch text[i] {
		case '\n':
			lines = append(lines, Line{Number: len(lines), Text: text[start:i]})
			start = i + 1
		case '\r':
			end := i
			if i+1 < len(text) && text[i+1] == '\n' {
				i++
			}
			lines = append(lines, Line{Number: len(lines), Text: text[start:end]})
			start = i + 1
		}
	}
	lines = append(lines, Line{Number: len(lines), Text: text[start:]})
	return lines
}

func (s *Script) addDiagnostic(line int, span Span, severity Severity, message string) {
	s.Diagnostics = append(s.Diagnostics, Diagnostic{
		Line:     line,
		Span:     span,
		Severity: severity,
		Message:  message,
	})
}

func (s *Script) parseLine(index int) {
	line := &s.Lines[index]
	raw := line.Text
	trimmed := trimSpan(raw, Span{Start: 0, End: len(raw)})
	if trimmed.IsEmpty() {
		line.Kind = KindBlank
		return
	}
	if raw[trimmed.Start] == '#' {
		line.Kind = KindComment
		line.Comment = &trimmed
		return
	}
	if strings.HasPrefix(raw[trimmed.Start:trimmed.End], "---") {
		line.Kind = KindDivider
		line.Divider = s.parseDivider(index, trimmed)
		return
	}

	rest := trimmed
	if raw[rest.Start] == '(' {
		closing := findClosing(raw[rest.Start:rest.End], '(', ')')
		if closing < 0 {
			s.addDiagnostic(index, Span{Start: rest.Start, End: rest.Start + 1}, SeverityError,
				"Condition is missing a closing parenthesis")
			line.Kind = KindIncomplete
			line.Condition = &Condition{
				Open: Span{Start: rest.Start, End: rest.Start + 1},
				Expr: trimSpan(raw, Span{Start: rest.Start + 1, End: rest.End}),
			}
			return
		}
		closingOffset := rest.Start + closing
		condition := &Condition{
			Open:   Span{Start: rest.Start, End: rest.Start + 1},
			Expr:   trimSpan(raw, Span{Start: rest.Start + 1, End: closingOffset}),
			Close:  Span{Start: closingOffset, End: closingOffset + 1},
			Closed: true,
		}
		line.Condition = condition
		if condition.Expr.IsEmpty() {
			s.addDiagnostic(index, condition.Open, SeverityError, "Condition is empty")
		}
		rest = trimSpan(raw, Span{Start: closingOffset + 1, End: trimmed.End})
		if rest.IsEmpty() {
			s.addDiagnostic(index, condition.Open, SeverityError, "Condition is not followed by an action")
			line.Kind = KindIncomplete
			return
		}
	}

	if raw[rest.Start] == '[' {
		line.Kind = KindCommand
		line.Command = s.parseCommandAction(index, rest)
		return
	}
	line.Kind = KindDialogue
	line.Dialogue = s.parseDialogueAction(index, rest)
}

func (s *Script) parseDivider(index int, span Span) *Divider {
	raw := s.Lines[index].Text
	divider := &Divider{
		Dashes: Span{Start: span.Start, End: span.Start + 3},
		Name:   trimSpan(raw, Span{Start: span.Start + 3, End: span.End}),
	}
	divider.NameText = raw[divider.Name.Start:divider.Name.End]
	if divider.Name.IsEmpty() {
		s.addDiagnostic(index, divider.Dashes, SeverityError, "Divider is missing a dialog name")
		return divider
	}
	if !validDialogName(divider.NameText) {
		s.addDiagnostic(index, divider.Name, SeverityError, "Invalid dialog name: "+divider.NameText)
	}
	return divider
}

func ValidDialogName(name string) bool {
	return validDialogName(name)
}

func validDialogName(name string) bool {
	if name == "" {
		return false
	}
	return !strings.ContainsRune(name, ':') && !hasWhitespace(name)
}

func (s *Script) parseCommandAction(index int, span Span) *CommandAction {
	raw := s.Lines[index].Text
	action := &CommandAction{Open: Span{Start: span.Start, End: span.Start + 1}}
	closing := findClosing(raw[span.Start:span.End], '[', ']')
	if closing < 0 {
		s.addDiagnostic(index, action.Open, SeverityError, "Command action is missing a closing bracket")
		action.Commands = s.parseCommands(index, Span{Start: span.Start + 1, End: span.End})
		return action
	}
	closingOffset := span.Start + closing
	action.Close = Span{Start: closingOffset, End: closingOffset + 1}
	action.Closed = true
	action.Commands = s.parseCommands(index, Span{Start: span.Start + 1, End: closingOffset})
	s.validateWaitCommands(index, action.Commands)

	tailStart := closingOffset + 1
	codeEnd := span.End
	if relative := strings.IndexByte(raw[tailStart:span.End], '#'); relative >= 0 {
		commentStart := tailStart + relative
		s.Lines[index].Comment = &Span{Start: commentStart, End: span.End}
		codeEnd = commentStart
	}
	code := trimSpan(raw, Span{Start: tailStart, End: codeEnd})
	if code.IsEmpty() {
		return action
	}
	if raw[code.Start] != '@' {
		s.addDiagnostic(index, code, SeverityError,
			"Unexpected content after command action: "+raw[code.Start:code.End])
		return action
	}
	name := Span{Start: code.Start + 1, End: code.End}
	if name.IsEmpty() || hasWhitespace(raw[name.Start:name.End]) {
		s.addDiagnostic(index, code, SeverityError, "Invalid selector: "+raw[code.Start:code.End])
		return action
	}
	action.Selector = &Selector{Span: code}
	return action
}

func (s *Script) parseDialogueAction(index int, span Span) *DialogueAction {
	raw := s.Lines[index].Text
	contentEnd := span.End
	for i := span.Start; i < span.End; i++ {
		if raw[i] == '#' && (i == span.Start || raw[i-1] != '\\') {
			contentEnd = i
			s.Lines[index].Comment = &Span{Start: i, End: span.End}
			break
		}
	}
	content := trimSpan(raw, Span{Start: span.Start, End: contentEnd})
	action := &DialogueAction{Body: content}
	if !content.IsEmpty() {
		if relative := strings.IndexByte(raw[content.Start:content.End], ':'); relative >= 0 {
			colon := content.Start + relative
			speaker := trimSpan(raw, Span{Start: content.Start, End: colon})
			if speaker.IsEmpty() {
				s.addDiagnostic(index, Span{Start: colon, End: colon + 1}, SeverityError, "Speaker is empty")
			} else {
				action.Speaker = &speaker
			}
			action.Colon = &Span{Start: colon, End: colon + 1}
			action.Body = trimSpan(raw, Span{Start: colon + 1, End: content.End})
		}
	}
	textSpan, click := s.splitClickCommand(index, action.Body)
	action.Text = s.parseText(index, textSpan)
	action.Click = click
	return action
}

func (s *Script) splitClickCommand(index int, body Span) (Span, *ClickCommand) {
	raw := s.Lines[index].Text
	sourceStart, sourceEnd := body.Start, body.End
	var selector *Span

	if relative := strings.LastIndexByte(raw[sourceStart:sourceEnd], '@'); relative >= 0 {
		at := sourceStart + relative
		candidate := raw[at+1 : sourceEnd]
		beforeEnd := trimRight(raw, sourceStart, at)
		if candidate != "" && !hasWhitespace(candidate) && beforeEnd > sourceStart && raw[beforeEnd-1] == ']' {
			selector = &Span{Start: at, End: sourceEnd}
			sourceEnd = beforeEnd
		}
	}

	if sourceEnd <= sourceStart || raw[sourceEnd-1] != ']' {
		return body, nil
	}
	closing := sourceEnd - 1
	opening := findOpening(raw[sourceStart:sourceEnd], closing-sourceStart)
	if opening < 0 {
		return body, nil
	}
	opening += sourceStart
	commands := s.parseCommands(index, Span{Start: opening + 1, End: closing})
	for _, command := range commands {
		if spanText(raw, command.FirstWord) == "wait" {
			s.addDiagnostic(index, command.FirstWord, SeverityError,
				"wait cannot be used in a click command")
		}
	}
	text := trimSpan(raw, Span{Start: sourceStart, End: opening})
	return text, &ClickCommand{
		Open:     Span{Start: opening, End: opening + 1},
		Commands: commands,
		Close:    Span{Start: closing, End: closing + 1},
		Selector: selector,
	}
}

func (s *Script) parseCommands(index int, span Span) []Command {
	raw := s.Lines[index].Text
	commands := make([]Command, 0, 4)
	segmentStart := span.Start
	for i := span.Start; i < span.End; i++ {
		if raw[i] == '\\' && i+1 < span.End && raw[i+1] == ';' {
			i++
			continue
		}
		if raw[i] == ';' {
			s.appendCommand(index, &commands, segmentStart, i)
			segmentStart = i + 1
		}
	}
	s.appendCommand(index, &commands, segmentStart, span.End)
	return commands
}

func (s *Script) appendCommand(index int, commands *[]Command, start, end int) {
	raw := s.Lines[index].Text
	trimmed := trimSpan(raw, Span{Start: start, End: end})
	if trimmed.IsEmpty() {
		s.addDiagnostic(index, pointSpan(raw, start), SeverityError, "Command is empty")
		return
	}
	firstWordEnd := trimmed.Start
	for firstWordEnd < trimmed.End {
		r, size := utf8.DecodeRuneInString(raw[firstWordEnd:trimmed.End])
		if unicode.IsSpace(r) {
			break
		}
		firstWordEnd += size
	}
	*commands = append(*commands, Command{
		Span:      trimmed,
		FirstWord: Span{Start: trimmed.Start, End: firstWordEnd},
	})
}

func (s *Script) validateWaitCommands(index int, commands []Command) {
	raw := s.Lines[index].Text
	for _, command := range commands {
		if spanText(raw, command.FirstWord) != "wait" {
			continue
		}
		rest := trimSpan(raw, Span{Start: command.FirstWord.End, End: command.Span.End})
		if rest.IsEmpty() {
			continue
		}
		timeout := spanText(raw, rest)
		if !validWaitTicks(timeout) {
			s.addDiagnostic(index, rest, SeverityError, "Invalid wait timeout: "+timeout)
		}
	}
}

var waitTicksPattern = regexp.MustCompile(`^[+-]?[0-9]+$`)

func validWaitTicks(text string) bool {
	if !waitTicksPattern.MatchString(text) {
		return false
	}
	value, err := strconv.ParseInt(text, 10, 64)
	return err == nil && value >= 0 && value <= math.MaxInt32
}

func (s *Script) parseText(index int, span Span) []TextSegment {
	raw := s.Lines[index].Text
	segments := make([]TextSegment, 0, 4)
	literalStart := span.Start
	flush := func(end int) {
		if end > literalStart {
			segments = append(segments, TextSegment{
				Kind: SegmentLiteral,
				Span: Span{Start: literalStart, End: end},
			})
		}
	}

	for i := span.Start; i < span.End; {
		switch raw[i] {
		case '\\':
			if i+1 < span.End && (raw[i+1] == '#' || raw[i+1] == '$') {
				i += 2
			} else {
				i++
			}
		case '&':
			if i+1 < span.End && isColorCode(raw[i+1]) {
				flush(i)
				segments = append(segments, TextSegment{
					Kind: SegmentColor,
					Span: Span{Start: i, End: i + 2},
				})
				i += 2
				literalStart = i
			} else {
				i++
			}
		case '$':
			relative := strings.IndexByte(raw[i+1:span.End], '$')
			if relative < 0 {
				s.addDiagnostic(index, Span{Start: i, End: i + 1}, SeverityError,
					"Inline command is missing a closing $")
				i = span.End
				continue
			}
			closing := i + 1 + relative
			flush(i)
			commands := s.parseCommands(index, Span{Start: i + 1, End: closing})
			s.rejectFlowCommands(index, commands)
			segments = append(segments, TextSegment{
				Kind:     SegmentInlineCommand,
				Span:     Span{Start: i, End: closing + 1},
				Commands: commands,
			})
			i = closing + 1
			literalStart = i
		default:
			i++
		}
	}
	flush(span.End)
	return segments
}

func (s *Script) rejectFlowCommands(index int, commands []Command) {
	raw := s.Lines[index].Text
	for _, command := range commands {
		if _, ok := flowKeywords[flowWord(spanText(raw, command.FirstWord))]; !ok {
			continue
		}
		s.addDiagnostic(index, command.Span, SeverityError,
			"Flow command cannot be used in an inline command: "+spanText(raw, command.Span))
	}
}

func isColorCode(char byte) bool {
	return strings.IndexByte("0123456789abcdefklmnorABCDEFKLMNOR", char) >= 0
}

func hasWhitespace(text string) bool {
	for _, r := range text {
		if unicode.IsSpace(r) {
			return true
		}
	}
	return false
}

func trimSpan(text string, span Span) Span {
	start, end := span.Start, span.End
	if start < 0 {
		start = 0
	}
	if end > len(text) {
		end = len(text)
	}
	for start < end {
		r, size := utf8.DecodeRuneInString(text[start:end])
		if !unicode.IsSpace(r) {
			break
		}
		start += size
	}
	for end > start {
		r, size := utf8.DecodeLastRuneInString(text[start:end])
		if !unicode.IsSpace(r) {
			break
		}
		end -= size
	}
	if start > end {
		start = end
	}
	return Span{Start: start, End: end}
}

func trimRight(text string, start, end int) int {
	for end > start {
		r, size := utf8.DecodeLastRuneInString(text[start:end])
		if !unicode.IsSpace(r) {
			break
		}
		end -= size
	}
	return end
}

func pointSpan(text string, index int) Span {
	if index < 0 {
		index = 0
	}
	if index >= len(text) {
		if len(text) == 0 {
			return Span{}
		}
		return Span{Start: len(text) - 1, End: len(text)}
	}
	_, size := utf8.DecodeRuneInString(text[index:])
	return Span{Start: index, End: index + size}
}

func spanText(text string, span Span) string {
	start, end := span.Start, span.End
	if start < 0 {
		start = 0
	}
	if end > len(text) {
		end = len(text)
	}
	if start > end {
		start = end
	}
	return text[start:end]
}

func findClosing(text string, open, close byte) int {
	depth := 0
	for i := 0; i < len(text); i++ {
		switch text[i] {
		case open:
			depth++
		case close:
			depth--
			if depth == 0 {
				return i
			}
		}
	}
	return -1
}

func findOpening(text string, closingIndex int) int {
	depth := 0
	for i := closingIndex; i >= 0; i-- {
		switch text[i] {
		case ']':
			depth++
		case '[':
			depth--
			if depth == 0 {
				return i
			}
		}
	}
	return -1
}
