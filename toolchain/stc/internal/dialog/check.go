package dialog

import (
	"regexp"
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

var (
	updateIntegerPattern = regexp.MustCompile(`^[+-]?[0-9]+$`)
	updateDecimalPattern = regexp.MustCompile(`^[+-]?(?:(?:[0-9]+\.[0-9]*|\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[0-9]+[eE][+-]?[0-9]+)$`)
)

func classifyUpdateValue(text string) StateKeyType {
	trimmed := strings.TrimSpace(text)
	if len(trimmed) >= 2 {
		first := trimmed[0]
		last := trimmed[len(trimmed)-1]
		if (first == '"' && last == '"') || (first == '\'' && last == '\'') {
			trimmed = trimmed[1 : len(trimmed)-1]
		}
	}
	switch {
	case trimmed == "true" || trimmed == "false":
		return StateKeyBoolean
	case updateIntegerPattern.MatchString(trimmed):
		return StateKeyInteger
	case updateDecimalPattern.MatchString(trimmed):
		return StateKeyDecimal
	default:
		return StateKeyString
	}
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
		global := false
		if index < len(words) && spanText(raw, words[index]) == "global" {
			global = true
			index++
		}
		if index >= len(words) {
			s.addDiagnostic(line, command.Span, SeverityError, "update requires a key")
			return
		}
		keySpan := words[index]
		s.checkKey(line, keySpan)
		valueSpan := trimSpan(raw, Span{Start: keySpan.End, End: command.Span.End})
		if valueSpan.IsEmpty() {
			s.addDiagnostic(line, command.Span, SeverityError, "update requires a value")
			return
		}
		keyName := spanText(raw, keySpan)
		if _, reserved := reservedKeys[keyName]; !reserved {
			s.StateKeys = append(s.StateKeys, StateKey{
				Name:   keyName,
				Global: global,
				Type:   classifyUpdateValue(spanText(raw, valueSpan)),
			})
		}
	case "get", "delete":
		index := 1
		global := false
		if index < len(words) && spanText(raw, words[index]) == "global" {
			global = true
			index++
		}
		if index >= len(words) {
			s.addDiagnostic(line, command.Span, SeverityError, keyword+" requires a key")
			return
		}
		keySpan := words[index]
		s.checkKey(line, keySpan)
		if index+1 < len(words) {
			s.addDiagnostic(line, Span{Start: words[index+1].Start, End: words[len(words)-1].End}, SeverityError,
				keyword+" accepts exactly one key")
			return
		}
		if keyword == "delete" {
			keyName := spanText(raw, keySpan)
			if _, reserved := reservedKeys[keyName]; !reserved {
				s.StateKeys = append(s.StateKeys, StateKey{Name: keyName, Global: global, Type: StateKeyUnknown})
			}
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
	text := spanText(s.Lines[line].Text, span)
	s.DialogRefs = append(s.DialogRefs, DialogRef{Line: line, Span: span, Text: text})
	if !s.HasScriptID {
		return
	}
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
