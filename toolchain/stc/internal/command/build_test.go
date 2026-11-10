package command

import (
	"encoding/json"
	"errors"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

type generatedMetadata struct {
	Pack map[string]json.RawMessage `json:"pack"`
}

func TestBuildCreatesArchives(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                 validPackMetadata,
		"assets/demo/lang/en_us.json": "{}\n",
		"data/demo/dialog/start.txt":  "Hello\n",
	})

	output, err := runCommand(t, "build", dir)
	if err != nil {
		t.Fatalf("build failed: %v (%s)", err, output)
	}
	if !strings.Contains(output, filepath.Join(dir, "build", "assets.zip")) {
		t.Fatalf("unexpected output %q", output)
	}

	assets := readArchive(t, filepath.Join(dir, "build", "assets.zip"))
	if got := assets["assets/demo/lang/en_us.json"]; got != "{}\n" {
		t.Fatalf("unexpected asset content %q", got)
	}
	if _, exists := assets["data/demo/dialog/start.txt"]; exists {
		t.Fatal("resource pack contains data files")
	}
	var assetMetadata generatedMetadata
	if err := json.Unmarshal([]byte(assets["pack.mcmeta"]), &assetMetadata); err != nil {
		t.Fatal(err)
	}
	if string(assetMetadata.Pack["min_format"]) != `[97,1]` {
		t.Fatalf("unexpected resource pack min_format %s", assetMetadata.Pack["min_format"])
	}
	if string(assetMetadata.Pack["max_format"]) != `[97,1]` {
		t.Fatalf("unexpected resource pack max_format %s", assetMetadata.Pack["max_format"])
	}
	if string(assetMetadata.Pack["description"]) != `"Example pack"` {
		t.Fatalf("unexpected resource pack description %s", assetMetadata.Pack["description"])
	}
	if _, exists := assetMetadata.Pack["resourcepack"]; exists {
		t.Fatal("resource pack metadata contains the workspace section")
	}

	data := readArchive(t, filepath.Join(dir, "build", "data.zip"))
	if got := data["data/demo/dialog/start.txt"]; got != "Hello\n" {
		t.Fatalf("unexpected data content %q", got)
	}
	if _, exists := data["assets/demo/lang/en_us.json"]; exists {
		t.Fatal("data pack contains asset files")
	}
	var dataMetadata generatedMetadata
	if err := json.Unmarshal([]byte(data["pack.mcmeta"]), &dataMetadata); err != nil {
		t.Fatal(err)
	}
	if string(dataMetadata.Pack["min_format"]) != `[121,0]` {
		t.Fatalf("unexpected data pack min_format %s", dataMetadata.Pack["min_format"])
	}
	if string(dataMetadata.Pack["max_format"]) != `[121,0]` {
		t.Fatalf("unexpected data pack max_format %s", dataMetadata.Pack["max_format"])
	}
	if string(dataMetadata.Pack["description"]) != `"Example pack"` {
		t.Fatalf("unexpected data pack description %s", dataMetadata.Pack["description"])
	}
	if _, exists := dataMetadata.Pack["datapack"]; exists {
		t.Fatal("data pack metadata contains the workspace section")
	}
}

func TestBuildDeploysToMinecraftRoot(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                 validPackMetadata,
		"assets/demo/lang/en_us.json": "{}\n",
		"data/demo/dialog/start.txt":  "Hello\n",
	})
	packName := filepath.Base(dir)
	minecraft := t.TempDir()
	worldDir := filepath.Join(minecraft, "saves", "Test World")
	resourceDir := filepath.Join(minecraft, "resourcepacks", packName)
	dataDir := filepath.Join(worldDir, "datapacks", packName)
	if err := os.MkdirAll(filepath.Join(dataDir, "data"), 0o755); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dataDir, "data", "stale.txt"), []byte("stale"), 0o644); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(filepath.Join(dataDir, "keep.txt"), []byte("keep"), 0o644); err != nil {
		t.Fatal(err)
	}

	output, err := runCommand(t, "build", "--minecraft", minecraft, "--world", "Test World", dir)
	if err != nil {
		t.Fatalf("build failed: %v (%s)", err, output)
	}
	if !strings.Contains(output, resourceDir) || !strings.Contains(output, dataDir) {
		t.Fatalf("unexpected output %q", output)
	}
	if _, err := os.Stat(filepath.Join(dir, "build")); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("deployment created the workspace build directory")
	}

	content, err := os.ReadFile(filepath.Join(resourceDir, "assets", "demo", "lang", "en_us.json"))
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "{}\n" {
		t.Fatalf("unexpected asset content %q", content)
	}
	content, err = os.ReadFile(filepath.Join(dataDir, "data", "demo", "dialog", "start.txt"))
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "Hello\n" {
		t.Fatalf("unexpected data content %q", content)
	}
	if _, err := os.Stat(filepath.Join(dataDir, "data", "stale.txt")); !errors.Is(err, fs.ErrNotExist) {
		t.Fatal("stale data file was not removed")
	}
	content, err = os.ReadFile(filepath.Join(dataDir, "keep.txt"))
	if err != nil {
		t.Fatal(err)
	}
	if string(content) != "keep" {
		t.Fatalf("unrelated file was modified: %q", content)
	}

	encoded, err := os.ReadFile(filepath.Join(resourceDir, "pack.mcmeta"))
	if err != nil {
		t.Fatal(err)
	}
	var resourceMetadata generatedMetadata
	if err := json.Unmarshal(encoded, &resourceMetadata); err != nil {
		t.Fatal(err)
	}
	if string(resourceMetadata.Pack["min_format"]) != `[97,1]` {
		t.Fatalf("unexpected resource pack min_format %s", resourceMetadata.Pack["min_format"])
	}
	encoded, err = os.ReadFile(filepath.Join(dataDir, "pack.mcmeta"))
	if err != nil {
		t.Fatal(err)
	}
	var dataMetadata generatedMetadata
	if err := json.Unmarshal(encoded, &dataMetadata); err != nil {
		t.Fatal(err)
	}
	if string(dataMetadata.Pack["min_format"]) != `[121,0]` {
		t.Fatalf("unexpected data pack min_format %s", dataMetadata.Pack["min_format"])
	}
}

func TestBuildRequiresWorldWithMinecraftRoot(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                 validPackMetadata,
		"assets/demo/lang/en_us.json": "{}\n",
		"data/demo/dialog/start.txt":  "Hello\n",
	})
	if _, err := runCommand(t, "build", "--minecraft", t.TempDir(), dir); err == nil {
		t.Fatal("expected build to fail without --world")
	}
	if _, err := runCommand(t, "build", "--world", "Test World", dir); err == nil {
		t.Fatal("expected build to fail without --minecraft")
	}
}

func TestBuildRequiresPackDirectories(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":          validPackMetadata,
		"assets/demo/file.txt": "content\n",
	})
	if _, err := runCommand(t, "build", dir); err == nil {
		t.Fatal("expected build to fail without data directory")
	}
}
