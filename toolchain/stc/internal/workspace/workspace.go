package workspace

import (
	"encoding/json"
	"errors"
	"fmt"
	"os"
	"path/filepath"
)

type Pack struct {
	Description  json.RawMessage            `json:"description"`
	Resourcepack map[string]json.RawMessage `json:"resourcepack"`
	Datapack     map[string]json.RawMessage `json:"datapack"`
}

type Workspace struct {
	Root string
	Pack Pack
}

type packFile struct {
	Pack Pack `json:"pack"`
}

func Open(dir string) (*Workspace, error) {
	info, err := os.Stat(dir)
	if err != nil {
		return nil, err
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("%s is not a directory", dir)
	}
	content, err := os.ReadFile(filepath.Join(dir, "pack.mcmeta"))
	if err != nil {
		return nil, err
	}
	var file packFile
	if err := json.Unmarshal(content, &file); err != nil {
		return nil, fmt.Errorf("read pack.mcmeta: %w", err)
	}
	if len(file.Pack.Description) == 0 || string(file.Pack.Description) == "null" {
		return nil, errors.New("pack.mcmeta: pack.description is required")
	}
	if len(file.Pack.Resourcepack) == 0 {
		return nil, errors.New("pack.mcmeta: pack.resourcepack is required")
	}
	if len(file.Pack.Datapack) == 0 {
		return nil, errors.New("pack.mcmeta: pack.datapack is required")
	}
	return &Workspace{Root: dir, Pack: file.Pack}, nil
}
