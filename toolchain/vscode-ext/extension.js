const vscode = require("vscode");
const { LanguageClient, State, TransportKind } = require("vscode-languageclient/node");
const { resolveStc } = require("./stcPath");
const { ConditionFeatures } = require("./conditions");

let client;
let outputChannel;
let conditionFeatures;
let restartChain = Promise.resolve();

async function activate(context) {
  outputChannel = vscode.window.createOutputChannel("Storyteller", { log: true });
  conditionFeatures = new ConditionFeatures(
    () => (client && client.state === State.Running ? client : undefined),
    outputChannel,
  );
  context.subscriptions.push(outputChannel, conditionFeatures);

  context.subscriptions.push(
    vscode.workspace.onDidChangeConfiguration((event) => {
      if (event.affectsConfiguration("storyteller.stcPath")) {
        restartChain = restartChain
          .then(() => restartClient())
          .catch((error) => {
            reportError(`language server restart failed: ${error.message ?? error}`);
          });
      }
    }),
  );

  await startClient();
}

async function startClient() {
  let executable;
  try {
    executable = resolveStc();
  } catch (error) {
    reportError(error.message);
    return;
  }
  outputChannel.appendLine(`stc executable: ${executable}`);
  const folder = vscode.workspace.workspaceFolders?.[0]?.uri.fsPath;
  const serverOptions = {
    command: executable,
    args: ["lsp"],
    transport: TransportKind.stdio,
  };
  if (folder) {
    serverOptions.options = { cwd: folder };
  }
  const clientOptions = {
    documentSelector: [{ scheme: "file", language: "storyteller-dialog" }],
    outputChannel,
  };
  const next = new LanguageClient("storyteller-dialog", "Storyteller Dialog", serverOptions, clientOptions);
  client = next;
  next.onDidChangeState((event) => {
    if (event.newState === State.Running) {
      conditionFeatures.refreshSymbols();
    }
  });
  try {
    await next.start();
  } catch (error) {
    outputChannel.appendLine(`language server failed to start: ${error.message ?? error}`);
    if (client === next) {
      client = undefined;
    }
    reportError(`language server failed to start: ${error.message ?? error}`);
  }
}

async function restartClient() {
  const previous = client;
  client = undefined;
  if (previous) {
    try {
      await previous.stop();
    } catch (error) {
      outputChannel.appendLine(`failed to stop the language server: ${error.message ?? error}`);
    }
  }
  await startClient();
}

function reportError(message) {
  outputChannel.appendLine(message);
  const action = "Open Settings";
  vscode.window.showErrorMessage(`Storyteller: ${message}`, action).then((selection) => {
    if (selection === action) {
      vscode.commands.executeCommand("workbench.action.openSettings", "storyteller.stcPath");
    }
  });
}

function deactivate() {
  const current = client;
  client = undefined;
  return current ? current.stop() : undefined;
}

module.exports = { activate, deactivate };
