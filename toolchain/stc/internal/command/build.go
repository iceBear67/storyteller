package command

import (
	"archive/zip"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"io/fs"
	"os"
	"path/filepath"
	"time"

	"github.com/spf13/cobra"

	"io.ib67.mods.storyteller/toolchain/internal/workspace"
)

type buildOptions struct {
	root          string
	packName      string
	minecraftRoot string
	world         string
}

func newBuildCommand() *cobra.Command {
	var minecraftRoot string
	var world string
	var watch bool
	command := &cobra.Command{
		Use:   "build [workspace]",
		Short: "Package a workspace into resource pack and data pack archives",
		Args:  cobra.MaximumNArgs(1),
		RunE: func(cmd *cobra.Command, args []string) error {
			if minecraftRoot == "" && world != "" {
				return errors.New("--world requires --minecraft")
			}
			if minecraftRoot != "" && world == "" {
				return errors.New("--minecraft requires --world")
			}
			if world != "" && (world == "." || world == ".." || filepath.Base(world) != world) {
				return fmt.Errorf("invalid world name: %s", world)
			}
			target := "."
			if len(args) > 0 {
				target = args[0]
			}
			current, err := workspace.Open(target)
			if err != nil {
				return err
			}
			absoluteRoot, err := filepath.Abs(current.Root)
			if err != nil {
				return err
			}
			packName := filepath.Base(absoluteRoot)
			if packName == "" || packName == "." || packName == string(filepath.Separator) {
				return fmt.Errorf("cannot derive a pack name from %s", current.Root)
			}
			options := buildOptions{
				root:          current.Root,
				packName:      packName,
				minecraftRoot: minecraftRoot,
				world:         world,
			}
			build := func() error {
				outputs, err := buildWorkspace(options)
				if err != nil {
					return err
				}
				for _, output := range outputs {
					if _, err := fmt.Fprintf(cmd.OutOrStdout(), "wrote %s\n", output); err != nil {
						return err
					}
				}
				return nil
			}
			if err := build(); err != nil {
				return err
			}
			if !watch {
				return nil
			}
			if _, err := fmt.Fprintf(cmd.OutOrStdout(), "watching %s\n", current.Root); err != nil {
				return err
			}
			return watchWorkspace(current.Root, build, cmd.ErrOrStderr())
		},
	}
	command.Flags().StringVar(&minecraftRoot, "minecraft", "", "Deploy the packs into a Minecraft root directory")
	command.Flags().StringVar(&world, "world", "", "World directory name under saves for deployment")
	command.Flags().BoolVar(&watch, "watch", false, "Rebuild when workspace files change")
	return command
}

func buildWorkspace(options buildOptions) ([]string, error) {
	current, err := workspace.Open(options.root)
	if err != nil {
		return nil, err
	}
	packs := []struct {
		name     string
		source   string
		metadata map[string]json.RawMessage
	}{
		{"assets.zip", filepath.Join(current.Root, "assets"), current.Pack.Resourcepack},
		{"data.zip", filepath.Join(current.Root, "data"), current.Pack.Datapack},
	}
	for _, pack := range packs {
		info, err := os.Stat(pack.source)
		if err != nil {
			return nil, err
		}
		if !info.IsDir() {
			return nil, fmt.Errorf("%s is not a directory", pack.source)
		}
	}
	if options.minecraftRoot == "" {
		buildDir := filepath.Join(current.Root, "build")
		if err := os.MkdirAll(buildDir, 0o755); err != nil {
			return nil, err
		}
		outputs := make([]string, 0, len(packs))
		for _, pack := range packs {
			destination := filepath.Join(buildDir, pack.name)
			if err := writeArchive(destination, pack.source, current.Pack.Description, pack.metadata); err != nil {
				return nil, err
			}
			outputs = append(outputs, destination)
		}
		return outputs, nil
	}
	worldDir := filepath.Join(options.minecraftRoot, "saves", options.world)
	info, err := os.Stat(worldDir)
	if err != nil {
		return nil, err
	}
	if !info.IsDir() {
		return nil, fmt.Errorf("%s is not a directory", worldDir)
	}
	targets := []struct {
		destination string
		source      string
		metadata    map[string]json.RawMessage
	}{
		{
			filepath.Join(options.minecraftRoot, "resourcepacks", options.packName),
			filepath.Join(current.Root, "assets"),
			current.Pack.Resourcepack,
		},
		{
			filepath.Join(worldDir, "datapacks", options.packName),
			filepath.Join(current.Root, "data"),
			current.Pack.Datapack,
		},
	}
	outputs := make([]string, 0, len(targets))
	for _, target := range targets {
		if err := writePackDirectory(target.destination, target.source, current.Pack.Description, target.metadata); err != nil {
			return nil, err
		}
		outputs = append(outputs, target.destination)
	}
	return outputs, nil
}

func writeArchive(destination, source string, description json.RawMessage, metadata map[string]json.RawMessage) (err error) {
	file, err := os.Create(destination)
	if err != nil {
		return err
	}
	defer func() {
		if err != nil {
			file.Close()
			os.Remove(destination)
		}
	}()
	encoded, err := encodePackMetadata(description, metadata)
	if err != nil {
		return err
	}
	writer := zip.NewWriter(file)
	header := &zip.FileHeader{Name: "pack.mcmeta", Method: zip.Deflate}
	header.Modified = time.Now()
	header.SetMode(0o644)
	metaWriter, err := writer.CreateHeader(header)
	if err != nil {
		return err
	}
	if _, err := metaWriter.Write(encoded); err != nil {
		return err
	}
	prefix := filepath.Base(source)
	err = filepath.WalkDir(source, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() {
			return nil
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		if !info.Mode().IsRegular() {
			return nil
		}
		relative, err := filepath.Rel(source, path)
		if err != nil {
			return err
		}
		header, err := zip.FileInfoHeader(info)
		if err != nil {
			return err
		}
		header.Name = prefix + "/" + filepath.ToSlash(relative)
		header.Method = zip.Deflate
		entryWriter, err := writer.CreateHeader(header)
		if err != nil {
			return err
		}
		input, err := os.Open(path)
		if err != nil {
			return err
		}
		_, copyErr := io.Copy(entryWriter, input)
		closeErr := input.Close()
		if copyErr != nil {
			return copyErr
		}
		return closeErr
	})
	if err != nil {
		return err
	}
	if err := writer.Close(); err != nil {
		return err
	}
	return file.Close()
}

func writePackDirectory(destination, source string, description json.RawMessage, metadata map[string]json.RawMessage) error {
	prefix := filepath.Base(source)
	if err := os.MkdirAll(destination, 0o755); err != nil {
		return err
	}
	if err := os.RemoveAll(filepath.Join(destination, "pack.mcmeta")); err != nil {
		return err
	}
	if err := os.RemoveAll(filepath.Join(destination, prefix)); err != nil {
		return err
	}
	encoded, err := encodePackMetadata(description, metadata)
	if err != nil {
		return err
	}
	if err := os.WriteFile(filepath.Join(destination, "pack.mcmeta"), encoded, 0o644); err != nil {
		return err
	}
	return copyTree(source, filepath.Join(destination, prefix))
}

func copyTree(source, destination string) error {
	return filepath.WalkDir(source, func(path string, entry fs.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		relative, err := filepath.Rel(source, path)
		if err != nil {
			return err
		}
		target := filepath.Join(destination, relative)
		if entry.IsDir() {
			return os.MkdirAll(target, 0o755)
		}
		info, err := entry.Info()
		if err != nil {
			return err
		}
		if !info.Mode().IsRegular() {
			return nil
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o755); err != nil {
			return err
		}
		input, err := os.Open(path)
		if err != nil {
			return err
		}
		output, err := os.OpenFile(target, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, info.Mode().Perm())
		if err != nil {
			input.Close()
			return err
		}
		_, copyErr := io.Copy(output, input)
		closeOutputErr := output.Close()
		closeInputErr := input.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeOutputErr != nil {
			return closeOutputErr
		}
		return closeInputErr
	})
}

func encodePackMetadata(description json.RawMessage, metadata map[string]json.RawMessage) ([]byte, error) {
	pack := make(map[string]json.RawMessage, len(metadata)+1)
	for key, value := range metadata {
		pack[key] = value
	}
	pack["description"] = description
	return json.Marshal(struct {
		Pack map[string]json.RawMessage `json:"pack"`
	}{Pack: pack})
}
