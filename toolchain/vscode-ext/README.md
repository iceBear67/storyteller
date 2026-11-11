# Storyteller Dialog LSP

Language support for Storyteller dialog scripts.

## Features

- Semantic highlighting for dialog scripts.
- JavaScript syntax highlighting inside condition expressions through the built-in JavaScript grammar.
- Diagnostics for script syntax, dialog references, flow commands and state keys.
- Go to definition for `goto` and `expect` dialog references.
- Rename for dialog names in dividers and dialog references.
- JavaScript completion, hover and diagnostics inside condition expressions.

Condition expressions use the built-in TypeScript and JavaScript language service. The extension builds a virtual JavaScript document with `// @ts-check` and JSDoc type annotations, so expressions are parsed as JavaScript and TypeScript-only syntax is reported as an error. State keys are collected from every `update [global] <key> <value>` and `delete [global] <key>` command in the pack and provided to the expression as typed identifiers. Values are typed as `boolean`, `number` or `string`; integer and decimal values both use `number`. A key updated with conflicting kinds, or only deleted, is typed as `any`. Global keys are available as properties of `global`.

## Configuration

`storyteller.stcPath` sets the `stc` executable:

- Empty: search `PATH` for `stc`.
- Absolute path: use it directly.
- Path with a separator: resolve it against the first workspace folder.
- Command name without a separator: search `PATH` for that name.

The setting applies without reloading the window; changing it restarts the language server.

## Development

```bash
npm install
make -C ../.. stc
```

Open the repository root in VS Code and run the `Run Storyteller LSP Extension` task, or press F5 to start an extension host that opens `testpack/`. The task adds `toolchain/stc` to `PATH`.
