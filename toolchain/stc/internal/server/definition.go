package server

import (
	"net/url"
	"os"
	"path/filepath"
	"strings"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

func (s *Server) definition(_ *glsp.Context, params *protocol.DefinitionParams) (any, error) {
	script := s.lookup(params.TextDocument.URI)
	if script == nil {
		return nil, nil
	}
	lineIndex := int(params.Position.Line)
	if lineIndex < 0 || lineIndex >= len(script.Lines) {
		return nil, nil
	}
	line := script.Lines[lineIndex]
	offset := byteOffsetAt(line.Text, int(params.Position.Character))
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
		return nil, nil
	}
	scriptPart, dialogPart, found := strings.Cut(ref.Text, ":")
	if !found {
		scriptPart = ""
		dialogPart = ref.Text
	}
	if dialogPart == "" {
		return nil, nil
	}
	if scriptPart == "" || scriptPart == script.ScriptID {
		location, ok := dialogLocation(params.TextDocument.URI, script, dialogPart)
		if !ok {
			return nil, nil
		}
		return []protocol.Location{location}, nil
	}
	dataDir, ok := dataDirFromURI(params.TextDocument.URI)
	if !ok {
		return nil, nil
	}
	namespaces, err := os.ReadDir(dataDir)
	if err != nil {
		return nil, nil
	}
	locations := make([]protocol.Location, 0, 1)
	for _, namespace := range namespaces {
		if !namespace.IsDir() {
			continue
		}
		targetPath, ok := scriptPath(filepath.Join(dataDir, namespace.Name(), "dialog"), scriptPart)
		if !ok {
			continue
		}
		targetURI := fileURI(targetPath)
		target := s.lookup(targetURI)
		if target == nil {
			content, err := os.ReadFile(targetPath)
			if err != nil {
				continue
			}
			target = dialog.Analyze(string(content), scriptPart)
		}
		location, ok := dialogLocation(targetURI, target, dialogPart)
		if ok {
			locations = append(locations, location)
		}
	}
	if len(locations) == 0 {
		return nil, nil
	}
	return locations, nil
}

func dialogLocation(uri string, script *dialog.Script, name string) (protocol.Location, bool) {
	lineIndex, exists := script.DialogNames[name]
	if !exists || lineIndex >= len(script.Lines) {
		return protocol.Location{}, false
	}
	if lineIndex < 0 {
		return protocol.Location{
			URI: uri,
			Range: protocol.Range{
				Start: protocol.Position{Line: 0, Character: 0},
				End:   protocol.Position{Line: 0, Character: 0},
			},
		}, true
	}
	line := script.Lines[lineIndex]
	if line.Divider == nil {
		return protocol.Location{}, false
	}
	return protocol.Location{
		URI: uri,
		Range: protocol.Range{
			Start: protocol.Position{
				Line:      protocol.UInteger(lineIndex),
				Character: protocol.UInteger(charColumn(line.Text, line.Divider.Name.Start)),
			},
			End: protocol.Position{
				Line:      protocol.UInteger(lineIndex),
				Character: protocol.UInteger(charColumn(line.Text, line.Divider.Name.End)),
			},
		},
	}, true
}

func byteOffsetAt(text string, character int) int {
	column := 0
	for index, char := range text {
		if column >= character {
			return index
		}
		if char > 0xFFFF {
			column += 2
		} else {
			column++
		}
		if column > character {
			return index
		}
	}
	return len(text)
}

func dataDirFromURI(uri string) (string, bool) {
	parsed, err := url.Parse(uri)
	if err != nil || parsed.Scheme != "file" {
		return "", false
	}
	path := parsed.Path
	if path == "" {
		path = parsed.Opaque
	}
	segments := strings.Split(filepath.ToSlash(path), "/")
	dataDir := ""
	found := false
	for i := 0; i+3 < len(segments); i++ {
		if segments[i] != "data" || segments[i+2] != "dialog" {
			continue
		}
		dataDir = filepath.FromSlash(strings.Join(segments[:i+1], "/"))
		found = true
	}
	return dataDir, found
}

func scriptPath(dialogDir, scriptID string) (string, bool) {
	relative := filepath.Clean(filepath.FromSlash(scriptID) + ".txt")
	if filepath.IsAbs(relative) || relative == ".." || strings.HasPrefix(relative, ".."+string(filepath.Separator)) {
		return "", false
	}
	path := filepath.Join(dialogDir, relative)
	info, err := os.Stat(path)
	if err != nil || !info.Mode().IsRegular() {
		return "", false
	}
	return path, true
}

func fileURI(path string) string {
	normalized := filepath.ToSlash(path)
	if !strings.HasPrefix(normalized, "/") {
		normalized = "/" + normalized
	}
	return (&url.URL{Scheme: "file", Path: normalized}).String()
}
