package command

import "github.com/spf13/cobra"

func NewRoot() *cobra.Command {
	root := &cobra.Command{
		Use:           "stc",
		Short:         "Story compiler tools for Storyteller",
		SilenceUsage:  true,
		SilenceErrors: true,
	}
	root.AddCommand(newLSPCommand())
	return root
}
