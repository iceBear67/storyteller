package command

import (
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/fsnotify/fsnotify"
)

func watchWorkspace(root string, rebuild func() error, errorOut io.Writer) error {
	watcher, err := fsnotify.NewWatcher()
	if err != nil {
		return err
	}
	defer watcher.Close()

	addWatches := func() error {
		directories := []string{root}
		for _, name := range []string{"assets", "data"} {
			path := filepath.Join(root, name)
			info, err := os.Stat(path)
			if err != nil {
				if errors.Is(err, fs.ErrNotExist) {
					continue
				}
				return err
			}
			if info.IsDir() {
				directories = append(directories, path)
			}
		}
		for _, directory := range directories {
			if err := filepath.WalkDir(directory, func(path string, entry fs.DirEntry, walkErr error) error {
				if walkErr != nil {
					return walkErr
				}
				if !entry.IsDir() {
					return nil
				}
				return watcher.Add(path)
			}); err != nil {
				return err
			}
		}
		return nil
	}
	if err := addWatches(); err != nil {
		return err
	}

	timer := time.NewTimer(time.Hour)
	if !timer.Stop() {
		<-timer.C
	}
	for {
		select {
		case event, ok := <-watcher.Events:
			if !ok {
				return nil
			}
			if !watchRelevant(event.Name, root) {
				continue
			}
			if err := addWatches(); err != nil {
				fmt.Fprintln(errorOut, err)
			}
			if !timer.Stop() {
				select {
				case <-timer.C:
				default:
				}
			}
			timer.Reset(200 * time.Millisecond)
		case err, ok := <-watcher.Errors:
			if !ok {
				return nil
			}
			fmt.Fprintln(errorOut, err)
		case <-timer.C:
			if err := rebuild(); err != nil {
				fmt.Fprintln(errorOut, err)
			}
		}
	}
}

func watchRelevant(name, root string) bool {
	cleaned := filepath.Clean(name)
	if cleaned == filepath.Join(root, "pack.mcmeta") {
		return true
	}
	for _, directory := range []string{"assets", "data"} {
		base := filepath.Join(root, directory)
		if cleaned == base || strings.HasPrefix(cleaned, base+string(filepath.Separator)) {
			return true
		}
	}
	return false
}
