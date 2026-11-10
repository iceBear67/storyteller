package main

import (
	"fmt"
	"os"

	"io.ib67.mods.storyteller/toolchain/internal/command"
)

func main() {
	if err := command.NewRoot().Execute(); err != nil {
		fmt.Fprintln(os.Stderr, err)
		os.Exit(1)
	}
}
