# VS Code LSP

1. 首次使用时运行任务 `prepare storyteller lsp`，它会安装扩展依赖并构建 `toolchain/stc/stc`。
2. 按 Ctrl+Shift+B 运行任务 `Run Storyteller LSP Extension`，新窗口会打开 `testpack/`，不经过调试器。任务会把 `toolchain/stc` 加入新窗口的 `PATH`。
3. 在新窗口里编辑 `data/storyteller/dialog/` 下的 `.txt` 文件。

`launch.json` 的 `extensionHost` 调试配置仍然保留，并通过 `env` 把 `toolchain/stc` 加入 `PATH`。VS Code 调试器如果报告扩展宿主 10 秒未启动，使用上面的任务启动。

扩展位于 `toolchain/vscode-ext`。未设置 `storyteller.stcPath` 时，扩展从 `PATH` 查找 `stc`；设置可以填写绝对路径、相对于第一个工作区目录的路径，或直接填写命令名。语言服务器提供语义高亮、诊断、转到定义与重命名。条件表达式 `(...)` 的语法高亮使用内置 JavaScript 语法，补全、悬停与 JavaScript 诊断由 VS Code 内置的 TypeScript 语言服务按 JavaScript 检查，标识符来自项目里 `update` 命令写入的状态键。
