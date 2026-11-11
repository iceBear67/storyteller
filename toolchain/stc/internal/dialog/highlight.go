package dialog

func highlightLine(line *Line) []Token {
	var tokens []Token
	if line.Condition != nil {
		tokens = append(tokens, Token{Span: line.Condition.Open, Type: TokenOperator})
		if line.Condition.Closed {
			tokens = append(tokens, Token{Span: line.Condition.Close, Type: TokenOperator})
		}
	}
	switch line.Kind {
	case KindComment:
		if line.Comment != nil {
			tokens = append(tokens, Token{Span: *line.Comment, Type: TokenComment})
		}
	case KindDivider:
		if line.Divider != nil {
			tokens = append(tokens, Token{Span: line.Divider.Dashes, Type: TokenOperator})
			if !line.Divider.Name.IsEmpty() {
				tokens = append(tokens, Token{Span: line.Divider.Name, Type: TokenTypeName})
			}
		}
	case KindCommand:
		if line.Command != nil {
			tokens = append(tokens, highlightCommandAction(line.Text, line.Command)...)
		}
	case KindDialogue:
		if line.Dialogue != nil {
			tokens = append(tokens, highlightDialogue(line.Text, line.Dialogue)...)
		}
	}
	if line.Kind != KindComment && line.Comment != nil {
		tokens = append(tokens, Token{Span: *line.Comment, Type: TokenComment})
	}
	return tokens
}

func highlightCommandAction(raw string, action *CommandAction) []Token {
	tokens := []Token{{Span: action.Open, Type: TokenOperator}}
	tokens = append(tokens, highlightCommands(raw, action.Commands)...)
	if action.Closed {
		tokens = append(tokens, Token{Span: action.Close, Type: TokenOperator})
	}
	if action.Selector != nil {
		tokens = append(tokens, highlightSelector(*action.Selector)...)
	}
	return tokens
}

func highlightDialogue(raw string, dialogue *DialogueAction) []Token {
	var tokens []Token
	if dialogue.Speaker != nil {
		tokens = append(tokens, Token{Span: *dialogue.Speaker, Type: TokenProperty})
	}
	if dialogue.Colon != nil {
		tokens = append(tokens, Token{Span: *dialogue.Colon, Type: TokenOperator})
	}
	for _, segment := range dialogue.Text {
		switch segment.Kind {
		case SegmentLiteral:
			tokens = append(tokens, Token{Span: segment.Span, Type: TokenString})
		case SegmentColor:
			tokens = append(tokens, Token{Span: segment.Span, Type: TokenOperator})
		case SegmentInlineCommand:
			tokens = append(tokens, Token{
				Span: Span{Start: segment.Span.Start, End: segment.Span.Start + 1},
				Type: TokenOperator,
			})
			tokens = append(tokens, highlightCommands(raw, segment.Commands)...)
			tokens = append(tokens, Token{
				Span: Span{Start: segment.Span.End - 1, End: segment.Span.End},
				Type: TokenOperator,
			})
		}
	}
	if dialogue.Audience != nil {
		tokens = append(tokens, highlightSelector(*dialogue.Audience)...)
	}
	if dialogue.Click != nil {
		tokens = append(tokens, Token{Span: dialogue.Click.Open, Type: TokenOperator})
		tokens = append(tokens, highlightCommands(raw, dialogue.Click.Commands)...)
		tokens = append(tokens, Token{Span: dialogue.Click.Close, Type: TokenOperator})
		if dialogue.Click.Selector != nil {
			tokens = append(tokens, Token{
				Span: Span{Start: dialogue.Click.Selector.Start, End: dialogue.Click.Selector.Start + 1},
				Type: TokenOperator,
			})
			name := Span{Start: dialogue.Click.Selector.Start + 1, End: dialogue.Click.Selector.End}
			if !name.IsEmpty() {
				tokens = append(tokens, Token{Span: name, Type: TokenParameter})
			}
		}
	}
	return tokens
}

func highlightSelector(selector Selector) []Token {
	tokens := []Token{{Span: Span{Start: selector.Span.Start, End: selector.Span.Start + 1}, Type: TokenOperator}}
	name := Span{Start: selector.Span.Start + 1, End: selector.Span.End}
	if !name.IsEmpty() {
		tokens = append(tokens, Token{Span: name, Type: TokenParameter})
	}
	return tokens
}

func highlightCommands(raw string, commands []Command) []Token {
	var tokens []Token
	for _, command := range commands {
		tokens = append(tokens, highlightCommand(raw, command)...)
	}
	return tokens
}

func highlightCommand(raw string, command Command) []Token {
	firstWord := spanText(raw, command.FirstWord)
	keyword := flowWord(firstWord)
	if _, ok := scriptCommands[keyword]; ok {
		tokens := []Token{{Span: command.FirstWord, Type: TokenKeyword}}
		return append(tokens, highlightFlowArguments(raw, command, keyword)...)
	}
	if firstWord == "repeatable" {
		tokens := []Token{{Span: command.FirstWord, Type: TokenModifier}}
		rest := trimSpan(raw, Span{Start: command.FirstWord.End, End: command.Span.End})
		if !rest.IsEmpty() {
			tokens = append(tokens, Token{Span: rest, Type: TokenString})
		}
		return tokens
	}
	tokens := []Token{{Span: command.FirstWord, Type: TokenFunction}}
	rest := trimSpan(raw, Span{Start: command.FirstWord.End, End: command.Span.End})
	if !rest.IsEmpty() {
		tokens = append(tokens, Token{Span: rest, Type: TokenString})
	}
	return tokens
}

func highlightFlowArguments(raw string, command Command, keyword string) []Token {
	words := wordSpans(raw, command.Span)
	var tokens []Token
	switch keyword {
	case "expect":
		for _, word := range words[1:] {
			tokens = append(tokens, Token{Span: word, Type: TokenTypeName})
		}
	case "goto":
		if len(words) > 1 {
			tokens = append(tokens, Token{Span: words[1], Type: TokenTypeName})
		}
		if len(words) > 2 {
			for _, word := range words[2:] {
				tokens = append(tokens, Token{Span: word, Type: TokenString})
			}
		}
	case "check":
		if len(words) > 1 {
			expression := trimSpan(raw, Span{Start: words[1].Start, End: command.Span.End})
			tokens = append(tokens, Token{Span: expression, Type: TokenVariable})
		}
	case "update":
		index := 1
		if index < len(words) && spanText(raw, words[index]) == "global" {
			tokens = append(tokens, Token{Span: words[index], Type: TokenKeyword})
			index++
		}
		if index < len(words) {
			tokens = append(tokens, Token{Span: words[index], Type: TokenProperty})
			index++
		}
		if index < len(words) {
			value := Span{Start: words[index].Start, End: command.Span.End}
			tokens = append(tokens, Token{Span: value, Type: TokenString})
		}
	case "get", "delete":
		index := 1
		if index < len(words) && spanText(raw, words[index]) == "global" {
			tokens = append(tokens, Token{Span: words[index], Type: TokenKeyword})
			index++
		}
		if index < len(words) {
			tokens = append(tokens, Token{Span: words[index], Type: TokenProperty})
			index++
		}
		for _, word := range words[index:] {
			tokens = append(tokens, Token{Span: word, Type: TokenString})
		}
	case "return":
		if len(words) > 1 {
			span := words[1]
			value := spanText(raw, span)
			if value == "all" {
				tokens = append(tokens, Token{Span: span, Type: TokenKeyword})
			} else {
				tokens = append(tokens, Token{Span: span, Type: TokenString})
			}
		}
		if len(words) > 2 {
			for _, word := range words[2:] {
				tokens = append(tokens, Token{Span: word, Type: TokenString})
			}
		}
	case "wait":
		if len(words) > 1 {
			tokens = append(tokens, Token{Span: words[1], Type: TokenNumber})
		}
	}
	return tokens
}
