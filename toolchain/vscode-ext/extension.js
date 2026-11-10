const path = require("path");
const { window } = require("vscode");
const { LanguageClient, TransportKind } = require("vscode-languageclient/node");

let client;

function activate(context) {
  const command = path.resolve(__dirname, "..", "stc", "stc");
  const serverOptions = {
    command,
    args: ["lsp"],
    transport: TransportKind.stdio,
  };
  const clientOptions = {
    documentSelector: [{ scheme: "file", language: "storyteller-dialog" }],
  };
  client = new LanguageClient("storyteller-dialog", "Storyteller Dialog", serverOptions, clientOptions);
  client.start().catch((error) => {
    window.showErrorMessage(`Storyteller Dialog failed to start: ${error}`);
  });
}

function deactivate() {
  return client ? client.stop() : undefined;
}

module.exports = { activate, deactivate };
