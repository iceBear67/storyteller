package server

import (
	"net/url"
	"path/filepath"
	"strings"
	"sync"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

const sourceName = "storyteller-dialog"

type document struct {
	text   string
	parsed *dialog.Script
}

type Server struct {
	handler   *protocol.Handler
	mu        sync.Mutex
	documents map[string]*document
}

func New() *Server {
	s := &Server{documents: make(map[string]*document)}
	handler := &protocol.Handler{}
	handler.Initialize = s.initialize
	handler.Initialized = func(*glsp.Context, *protocol.InitializedParams) error { return nil }
	handler.TextDocumentDefinition = s.definition
	handler.TextDocumentPrepareRename = s.prepareRename
	handler.TextDocumentRename = s.rename
	handler.TextDocumentDidOpen = s.didOpen
	handler.TextDocumentDidChange = s.didChange
	handler.TextDocumentDidClose = s.didClose
	handler.TextDocumentSemanticTokensFull = s.semanticTokensFull
	handler.TextDocumentSemanticTokensRange = s.semanticTokensRange
	handler.WorkspaceExecuteCommand = s.executeCommand
	handler.SetTrace = func(*glsp.Context, *protocol.SetTraceParams) error { return nil }
	handler.Shutdown = func(*glsp.Context) error { return nil }
	s.handler = handler
	return s
}

func (s *Server) Handler() *protocol.Handler {
	return s.handler
}

func (s *Server) initialize(_ *glsp.Context, _ *protocol.InitializeParams) (any, error) {
	capabilities := s.handler.CreateServerCapabilities()
	syncKind := protocol.TextDocumentSyncKindFull
	capabilities.TextDocumentSync = &protocol.TextDocumentSyncOptions{
		OpenClose: &protocol.True,
		Change:    &syncKind,
	}
	capabilities.DefinitionProvider = true
	capabilities.RenameProvider = &protocol.RenameOptions{PrepareProvider: &protocol.True}
	capabilities.SemanticTokensProvider = &protocol.SemanticTokensOptions{
		Legend: protocol.SemanticTokensLegend{
			TokenTypes:     dialog.TokenTypeNames(),
			TokenModifiers: []string{},
		},
		Full:  true,
		Range: true,
	}
	capabilities.ExecuteCommandProvider = &protocol.ExecuteCommandOptions{
		Commands: []string{stateSymbolsCommand},
	}
	version := "0.1.0"
	return protocol.InitializeResult{
		Capabilities: capabilities,
		ServerInfo: &protocol.InitializeResultServerInfo{
			Name:    "stc",
			Version: &version,
		},
	}, nil
}

func (s *Server) didOpen(ctx *glsp.Context, params *protocol.DidOpenTextDocumentParams) error {
	s.store(params.TextDocument.URI, params.TextDocument.Text)
	s.publishDiagnostics(ctx, params.TextDocument.URI)
	return nil
}

func (s *Server) didChange(ctx *glsp.Context, params *protocol.DidChangeTextDocumentParams) error {
	uri := params.TextDocument.URI
	s.mu.Lock()
	current, exists := s.documents[uri]
	if !exists {
		s.mu.Unlock()
		return nil
	}
	text := applyChanges(current.text, params.ContentChanges)
	scriptID, hasScriptID := scriptIDFromURI(uri)
	if !hasScriptID {
		scriptID = ""
	}
	s.documents[uri] = &document{text: text, parsed: dialog.Analyze(text, scriptID)}
	s.mu.Unlock()
	s.publishDiagnostics(ctx, uri)
	return nil
}

func (s *Server) didClose(_ *glsp.Context, params *protocol.DidCloseTextDocumentParams) error {
	s.mu.Lock()
	delete(s.documents, params.TextDocument.URI)
	s.mu.Unlock()
	return nil
}

func (s *Server) semanticTokensFull(_ *glsp.Context, params *protocol.SemanticTokensParams) (*protocol.SemanticTokens, error) {
	parsed := s.lookup(params.TextDocument.URI)
	if parsed == nil {
		return &protocol.SemanticTokens{Data: []protocol.UInteger{}}, nil
	}
	return &protocol.SemanticTokens{Data: encodeTokens(parsed, nil)}, nil
}

func (s *Server) semanticTokensRange(_ *glsp.Context, params *protocol.SemanticTokensRangeParams) (any, error) {
	parsed := s.lookup(params.TextDocument.URI)
	if parsed == nil {
		return &protocol.SemanticTokens{Data: []protocol.UInteger{}}, nil
	}
	return &protocol.SemanticTokens{Data: encodeTokens(parsed, &params.Range)}, nil
}

func (s *Server) store(uri, text string) {
	scriptID, hasScriptID := scriptIDFromURI(uri)
	if !hasScriptID {
		scriptID = ""
	}
	parsed := dialog.Analyze(text, scriptID)
	s.mu.Lock()
	s.documents[uri] = &document{text: text, parsed: parsed}
	s.mu.Unlock()
}

func (s *Server) lookup(uri string) *dialog.Script {
	s.mu.Lock()
	defer s.mu.Unlock()
	if current, exists := s.documents[uri]; exists {
		return current.parsed
	}
	return nil
}

func (s *Server) publishDiagnostics(ctx *glsp.Context, uri string) {
	s.mu.Lock()
	current := s.documents[uri]
	s.mu.Unlock()
	if current == nil {
		return
	}
	diagnostics := make([]protocol.Diagnostic, 0, len(current.parsed.Diagnostics))
	for _, item := range current.parsed.Diagnostics {
		severity := protocol.DiagnosticSeverityError
		if item.Severity == dialog.SeverityWarning {
			severity = protocol.DiagnosticSeverityWarning
		}
		line := current.parsed.Lines[item.Line].Text
		source := sourceName
		diagnostics = append(diagnostics, protocol.Diagnostic{
			Range:    diagnosticRange(item.Line, line, item.Span),
			Severity: &severity,
			Source:   &source,
			Message:  item.Message,
		})
	}
	ctx.Notify(protocol.ServerTextDocumentPublishDiagnostics, protocol.PublishDiagnosticsParams{
		URI:         uri,
		Diagnostics: diagnostics,
	})
}

func applyChanges(text string, changes []any) string {
	for _, change := range changes {
		switch value := change.(type) {
		case protocol.TextDocumentContentChangeEventWhole:
			text = value.Text
		case protocol.TextDocumentContentChangeEvent:
			if value.Range == nil {
				text = value.Text
				continue
			}
			start, end := value.Range.IndexesIn(text)
			text = text[:start] + value.Text + text[end:]
		}
	}
	return text
}

func scriptIDFromURI(uri string) (string, bool) {
	parsed, err := url.Parse(uri)
	if err != nil || parsed.Scheme != "file" {
		return "", false
	}
	path := parsed.Path
	if path == "" {
		path = parsed.Opaque
	}
	segments := strings.Split(filepath.ToSlash(path), "/")
	id := ""
	found := false
	for i := 0; i+3 < len(segments); i++ {
		if segments[i] != "data" || segments[i+2] != "dialog" {
			continue
		}
		remainder := strings.Join(segments[i+3:], "/")
		if !strings.HasSuffix(remainder, ".txt") || len(remainder) == len(".txt") {
			continue
		}
		id = strings.TrimSuffix(remainder, ".txt")
		found = true
	}
	return id, found
}
