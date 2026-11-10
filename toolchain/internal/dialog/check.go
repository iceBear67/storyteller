package dialog

import (
	"strings"
	"unicode"
	"unicode/utf8"
)

var flowKeywords = map[string]struct{}{
	"expect": {},
	"check":  {},
	"update": {},
	"goto":   {},
	"delete": {},
	"return": {},
	"wait":   {},
}

var scriptCommands = map[string]struct{}{
	"expect": {},
	"check":  {},
	"update": {},
	"goto":   {},
	"get":    {},
	"delete": {},
	"return": {},
	"wait":   {},
}

var reservedKeys = map[string]struct{}{
	"dialogStack":          {},
	"dialogScriptChecksum": {},
	"dialogSuspended":      {},
}

type commandContext int

const (
	contextCommandAction commandContext = iota
	contextClickCommand
	contextInlineCommand
)

func flowWord(word string) string {
	return strings.TrimPrefix(word, "storyteller:")
}

func (s *Script) checkScript() {
	if s.HasScriptID {
		s.DialogNames[s.ScriptID] = -1
	}
	for i := range s.Lines {
		line := &s.Lines[i]
		if line.Kind != KindDivider || line.Divider == nil {
			continue
		}
		divider := line.Divider
		if divider.Name.IsEmpty() || !validDialogName(divider.NameText) {
			continue
		}
		if _, exists := s.DialogNames[divider.NameText]; exists {
			s.addDiagnostic(i, divider.Name, SeverityError, "Duplicate dialog name: "+divider.NameText)
			continue
		}
		s.DialogNames[divider.NameText] = i
	}

	for i := range s.Lines {
		line := &s.Lines[i]
		if line.Command != nil {
			s.checkCommands(i, line.Command.Commands, contextCommandAction)
		}
		if line.Dialogue != nil {
			if line.Dialogue.Click != nil {
				s.checkCommands(i, line.Dialogue.Click.Commands, contextClickCommand)
			}
			for _, segment := range line.Dialogue.Text {
				if segment.Kind == SegmentInlineCommand {
					s.checkCommands(i, segment.Commands, contextInlineCommand)
				}
			}
		}
	}
}

func (s *Script) checkCommands(line int, commands []Command, context commandContext) {
	if len(commands) == 0 {
		return
	}
	raw := s.Lines[line].Text
	start := 0
	if spanText(raw, commands[0].Span) == "repeatable" {
		switch context {
		case contextCommandAction:
			s.addDiagnostic(line, commands[0].Span, SeverityError,
				"repeatable can only be used in a click command")
		case contextClickCommand:
			if len(commands) == 1 {
				s.addDiagnostic(line, commands[0].Span, SeverityError,
					"repeatable must be followed by a command")
				return
			}
			start = 1
		}
	}
	for _, command := range commands[start:] {
		s.checkCommand(line, command, context)
	}
}

func (s *Script) checkCommand(line int, command Command, context commandContext) {
	raw := s.Lines[line].Text
	keyword := flowWord(spanText(raw, command.FirstWord))
	if _, ok := scriptCommands[keyword]; !ok {
		return
	}
	if context == contextInlineCommand && keyword != "get" {
		return
	}
	s.checkFlowCommand(line, command, keyword)
}

func (s *Script) checkFlowCommand(line int, command Command, keyword string) {
	raw := s.Lines[line].Text
	words := wordSpans(raw, command.Span)
	switch keyword {
	case "expect":
		if len(words) < 2 {
			s.addDiagnostic(line, command.Span, SeverityError,
				"expect requires at least one dialog reference")
			return
		}
		for _, word := range words[1:] {
			s.checkDialogRef(line, word)
		}
	case "goto":
		if len(words) < 2 {
			s.addDiagnostic(line, command.Span, SeverityError,
				"goto requires a dialog reference")
			return
		}
		if len(words) > 2 {
			s.addDiagnostic(line, Span{Start: words[2].Start, End: words[len(words)-1].End}, SeverityError,
				"goto accepts exactly one dialog reference")
			return
		}
		s.checkDialogRef(line, words[1])
	case "update":
		index := 1
		if index < len(words) && spanText(raw, words[index]) == "global" {
			index++
		}
		if index >= len(words) {
			s.addDiagnostic(line, command.Span, SeverityError, "update requires a key")
			return
		}
		s.checkKey(line, words[index])
		index++
		if index >= len(words) {
			s.addDiagnostic(line, command.Span, SeverityError, "update requires a value")
		}
	case "get", "delete":
		index := 1
		if index < len(words) && spanText(raw, words[index]) == "global" {
			index++
		}
		if index >= len(words) {
			s.addDiagnostic(line, command.Span, SeverityError, keyword+" requires a key")
			return
		}
		s.checkKey(line, words[index])
		if index+1 < len(words) {
			s.addDiagnostic(line, Span{Start: words[index+1].Start, End: words[len(words)-1].End}, SeverityError,
				keyword+" accepts exactly one key")
		}
	case "check":
		if len(words) < 2 {
			s.addDiagnostic(line, command.Span, SeverityError, "check requires an expression")
		}
	case "return":
		if len(words) > 2 {
			s.addDiagnostic(line, Span{Start: words[2].Start, End: words[len(words)-1].End}, SeverityError,
				`return only accepts "all"`)
			return
		}
		if len(words) == 2 {
			argument := spanText(raw, words[1])
			if argument != "all" {
				s.addDiagnostic(line, words[1], SeverityError, `return only accepts "all"`)
			}
		}
	}
}

func (s *Script) checkDialogRef(line int, span Span) {
	if !s.HasScriptID {
		return
	}
	text := spanText(s.Lines[line].Text, span)
	scriptPart := ""
	dialogPart := text
	if separator := strings.IndexByte(text, ':'); separator >= 0 {
		scriptPart = text[:separator]
		dialogPart = text[separator+1:]
	}
	if dialogPart == "" {
		s.addDiagnostic(line, span, SeverityError, "Dialog reference has an empty dialog name: "+text)
		return
	}
	if scriptPart != "" && scriptPart != s.ScriptID {
		return
	}
	if _, exists := s.DialogNames[dialogPart]; !exists {
		s.addDiagnostic(line, span, SeverityError, "Unknown dialog: "+text)
	}
}

func (s *Script) checkKey(line int, span Span) {
	text := spanText(s.Lines[line].Text, span)
	for _, char := range text {
		if !isWordChar(char) {
			s.addDiagnostic(line, span, SeverityError, "Invalid key name: "+text)
			return
		}
	}
	if _, reserved := reservedKeys[text]; reserved {
		s.addDiagnostic(line, span, SeverityError, "Key is reserved: "+text)
	}
}

func isWordChar(char rune) bool {
	return char >= '0' && char <= '9' ||
		char >= 'A' && char <= 'Z' ||
		char >= 'a' && char <= 'z' ||
		char == '_' || char == '-' || char == '.' || char == '+'
}

func wordSpans(text string, span Span) []Span {
	var words []Span
	index := span.Start
	for index < span.End {
		for index < span.End {
			r, size := utf8.DecodeRuneInString(text[index:span.End])
			if !unicode.IsSpace(r) {
				break
			}
			index += size
		}
		if index >= span.End {
			break
		}
		start := index
		for index < span.End {
			r, size := utf8.DecodeRuneInString(text[index:span.End])
			if unicode.IsSpace(r) {
				break
			}
			index += size
		}
		words = append(words, Span{Start: start, End: index})
	}
	return words
}
