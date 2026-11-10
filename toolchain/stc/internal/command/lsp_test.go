package command

import "testing"

func TestLSPCommandAcceptsClientFlags(t *testing.T) {
	command := newLSPCommand()
	if err := command.Flags().Parse([]string{"--stdio", "--clientProcessId=1234"}); err != nil {
		t.Fatal(err)
	}
}
