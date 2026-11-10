package command

import (
	"errors"
	"fmt"
	"io/fs"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"unicode/utf8"

	"github.com/spf13/cobra"

	"io.ib67.mods.storyteller/toolchain/internal/dialog"
	"io.ib67.mods.storyteller/toolchain/internal/workspace"
)

func newLintCommand() *cobra.Command {
	return &cobra.Command{
		Use:   "lint [workspace]",
		Short: "Check every dialog script in a workspace",
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			target := "."
			if len(args) > 0 {
				target = args[0]
			}
			current, err := workspace.Open(target)
			if err != nil {
				return err
			}
			dataDir := filepath.Join(current.Root, "data")
			info, err := os.Stat(dataDir)
			if err != nil {
				return err
			}
			if !info.IsDir() {
				return fmt.Errorf("%s is not a directory", dataDir)
			}

			type loadedScript struct {
				path   string
				script *dialog.Script
			}
			type problem struct {
				path     string
				line     int
				column   int
				severity dialog.Severity
				message  string
			}

			var problems []problem
			var loaded []loadedScript
			scripts := make(map[string]*dialog.Script)
			scriptPaths := make(map[string]string)

			namespaces, err := os.ReadDir(dataDir)
			if err != nil {
				return err
			}
			for _, namespace := range namespaces {
				if !namespace.IsDir() {
					continue
				}
				dialogDir := filepath.Join(dataDir, namespace.Name(), "dialog")
				info, err := os.Stat(dialogDir)
				if err != nil {
					if errors.Is(err, fs.ErrNotExist) {
						continue
					}
					return err
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
					content, err := os.ReadFile(path)
					if err != nil {
						return err
					}
					relative, err := filepath.Rel(dialogDir, path)
					if err != nil {
						return err
					}
					scriptID := strings.TrimSuffix(filepath.ToSlash(relative), ".txt")
					script := dialog.Analyze(string(content), scriptID)
					loaded = append(loaded, loadedScript{path: path, script: script})
					if existing, exists := scriptPaths[scriptID]; exists {
						problems = append(problems, problem{
							path:     path,
							severity: dialog.SeverityError,
							message:  fmt.Sprintf("duplicate script ID %q, already defined in %s", scriptID, existing),
						})
					} else {
						scriptPaths[scriptID] = path
						scripts[scriptID] = script
					}
					for _, item := range script.Diagnostics {
						line := script.Lines[item.Line]
						problems = append(problems, problem{
							path:     path,
							line:     item.Line + 1,
							column:   columnAt(line.Text, item.Span.Start),
							severity: item.Severity,
							message:  item.Message,
						})
					}
					return nil
				})
				if err != nil {
					return err
				}
			}

			for _, item := range loaded {
				for _, ref := range item.script.DialogRefs {
					scriptPart, dialogPart, hasSeparator := strings.Cut(ref.Text, ":")
					if !hasSeparator || scriptPart == "" || scriptPart == item.script.ScriptID || dialogPart == "" {
						continue
					}
					targetScript, exists := scripts[scriptPart]
					if !exists {
						problems = append(problems, problem{
							path:     item.path,
							line:     ref.Line + 1,
							column:   columnAt(item.script.Lines[ref.Line].Text, ref.Span.Start),
							severity: dialog.SeverityWarning,
							message:  "Script not found in workspace: " + scriptPart,
						})
						continue
					}
					if _, exists := targetScript.DialogNames[dialogPart]; !exists {
						problems = append(problems, problem{
							path:     item.path,
							line:     ref.Line + 1,
							column:   columnAt(item.script.Lines[ref.Line].Text, ref.Span.Start),
							severity: dialog.SeverityError,
							message:  "Unknown dialog: " + ref.Text,
						})
					}
				}
			}

			sort.SliceStable(problems, func(i, j int) bool {
				if problems[i].path != problems[j].path {
					return problems[i].path < problems[j].path
				}
				if problems[i].line != problems[j].line {
					return problems[i].line < problems[j].line
				}
				return problems[i].column < problems[j].column
			})

			out := cmd.OutOrStdout()
			errorCount := 0
			for _, item := range problems {
				label := "error"
				if item.severity == dialog.SeverityWarning {
					label = "warning"
				} else {
					errorCount++
				}
				if item.line > 0 {
					fmt.Fprintf(out, "%s:%d:%d: %s: %s\n", item.path, item.line, item.column, label, item.message)
				} else {
					fmt.Fprintf(out, "%s: %s: %s\n", item.path, label, item.message)
				}
			}
			if errorCount == 1 {
				return errors.New("lint found 1 error")
			}
			if errorCount > 1 {
				return fmt.Errorf("lint found %d errors", errorCount)
			}
			return nil
		},
	}
}

func columnAt(text string, offset int) int {
	if offset < 0 {
		offset = 0
	}
	if offset > len(text) {
		offset = len(text)
	}
	return utf8.RuneCountInString(text[:offset]) + 1
}
