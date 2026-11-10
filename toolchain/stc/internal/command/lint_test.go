package command

import (
	"strings"
	"testing"
)

func TestLintReportsSyntaxProblem(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                 validPackMetadata,
		"data/demo/dialog/broken.txt": "[goto]\n",
	})
	output, err := runCommand(t, "lint", dir)
	if err == nil {
		t.Fatalf("expected lint to fail, output %q", output)
	}
	if !strings.Contains(output, "broken.txt:1:2: error: goto requires a dialog reference") {
		t.Fatalf("unexpected output %q", output)
	}
}

func TestLintResolvesWorkspaceReferences(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                validPackMetadata,
		"data/demo/dialog/start.txt": "[goto other:branch]\n",
		"data/demo/dialog/other.txt": "--- branch\nHello\n",
	})
	output, err := runCommand(t, "lint", dir)
	if err != nil {
		t.Fatalf("lint failed: %v (%s)", err, output)
	}
	if output != "" {
		t.Fatalf("unexpected output %q", output)
	}
}

func TestLintReportsUnknownDialog(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                validPackMetadata,
		"data/demo/dialog/start.txt": "[goto other:missing]\n",
		"data/demo/dialog/other.txt": "--- branch\nHello\n",
	})
	output, err := runCommand(t, "lint", dir)
	if err == nil {
		t.Fatalf("expected lint to fail, output %q", output)
	}
	if !strings.Contains(output, "error: Unknown dialog: other:missing") {
		t.Fatalf("unexpected output %q", output)
	}
}

func TestLintWarnsAboutExternalScript(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":                validPackMetadata,
		"data/demo/dialog/start.txt": "[goto elsewhere:branch]\n",
	})
	output, err := runCommand(t, "lint", dir)
	if err != nil {
		t.Fatalf("expected warnings only, got %v (%s)", err, output)
	}
	if !strings.Contains(output, "warning: Script not found in workspace: elsewhere") {
		t.Fatalf("unexpected output %q", output)
	}
}

func TestLintReportsDuplicateScriptIDs(t *testing.T) {
	dir := createWorkspace(t, map[string]string{
		"pack.mcmeta":              validPackMetadata,
		"data/one/dialog/same.txt": "Hello\n",
		"data/two/dialog/same.txt": "Hello\n",
	})
	output, err := runCommand(t, "lint", dir)
	if err == nil {
		t.Fatalf("expected lint to fail, output %q", output)
	}
	if !strings.Contains(output, `duplicate script ID "same"`) {
		t.Fatalf("unexpected output %q", output)
	}
}

func TestLintRequiresDataDirectory(t *testing.T) {
	dir := createWorkspace(t, map[string]string{"pack.mcmeta": validPackMetadata})
	if _, err := runCommand(t, "lint", dir); err == nil {
		t.Fatal("expected lint to fail without data directory")
	}
}
