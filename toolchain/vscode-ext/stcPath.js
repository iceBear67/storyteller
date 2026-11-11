const fs = require("fs");
const path = require("path");
const vscode = require("vscode");
const which = require("which");

function resolveStc() {
  const configured = vscode.workspace.getConfiguration("storyteller").get("stcPath", "").trim();
  if (configured === "") {
    const found = which.sync("stc", { nothrow: true });
    if (!found) {
      throw new Error('cannot find stc; set "storyteller.stcPath" or add stc to PATH');
    }
    return requireExecutable(found);
  }
  let candidate = configured;
  if (!path.isAbsolute(candidate)) {
    if (candidate.includes("/") || candidate.includes("\\")) {
      const folders = vscode.workspace.workspaceFolders;
      if (!folders || folders.length === 0) {
        throw new Error('a relative "storyteller.stcPath" requires an open workspace folder');
      }
      candidate = path.resolve(folders[0].uri.fsPath, candidate);
    } else {
      const found = which.sync(candidate, { nothrow: true });
      if (!found) {
        throw new Error(`cannot find ${candidate} on PATH`);
      }
      candidate = found;
    }
  }
  return requireExecutable(candidate);
}

function requireExecutable(candidate) {
  let info;
  try {
    info = fs.statSync(candidate);
  } catch {
    throw new Error(`stc executable does not exist: ${candidate}`);
  }
  if (!info.isFile()) {
    throw new Error(`stc path is not a file: ${candidate}`);
  }
  if (process.platform !== "win32") {
    try {
      fs.accessSync(candidate, fs.constants.X_OK);
    } catch {
      throw new Error(`stc executable is not executable: ${candidate}`);
    }
  }
  return candidate;
}

module.exports = { resolveStc };
