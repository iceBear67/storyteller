package workspace

import (
	"os"
	"path/filepath"
	"testing"
)

const testPackMetadata = `{"pack":{"description":"Example","resourcepack":{"min_format":[97,1],"max_format":[97,1]},"datapack":{"min_format":[121,0],"max_format":[121,0]}}}`

func writePack(t *testing.T, content string) string {
	t.Helper()
	dir := t.TempDir()
	if err := os.WriteFile(filepath.Join(dir, "pack.mcmeta"), []byte(content), 0o644); err != nil {
		t.Fatal(err)
	}
	return dir
}

func TestOpenReadsPackMetadata(t *testing.T) {
	dir := writePack(t, testPackMetadata)
	current, err := Open(dir)
	if err != nil {
		t.Fatal(err)
	}
	if current.Root != dir {
		t.Fatalf("unexpected root %q", current.Root)
	}
	if string(current.Pack.Description) != `"Example"` {
		t.Fatalf("unexpected description %s", current.Pack.Description)
	}
	if string(current.Pack.Resourcepack["min_format"]) != `[97,1]` {
		t.Fatalf("unexpected resource pack metadata %v", current.Pack.Resourcepack)
	}
	if string(current.Pack.Datapack["max_format"]) != `[121,0]` {
		t.Fatalf("unexpected data pack metadata %v", current.Pack.Datapack)
	}
}

func TestOpenRejectsInvalidPackMetadata(t *testing.T) {
	cases := []string{
		`{"pack":{"datapack":{"min_format":[121,0]},"description":"Example"}}`,
		`{"pack":{"resourcepack":{"min_format":[97,1]},"description":"Example"}}`,
		`{"pack":{"resourcepack":{},"datapack":{"min_format":[121,0]},"description":"Example"}}`,
		`{"pack":{"resourcepack":{"min_format":[97,1]},"datapack":{},"description":"Example"}}`,
		`{"pack":{"resourcepack":[],"datapack":{"min_format":[121,0]},"description":"Example"}}`,
		`{"pack":{"resourcepack":{"min_format":[97,1]},"datapack":{"min_format":[121,0]}}}`,
		`{"pack":{"resourcepack":{"min_format":[97,1]},"datapack":{"min_format":[121,0]},"description":null}}`,
	}
	for _, content := range cases {
		if _, err := Open(writePack(t, content)); err == nil {
			t.Fatalf("expected error for %s", content)
		}
	}
}
