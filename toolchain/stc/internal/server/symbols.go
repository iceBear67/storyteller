package server

import (
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"

	"github.com/tliron/glsp"
	protocol "github.com/tliron/glsp/protocol_3_16"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
)

const stateSymbolsCommand = "storyteller.stateSymbols"

type stateSymbol struct {
	Name string `json:"name"`
	Type string `json:"type"`
}

type stateSymbolsResult struct {
	Player []stateSymbol `json:"player"`
	Global []stateSymbol `json:"global"`
}

func (s *Server) executeCommand(_ *glsp.Context, params *protocol.ExecuteCommandParams) (any, error) {
	if params.Command != stateSymbolsCommand {
		return nil, fmt.Errorf("unknown command: %s", params.Command)
	}
	if len(params.Arguments) == 0 {
		return nil, fmt.Errorf("missing document URI")
	}
	uri, ok := params.Arguments[0].(string)
	if !ok {
		return nil, fmt.Errorf("invalid document URI")
	}
	return s.collectStateSymbols(uri)
}

func (s *Server) collectStateSymbols(uri string) (stateSymbolsResult, error) {
	dataDir, ok := dataDirFromURI(uri)
	if !ok {
		return stateSymbolsResult{}, fmt.Errorf("document is not inside a dialog directory")
	}
	namespaces, err := os.ReadDir(dataDir)
	if err != nil {
		return stateSymbolsResult{}, err
	}
	player := make(map[string]map[string]struct{})
	global := make(map[string]map[string]struct{})
	for _, namespace := range namespaces {
		if !namespace.IsDir() {
			continue
		}
		dialogDir := filepath.Join(dataDir, namespace.Name(), "dialog")
		info, err := os.Stat(dialogDir)
		if err != nil {
			if os.IsNotExist(err) {
				continue
			}
			return stateSymbolsResult{}, err
		}
		if !info.IsDir() {
			continue
		}
		err = filepath.WalkDir(dialogDir, func(path string, entry fs.DirEntry, walkErr error) error {
			if walkErr != nil {
				return walkErr
			}
			if entry.IsDir() || !strings.HasSuffix(entry.Name(), ".txt") {
				return nil
			}
			script := s.lookup(fileURI(path))
			if sameFile(uri, path) {
				script = s.lookup(uri)
			}
			if script == nil {
				content, err := os.ReadFile(path)
				if err != nil {
					return err
				}
				script = dialog.Analyze(string(content), scriptIDForPath(dialogDir, path))
			}
			for _, key := range script.StateKeys {
				target := player
				if key.Global {
					target = global
				}
				types := target[key.Name]
				if types == nil {
					types = make(map[string]struct{})
					target[key.Name] = types
				}
				types[key.Type.TypeName()] = struct{}{}
			}
			return nil
		})
		if err != nil {
			return stateSymbolsResult{}, err
		}
	}
	return stateSymbolsResult{
		Player: mergeStateSymbols(player),
		Global: mergeStateSymbols(global),
	}, nil
}

func mergeStateSymbols(types map[string]map[string]struct{}) []stateSymbol {
	names := make([]string, 0, len(types))
	for name := range types {
		names = append(names, name)
	}
	sort.Strings(names)
	result := make([]stateSymbol, 0, len(names))
	for _, name := range names {
		known := make(map[string]struct{})
		for typeName := range types[name] {
			if typeName != "any" {
				known[typeName] = struct{}{}
			}
		}
		typeName := "any"
		if len(known) == 1 {
			for value := range known {
				typeName = value
			}
		}
		result = append(result, stateSymbol{Name: name, Type: typeName})
	}
	return result
}
