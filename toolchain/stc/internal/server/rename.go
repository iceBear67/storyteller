package server

import (
	"fmt"
	"io/fs"
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

type renameTarget struct {
	scriptID       string
	dialogName     string
	definingURI    protocol.DocumentUri
	definingScript *dialog.Script
	symbolLine     int
	symbolSpan     dialog.Span
}

func (s *Server) prepareRename(_ *glsp.Context, params *protocol.PrepareRenameParams) (any, error) {
	target, ok := s.renameTarget(params.TextDocument.URI, params.Position)
	if !ok {
		return nil, nil
	}
	script := s.lookup(params.TextDocument.URI)
	if script == nil || target.symbolLine < 0 || target.symbolLine >= len(script.Lines) {
		return nil, nil
	}
	text := script.Lines[target.symbolLine].Text
	return protocol.RangeWithPlaceholder{
		Range: protocol.Range{
			Start: protocol.Position{
				Line:      protocol.UInteger(target.symbolLine),
				Character: protocol.UInteger(charColumn(text, target.symbolSpan.Start)),
			},
			End: protocol.Position{
				Line:      protocol.UInteger(target.symbolLine),
				Character: protocol.UInteger(charColumn(text, target.symbolSpan.End)),
			},
		},
		Placeholder: target.dialogName,
	}, nil
}

func (s *Server) rename(_ *glsp.Context, params *protocol.RenameParams) (*protocol.WorkspaceEdit, error) {
	target, ok := s.renameTarget(params.TextDocument.URI, params.Position)
	if !ok {
		return nil, fmt.Errorf("cannot rename this symbol")
	}
	newName := params.NewName
	if !dialog.ValidDialogName(newName) {
		return nil, fmt.Errorf("invalid dialog name: %s", newName)
	}
	if newName == target.dialogName {
		return &protocol.WorkspaceEdit{}, nil
	}
	lineIndex := target.definingScript.DialogNames[target.dialogName]
	if lineIndex < 0 || lineIndex >= len(target.definingScript.Lines) {
		return nil, fmt.Errorf("cannot rename the default dialog")
	}
	if _, exists := target.definingScript.DialogNames[newName]; exists {
		return nil, fmt.Errorf("dialog already exists: %s", newName)
	}
	divider := target.definingScript.Lines[lineIndex].Divider
	if divider == nil {
		return nil, fmt.Errorf("cannot rename this dialog")
	}
	edits := make(map[protocol.DocumentUri][]protocol.TextEdit)
	edits[target.definingURI] = append(edits[target.definingURI],
		textEdit(lineIndex, target.definingScript.Lines[lineIndex].Text, divider.Name, newName))

	dataDir, ok := dataDirFromURI(params.TextDocument.URI)
	if !ok {
		return nil, fmt.Errorf("cannot find the data directory")
	}
	namespaces, err := os.ReadDir(dataDir)
	if err != nil {
		return nil, err
	}
	for _, namespace := range namespaces {
		if !namespace.IsDir() {
			continue
		}
		dialogDir := filepath.Join(dataDir, namespace.Name(), "dialog")
		if err := filepath.WalkDir(dialogDir, func(path string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".txt") {
				return nil
			}
			uri := protocol.DocumentUri(fileURI(path))
			script := s.lookup(uri)
			if sameFile(params.TextDocument.URI, path) {
				uri = protocol.DocumentUri(params.TextDocument.URI)
				script = s.lookup(params.TextDocument.URI)
			}
			if script == nil {
				content, err := os.ReadFile(path)
				if err != nil {
					return err
				}
				script = dialog.Analyze(string(content), scriptIDForPath(dialogDir, path))
			}
			for _, ref := range script.DialogRefs {
				scriptPart, dialogPart, hasSeparator := strings.Cut(ref.Text, ":")
				if !hasSeparator {
					scriptPart = ""
					dialogPart = ref.Text
				}
				if dialogPart != target.dialogName {
					continue
				}
				if hasSeparator && scriptPart != "" {
					if scriptPart != target.scriptID {
						continue
					}
				} else if script.ScriptID != target.scriptID {
					continue
				}
				start := ref.Span.Start
				if hasSeparator {
					start += len(scriptPart) + 1
				}
				line := script.Lines[ref.Line].Text
				edits[uri] = append(edits[uri], textEdit(ref.Line, line, dialog.Span{Start: start, End: ref.Span.End}, newName))
			}
			return nil
		}); err != nil {
			return nil, err
		}
	}
	return &protocol.WorkspaceEdit{Changes: edits}, nil
}

func textEdit(line int, text string, span dialog.Span, newText string) protocol.TextEdit {
	return protocol.TextEdit{
		Range: protocol.Range{
			Start: protocol.Position{
				Line:      protocol.UInteger(line),
				Character: protocol.UInteger(charColumn(text, span.Start)),
			},
			End: protocol.Position{
				Line:      protocol.UInteger(line),
				Character: protocol.UInteger(charColumn(text, span.End)),
			},
		},
		NewText: newText,
	}
}

func (s *Server) renameTarget(uri string, position protocol.Position) (*renameTarget, bool) {
	script := s.lookup(uri)
	if script == nil {
		return nil, false
	}
	lineIndex := int(position.Line)
	if lineIndex < 0 || lineIndex >= len(script.Lines) {
		return nil, false
	}
	line := script.Lines[lineIndex]
	offset := byteOffsetAt(line.Text, int(position.Character))
	if line.Divider != nil && offset >= line.Divider.Name.Start && offset <= line.Divider.Name.End {
		index, exists := script.DialogNames[line.Divider.NameText]
		if !exists || index != lineIndex {
			return nil, false
		}
		return &renameTarget{
			scriptID:       script.ScriptID,
			dialogName:     line.Divider.NameText,
			definingURI:    protocol.DocumentUri(uri),
			definingScript: script,
			symbolLine:     lineIndex,
			symbolSpan:     line.Divider.Name,
		}, true
	}
	var ref *dialog.DialogRef
	for i := range script.DialogRefs {
		candidate := &script.DialogRefs[i]
		if candidate.Line != lineIndex {
			continue
		}
		if offset < candidate.Span.Start || offset > candidate.Span.End {
			continue
		}
		ref = candidate
		break
	}
	if ref == nil {
		return nil, false
	}
	scriptPart, dialogPart, hasSeparator := strings.Cut(ref.Text, ":")
	if !hasSeparator {
		scriptPart = ""
		dialogPart = ref.Text
	}
	if dialogPart == "" {
		return nil, false
	}
	symbolStart := ref.Span.Start
	if hasSeparator {
		symbolStart += len(scriptPart) + 1
	}
	if offset < symbolStart {
		return nil, false
	}
	if !hasSeparator || scriptPart == "" || scriptPart == script.ScriptID {
		index, exists := script.DialogNames[dialogPart]
		if !exists || index < 0 {
			return nil, false
		}
		return &renameTarget{
			scriptID:       script.ScriptID,
			dialogName:     dialogPart,
			definingURI:    protocol.DocumentUri(uri),
			definingScript: script,
			symbolLine:     lineIndex,
			symbolSpan:     dialog.Span{Start: symbolStart, End: ref.Span.End},
		}, true
	}
	targetURI, targetScript, ok := s.findScript(uri, scriptPart)
	if !ok {
		return nil, false
	}
	index, exists := targetScript.DialogNames[dialogPart]
	if !exists || index < 0 {
		return nil, false
	}
	return &renameTarget{
		scriptID:       scriptPart,
		dialogName:     dialogPart,
		definingURI:    targetURI,
		definingScript: targetScript,
		symbolLine:     lineIndex,
		symbolSpan:     dialog.Span{Start: symbolStart, End: ref.Span.End},
	}, true
}

func (s *Server) findScript(uri, scriptID string) (protocol.DocumentUri, *dialog.Script, bool) {
	dataDir, ok := dataDirFromURI(uri)
	if !ok {
		return "", nil, false
	}
	namespaces, err := os.ReadDir(dataDir)
	if err != nil {
		return "", nil, false
	}
	var foundURI protocol.DocumentUri
	var foundScript *dialog.Script
	count := 0
	for _, namespace := range namespaces {
		if !namespace.IsDir() {
			continue
		}
		path, ok := scriptPath(filepath.Join(dataDir, namespace.Name(), "dialog"), scriptID)
		if !ok {
			continue
		}
		uriValue := protocol.DocumentUri(fileURI(path))
		script := s.lookup(uriValue)
		if script == nil {
			content, err := os.ReadFile(path)
			if err != nil {
				continue
			}
			script = dialog.Analyze(string(content), scriptID)
		}
		if count == 0 {
			foundURI = uriValue
			foundScript = script
		}
		count++
	}
	if count != 1 {
		return "", nil, false
	}
	return foundURI, foundScript, true
}

func sameFile(uri, path string) bool {
	parsed, err := url.Parse(uri)
	if err != nil || parsed.Scheme != "file" {
		return false
	}
	return filepath.Clean(parsed.Path) == filepath.Clean(path)
}

func scriptIDForPath(dialogDir, path string) string {
	relative, err := filepath.Rel(dialogDir, path)
	if err != nil {
		return ""
	}
	return strings.TrimSuffix(filepath.ToSlash(relative), ".txt")
}
