package server

import (
	"unicode/utf8"

	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

type positionedToken struct {
	line      int
	start     int
	length    int
	tokenType dialog.TokenType
}

func encodeTokens(script *dialog.Script, filter *protocol.Range) []protocol.UInteger {
	data := make([]protocol.UInteger, 0, len(script.Tokens)*5)
	previousLine := 0
	previousStart := 0
	first := true
	for _, token := range positionedTokens(script, filter) {
		deltaLine := token.line - previousLine
		deltaStart := token.start
		if !first && deltaLine == 0 {
			deltaStart = token.start - previousStart
		}
		data = append(data,
			protocol.UInteger(deltaLine),
			protocol.UInteger(deltaStart),
			protocol.UInteger(token.length),
			protocol.UInteger(token.tokenType),
			0,
		)
		previousLine = token.line
		previousStart = token.start
		first = false
	}
	return data
}

func positionedTokens(script *dialog.Script, filter *protocol.Range) []positionedToken {
	tokens := make([]positionedToken, 0, len(script.Lines)*4)
	for lineIndex := range script.Lines {
		line := &script.Lines[lineIndex]
		for _, token := range script.Tokens[lineIndex] {
			start := charColumn(line.Text, token.Span.Start)
			end := charColumn(line.Text, token.Span.End)
			if end <= start {
				continue
			}
			if filter != nil && !withinRange(lineIndex, start, end, filter) {
				continue
			}
			tokens = append(tokens, positionedToken{
				line:      lineIndex,
				start:     start,
				length:    end - start,
				tokenType: token.Type,
			})
		}
	}
	return tokens
}

func withinRange(line, start, end int, filter *protocol.Range) bool {
	rangeStart := int(filter.Start.Line)
	rangeEnd := int(filter.End.Line)
	if line < rangeStart || line > rangeEnd {
		return false
	}
	if line == rangeStart && end <= int(filter.Start.Character) {
		return false
	}
	if line == rangeEnd && start >= int(filter.End.Character) {
		return false
	}
	return true
}

func charColumn(line string, byteOffset int) int {
	if byteOffset < 0 {
		byteOffset = 0
	}
	if byteOffset > len(line) {
		byteOffset = len(line)
	}
	column := 0
	for index, char := range line {
		if index >= byteOffset {
			break
		}
		if char > 0xFFFF {
			column += 2
		} else {
			column++
		}
	}
	return column
}

func diagnosticRange(lineNumber int, line string, span dialog.Span) protocol.Range {
	start := span.Start
	end := span.End
	if start < 0 {
		start = 0
	}
	if end > len(line) {
		end = len(line)
	}
	if start > end {
		start = end
	}
	if start == end {
		if end < len(line) {
			_, size := utf8.DecodeRuneInString(line[end:])
			end += size
		} else if start > 0 {
			_, size := utf8.DecodeLastRuneInString(line[:start])
			start -= size
		}
	}
	return protocol.Range{
		Start: protocol.Position{Line: protocol.UInteger(lineNumber), Character: protocol.UInteger(charColumn(line, start))},
		End:   protocol.Position{Line: protocol.UInteger(lineNumber), Character: protocol.UInteger(charColumn(line, end))},
	}
}
