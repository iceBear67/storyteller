package command

import (
	"github.com/spf13/cobra"
	glspserver "github.com/tliron/glsp/server"

	"io.ib67.mods.storyteller/toolchain/internal/server"
)

func newLSPCommand() *cobra.Command {
	command := &cobra.Command{
		Use:   "lsp",
		Short: "Run the dialog script language server over stdio",
		RunE: func(*cobra.Command, []string) error {
			runner := glspserver.NewServer(server.New().Handler(), "stc", false)
			return runner.RunStdio()
		},
	}
	command.Flags().Bool("stdio", false, "Accepted for compatibility with language clients that pass --stdio")
	command.Flags().String("clientProcessId", "", "Accepted for compatibility with language clients that pass the client process ID")
	return command
}
