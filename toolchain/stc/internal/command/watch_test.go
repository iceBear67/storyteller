package command

import (
	"path/filepath"
	"testing"
)

func TestWatchRelevant(t *testing.T) {
	root := filepath.Join("tmp", "workspace")
	cases := []struct {
		name  string
		match bool
	}{
		{filepath.Join(root, "pack.mcmeta"), true},
		{filepath.Join(root, "assets"), true},
		{filepath.Join(root, "assets", "demo", "file.txt"), true},
		{filepath.Join(root, "data", "demo", "dialog", "start.txt"), true},
		{filepath.Join(root, "build", "assets.zip"), false},
		{filepath.Join(root, "other.txt"), false},
	}
	for _, test := range cases {
		if got := watchRelevant(test.name, root); got != test.match {
			t.Fatalf("watchRelevant(%q) = %v, want %v", test.name, got, test.match)
		}
	}
}
