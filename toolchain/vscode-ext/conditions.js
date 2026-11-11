const crypto = require("crypto");
const vscode = require("vscode");

const LANGUAGE_ID = "storyteller-dialog";
const VIRTUAL_SCHEME = "storyteller-js";
const SELECTOR = { language: LANGUAGE_ID };
const SYMBOL_TYPES = new Set(["boolean", "number", "string", "any"]);
const CHECK_DIRECTIVE = "// @ts-check";

class ConditionFeatures {
  constructor(getClient, outputChannel) {
    this.getClient = getClient;
    this.output = outputChannel;
    this.entries = new Map();
    this.symbols = new Map();
    this.virtualContents = new Map();
    this.openedVirtual = new Set();
    this.diagnosticTimers = new Map();
    this.symbolGeneration = 0;
    this.symbolFailureReported = false;
    this.featureFailureReported = false;
    this.disposed = false;
    this.disposables = [];
    this.changeEmitter = new vscode.EventEmitter();

    this.disposables.push(
      vscode.workspace.registerTextDocumentContentProvider(VIRTUAL_SCHEME, {
        onDidChange: this.changeEmitter.event,
        provideTextDocumentContent: (uri) => this.virtualContents.get(uri.toString()) ?? "",
      }),
      vscode.languages.registerCompletionItemProvider(
        SELECTOR,
        {
          provideCompletionItems: (document, position, token, context) =>
            this.provideCompletionItems(document, position, token, context),
        },
        ".",
      ),
      vscode.languages.registerHoverProvider(SELECTOR, {
        provideHover: (document, position) => this.provideHover(document, position),
      }),
      vscode.workspace.onDidOpenTextDocument((document) => this.handleDocument(document)),
      vscode.workspace.onDidChangeTextDocument((event) => this.handleDocument(event.document)),
      vscode.workspace.onDidCloseTextDocument((document) => this.handleClosed(document)),
    );

    for (const pattern of ["**/data/*/dialog/*.txt", "**/data/*/dialog/**/*.txt"]) {
      const watcher = vscode.workspace.createFileSystemWatcher(pattern);
      this.disposables.push(
        watcher,
        watcher.onDidChange(() => this.scheduleSymbolRefresh()),
        watcher.onDidCreate(() => this.scheduleSymbolRefresh()),
        watcher.onDidDelete(() => this.scheduleSymbolRefresh()),
      );
    }

    this.diagnostics = vscode.languages.createDiagnosticCollection("storyteller-condition");
  }

  dispose() {
    this.disposed = true;
    if (this.symbolTimer) {
      clearTimeout(this.symbolTimer);
      this.symbolTimer = undefined;
    }
    for (const timer of this.diagnosticTimers.values()) {
      clearTimeout(timer);
    }
    this.diagnosticTimers.clear();
    this.diagnostics.dispose();
    this.changeEmitter.dispose();
    for (const disposable of this.disposables) {
      disposable.dispose();
    }
    this.disposables.length = 0;
  }

  async provideCompletionItems(document, position, _token, context) {
    const entry = this.entryFor(document);
    if (!entry || !conditionAt(entry, position)) {
      return undefined;
    }
    try {
      const virtualUri = await this.ensureVirtualDocument(entry);
      const result = await vscode.commands.executeCommand(
        "vscode.executeCompletionItemProvider",
        virtualUri,
        new vscode.Position(position.line + entry.preambleLines, position.character),
        context ? context.triggerCharacter : undefined,
      );
      const mapped = mapCompletions(document, position, result, entry.preambleLines);
      this.featureFailureReported = false;
      return mapped;
    } catch (error) {
      this.reportFeatureFailure(error);
      return undefined;
    }
  }

  async provideHover(document, position) {
    const entry = this.entryFor(document);
    if (!entry || !conditionAt(entry, position)) {
      return undefined;
    }
    try {
      const virtualUri = await this.ensureVirtualDocument(entry);
      const hovers = await vscode.commands.executeCommand(
        "vscode.executeHoverProvider",
        virtualUri,
        new vscode.Position(position.line + entry.preambleLines, position.character),
      );
      if (!Array.isArray(hovers) || hovers.length === 0) {
        return undefined;
      }
      const hover = hovers[0];
      this.featureFailureReported = false;
      return new vscode.Hover(
        hover.contents,
        mapRange(document, hover.range, entry.preambleLines),
      );
    } catch (error) {
      this.reportFeatureFailure(error);
      return undefined;
    }
  }

  async handleDocument(document) {
    if (this.disposed || document.languageId !== LANGUAGE_ID) {
      return;
    }
    if (!this.symbols.has(document.uri.toString()) && this.getClient()) {
      await this.refreshSymbols();
    }
    this.scheduleDiagnostics(document);
    this.scheduleSymbolRefresh();
  }

  handleClosed(document) {
    if (document.languageId !== LANGUAGE_ID) {
      return;
    }
    const key = document.uri.toString();
    this.diagnostics.delete(document.uri);
    const timer = this.diagnosticTimers.get(key);
    if (timer) {
      clearTimeout(timer);
      this.diagnosticTimers.delete(key);
    }
  }

  entryFor(document) {
    const key = document.uri.toString();
    const entry = this.entries.get(key);
    if (entry && entry.version === document.version) {
      return entry;
    }
    return this.rebuild(document);
  }

  rebuild(document) {
    const key = document.uri.toString();
    const text = document.getText();
    const spans = conditionSpans(text);
    if (spans.length === 0) {
      this.entries.delete(key);
      this.diagnostics.delete(document.uri);
      return undefined;
    }
    let entry = this.entries.get(key);
    if (!entry) {
      entry = {
        uri: virtualUriFor(document.uri),
        content: "",
        version: -1,
        spans: [],
        preambleLines: 0,
      };
      this.entries.set(key, entry);
    }
    entry.spans = spans;
    entry.version = document.version;
    const built = virtualContent(text, spans, this.symbols.get(key));
    entry.preambleLines = built.preambleLines;
    if (entry.content !== built.content) {
      entry.content = built.content;
      const virtualKey = entry.uri.toString();
      this.virtualContents.set(virtualKey, built.content);
      if (this.openedVirtual.has(virtualKey)) {
        this.changeEmitter.fire(entry.uri);
      }
    }
    return entry;
  }

  async ensureVirtualDocument(entry) {
    const key = entry.uri.toString();
    let document = findDocument(entry.uri);
    if (!document) {
      document = await vscode.workspace.openTextDocument(entry.uri);
      this.openedVirtual.add(key);
    }
    if (document.languageId !== "javascript") {
      document = await vscode.languages.setTextDocumentLanguage(document, "javascript");
      this.openedVirtual.add(key);
    }
    if (!document) {
      throw new Error(`could not open the generated JavaScript document: ${key}`);
    }
    if (document.getText() !== entry.content) {
      this.changeEmitter.fire(entry.uri);
      await this.waitForVirtualContent(entry);
    }
    return entry.uri;
  }

  async waitForVirtualContent(entry) {
    const key = entry.uri.toString();
    for (let attempt = 0; attempt < 100; attempt += 1) {
      const document = findDocument(entry.uri);
      if (document && document.getText() === entry.content) {
        return;
      }
      await new Promise((resolve) => setTimeout(resolve, 10));
    }
    throw new Error(`the generated JavaScript document did not refresh: ${key}`);
  }

  scheduleSymbolRefresh() {
    if (this.symbolTimer) {
      clearTimeout(this.symbolTimer);
    }
    this.symbolTimer = setTimeout(() => {
      this.symbolTimer = undefined;
      this.refreshSymbols();
    }, 300);
  }

  async refreshSymbols() {
    const client = this.getClient();
    if (!client) {
      return;
    }
    const documents = vscode.workspace.textDocuments.filter(
      (document) => document.languageId === LANGUAGE_ID && isDialogDocument(document.uri),
    );
    if (documents.length === 0) {
      return;
    }
    const generation = ++this.symbolGeneration;
    try {
      const tables = await Promise.all(
        documents.map(async (document) => {
          const response = await client.sendRequest("workspace/executeCommand", {
            command: "storyteller.stateSymbols",
            arguments: [document.uri.toString()],
          });
          return [document.uri.toString(), normalizeSymbols(response)];
        }),
      );
      if (this.disposed || generation !== this.symbolGeneration || this.getClient() !== client) {
        return;
      }
      const next = new Map(tables);
      if (sameSymbolMaps(this.symbols, next)) {
        this.symbolFailureReported = false;
        return;
      }
      this.symbols = next;
      this.symbolFailureReported = false;
      for (const document of documents) {
        this.rebuild(document);
      }
      for (const document of documents) {
        this.scheduleDiagnostics(document);
      }
    } catch (error) {
      if (this.getClient() !== client) {
        return;
      }
      this.reportSymbolFailure(error);
    }
  }

  scheduleDiagnostics(document) {
    if (this.disposed || document.languageId !== LANGUAGE_ID) {
      return;
    }
    const key = document.uri.toString();
    const previous = this.diagnosticTimers.get(key);
    if (previous) {
      clearTimeout(previous);
    }
    this.diagnosticTimers.set(
      key,
      setTimeout(() => {
        this.diagnosticTimers.delete(key);
        this.updateDiagnostics(document.uri).catch((error) => this.reportFeatureFailure(error));
      }, 600),
    );
  }

  async updateDiagnostics(uri) {
    if (this.disposed) {
      return;
    }
    const document = findDocument(uri);
    if (!document || document.languageId !== LANGUAGE_ID) {
      this.diagnostics.delete(uri);
      return;
    }
    const entry = this.entryFor(document);
    if (!entry) {
      this.diagnostics.delete(uri);
      return;
    }
    const version = document.version;
    const virtualUri = await this.ensureVirtualDocument(entry);
    const [syntactic, semantic] = await Promise.all([
      requestTypeScriptDiagnostics("syntacticDiagnosticsSync", virtualUri),
      requestTypeScriptDiagnostics("semanticDiagnosticsSync", virtualUri),
    ]);
    const current = findDocument(uri);
    if (this.disposed || !current || current.version !== version) {
      return;
    }
    this.diagnostics.set(uri, mapDiagnostics(current, entry, syntactic.concat(semantic)));
    this.featureFailureReported = false;
  }

  reportSymbolFailure(error) {
    const message = errorMessage(error);
    this.output.appendLine(`state symbol lookup failed: ${message}`);
    if (this.symbolFailureReported) {
      return;
    }
    this.symbolFailureReported = true;
    vscode.window.showErrorMessage(
      `Storyteller: state symbol lookup failed (${message}). Rebuild or update stc, then reload the window.`,
    );
  }

  reportFeatureFailure(error) {
    const message = errorMessage(error);
    this.output.appendLine(`condition language feature failed: ${message}`);
    if (this.featureFailureReported) {
      return;
    }
    this.featureFailureReported = true;
    vscode.window.showErrorMessage(
      `Storyteller: condition language features failed (${message}). Ensure the built-in TypeScript and JavaScript extension is enabled.`,
    );
  }
}

function findDocument(uri) {
  const key = uri.toString();
  return vscode.workspace.textDocuments.find((document) => document.uri.toString() === key);
}

function isDialogDocument(uri) {
  if (uri.scheme !== "file") {
    return false;
  }
  return /\/data\/[^/]+\/dialog\//.test(uri.path);
}

function conditionSpans(text) {
  const lines = text.split(/\r?\n/);
  const spans = [];
  for (let line = 0; line < lines.length; line += 1) {
    const raw = lines[line];
    let open = 0;
    while (open < raw.length && /\s/.test(raw[open])) {
      open += 1;
    }
    if (raw[open] !== "(") {
      continue;
    }
    let depth = 0;
    let close = -1;
    for (let index = open; index < raw.length; index += 1) {
      if (raw[index] === "(") {
        depth += 1;
      } else if (raw[index] === ")") {
        depth -= 1;
        if (depth === 0) {
          close = index;
          break;
        }
      }
    }
    if (close < 0 || close === open + 1) {
      continue;
    }
    spans.push({ line, open, close });
  }
  return spans;
}

function conditionAt(entry, position) {
  return entry.spans.some(
    (span) =>
      span.line === position.line &&
      position.character >= span.open + 1 &&
      position.character <= span.close,
  );
}

function virtualContent(text, spans, symbols) {
  const lines = text.split(/\r?\n/);
  const byLine = new Map();
  for (const span of spans) {
    const conditions = byLine.get(span.line);
    if (conditions) {
      conditions.push(span);
    } else {
      byLine.set(span.line, [span]);
    }
  }
  const masked = lines.map((line, index) => {
    const conditions = byLine.get(index);
    if (!conditions) {
      return " ".repeat(line.length);
    }
    const characters = new Array(line.length).fill(" ");
    for (const condition of conditions) {
      for (let position = condition.open; position <= condition.close; position += 1) {
        characters[position] = line[position];
      }
      const terminator = condition.close + 1;
      if (terminator < characters.length) {
        characters[terminator] = ";";
      } else {
        characters.push(";");
      }
    }
    return characters.join("");
  });
  const declarations = declarationLines(symbols);
  return {
    content: [CHECK_DIRECTIVE, "{", ...declarations, ...masked, "}"].join("\n"),
    preambleLines: 2 + declarations.length,
  };
}

function declarationLines(symbols) {
  const player = uniqueSymbols(symbols ? symbols.player : []).filter((symbol) =>
    isVariableName(symbol.name),
  );
  const global = uniqueSymbols(symbols ? symbols.global : []).filter((symbol) =>
    isPropertyName(symbol.name),
  );
  const declarations = player.map(
    (symbol) =>
      `let ${symbol.name} = /** @type {${symbol.type}} */ (${defaultValue(symbol.type)});`,
  );
  const properties = global.map((symbol) => `${symbol.name}: ${symbol.type}`).join(", ");
  declarations.push(`let global = /** @type {{ ${properties} }} */ ({});`);
  return declarations;
}

function defaultValue(type) {
  if (type === "boolean") {
    return "false";
  }
  if (type === "number") {
    return "0";
  }
  if (type === "string") {
    return '""';
  }
  return "undefined";
}

function uniqueSymbols(values) {
  const byName = new Map();
  for (const value of values) {
    if (!byName.has(value.name)) {
      byName.set(value.name, value);
    }
  }
  return Array.from(byName.values());
}

function isVariableName(name) {
  if (name === "global" || !isPropertyName(name)) {
    return false;
  }
  try {
    new Function(`"use strict"; let ${name};`);
    return true;
  } catch {
    return false;
  }
}

function isPropertyName(name) {
  return /^[A-Za-z_$][A-Za-z0-9_$]*$/.test(name);
}

function virtualUriFor(sourceUri) {
  const hash = crypto.createHash("sha1").update(sourceUri.toString()).digest("hex");
  return vscode.Uri.from({ scheme: VIRTUAL_SCHEME, path: `/c/${hash}.js` });
}

function normalizeSymbols(value) {
  if (!value || !Array.isArray(value.player) || !Array.isArray(value.global)) {
    throw new Error("invalid state symbol response");
  }
  return {
    player: value.player.map(normalizeSymbol),
    global: value.global.map(normalizeSymbol),
  };
}

function normalizeSymbol(value) {
  if (!value || typeof value.name !== "string" || !SYMBOL_TYPES.has(value.type)) {
    throw new Error("invalid state symbol");
  }
  return { name: value.name, type: value.type };
}

function sameSymbolMaps(previous, next) {
  if (previous.size !== next.size) {
    return false;
  }
  for (const [key, value] of next) {
    const current = previous.get(key);
    if (!current || !sameSymbols(current, value)) {
      return false;
    }
  }
  return true;
}

function sameSymbols(previous, next) {
  return (
    previous.player.length === next.player.length &&
    previous.global.length === next.global.length &&
    previous.player.every((symbol, index) => sameSymbol(symbol, next.player[index])) &&
    previous.global.every((symbol, index) => sameSymbol(symbol, next.global[index]))
  );
}

function sameSymbol(previous, next) {
  return previous.name === next.name && previous.type === next.type;
}

function mapCompletions(document, position, result, preambleLines) {
  if (!result) {
    return undefined;
  }
  const items = Array.isArray(result) ? result : result.items;
  if (!Array.isArray(items)) {
    return undefined;
  }
  const mapped = items.map((item) => mapCompletionItem(document, position, item, preambleLines));
  if (!Array.isArray(result) && result.isIncomplete === true) {
    return new vscode.CompletionList(mapped, true);
  }
  return mapped;
}

function mapCompletionItem(document, position, item, preambleLines) {
  const label = typeof item.label === "string" ? item.label : item.label.label;
  const mapped = new vscode.CompletionItem(label, item.kind);
  mapped.detail = item.detail;
  mapped.documentation = item.documentation;
  mapped.sortText = item.sortText;
  mapped.filterText = item.filterText;
  mapped.preselect = item.preselect;
  mapped.commitCharacters = item.commitCharacters;
  mapped.insertTextFormat = item.insertTextFormat;
  mapped.tags = item.tags;
  mapped.labelDetails = item.labelDetails;
  const wordRange = document.getWordRangeAtPosition(position) || new vscode.Range(position, position);
  if (item.textEdit) {
    const edit = mapCompletionTextEdit(document, item.textEdit, preambleLines);
    if (edit) {
      mapped.textEdit = edit;
    } else {
      const adjusted = adjustDotCompletion(document, position, item.textEdit.newText, wordRange);
      mapped.range = adjusted.range;
      mapped.insertText = adjusted.insertText;
    }
  } else {
    const adjusted = adjustDotCompletion(document, position, item.insertText, wordRange);
    mapped.range = adjusted.range;
    if (item.insertText !== undefined) {
      mapped.insertText = adjusted.insertText;
    }
  }
  return mapped;
}

function mapCompletionTextEdit(document, edit, preambleLines) {
  if (edit.replace) {
    const insert = mapRange(document, edit.insert, preambleLines);
    const replace = mapRange(document, edit.replace, preambleLines);
    if (insert && replace) {
      return new vscode.InsertReplaceEdit(edit.newText, insert, replace);
    }
    if (replace) {
      return new vscode.TextEdit(replace, edit.newText);
    }
    return undefined;
  }
  const range = mapRange(document, edit.range, preambleLines);
  if (!range) {
    return undefined;
  }
  return new vscode.TextEdit(range, edit.newText);
}

function adjustDotCompletion(document, position, insertText, wordRange) {
  const value = typeof insertText === "string" ? insertText : insertText ? insertText.value : undefined;
  if (typeof value !== "string" || !value.startsWith(".")) {
    return { range: wordRange, insertText };
  }
  const line = document.lineAt(position.line).text;
  if (position.character === 0 || line[position.character - 1] !== ".") {
    return { range: wordRange, insertText };
  }
  const range = new vscode.Range(position.translate(0, -1), position);
  if (typeof insertText === "string") {
    return { range, insertText: value.slice(1) };
  }
  return { range, insertText: new vscode.SnippetString(value.slice(1)) };
}

function mapRange(document, range, preambleLines) {
  if (!range) {
    return undefined;
  }
  const startLine = range.start.line - preambleLines;
  if (startLine < 0 || startLine >= document.lineCount) {
    return undefined;
  }
  const endLine = Math.max(
    startLine,
    Math.min(range.end.line - preambleLines, document.lineCount - 1),
  );
  return new vscode.Range(
    startLine,
    Math.min(range.start.character, document.lineAt(startLine).text.length),
    endLine,
    Math.min(range.end.character, document.lineAt(endLine).text.length),
  );
}

async function requestTypeScriptDiagnostics(command, uri) {
  const response = await vscode.commands.executeCommand("typescript.tsserverRequest", command, {
    file: uri,
    includeLinePosition: true,
  });
  if (!response || response.type !== "response" || !Array.isArray(response.body)) {
    const message = response && response.message ? response.message : "no response";
    throw new Error(`${command} failed: ${message}`);
  }
  return response.body;
}

function mapDiagnostics(document, entry, diagnostics) {
  const conditionLines = new Set(entry.spans.map((span) => span.line));
  const result = [];
  for (const diagnostic of diagnostics) {
    if (!diagnostic) {
      continue;
    }
    const start = diagnosticLocation(diagnostic.startLocation, diagnostic.start);
    const end = diagnosticLocation(diagnostic.endLocation, diagnostic.end);
    if (!start || !end) {
      continue;
    }
    const sourceLine = start.line - 1 - entry.preambleLines;
    if (!conditionLines.has(sourceLine)) {
      continue;
    }
    const severity = diagnosticSeverity(diagnostic.category);
    if (severity === undefined) {
      continue;
    }
    const lineText = document.lineAt(sourceLine).text;
    const startCharacter = clamp(start.offset - 1, 0, lineText.length);
    let endLine = end.line - 1 - entry.preambleLines;
    if (endLine < sourceLine) {
      endLine = sourceLine;
    }
    if (endLine >= document.lineCount) {
      endLine = document.lineCount - 1;
    }
    const endText = document.lineAt(endLine).text;
    const endCharacter = clamp(end.offset - 1, 0, endText.length);
    const item = new vscode.Diagnostic(
      new vscode.Range(sourceLine, startCharacter, endLine, endCharacter),
      diagnostic.message || diagnostic.text || "JavaScript error",
      severity,
    );
    item.source = "storyteller-js";
    if (diagnostic.code !== undefined) {
      item.code = diagnostic.code;
    }
    result.push(item);
  }
  return result;
}

function diagnosticLocation(location, fallback) {
  if (location && typeof location.line === "number" && typeof location.offset === "number") {
    return location;
  }
  if (
    fallback &&
    typeof fallback === "object" &&
    typeof fallback.line === "number" &&
    typeof fallback.offset === "number"
  ) {
    return fallback;
  }
  return undefined;
}

function diagnosticSeverity(category) {
  if (category === "error") {
    return vscode.DiagnosticSeverity.Error;
  }
  if (category === "warning") {
    return vscode.DiagnosticSeverity.Warning;
  }
  return undefined;
}

function clamp(value, minimum, maximum) {
  return Math.max(minimum, Math.min(value, maximum));
}

function errorMessage(error) {
  return error instanceof Error ? error.message : String(error);
}

module.exports = { ConditionFeatures };
