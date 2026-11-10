# VS Code LSP

1. 首次使用时运行任务 `prepare storyteller lsp`，它会安装扩展依赖并构建 `toolchain/stc/stc`。
2. 按 Ctrl+Shift+B 运行任务 `Run Storyteller LSP Extension`，新窗口会打开 `testpack/`，不经过调试器。
3. 在新窗口里编辑 `data/storyteller/dialog/` 下的 `.txt` 文件。

`launch.json` 的 `extensionHost` 调试配置仍然保留。VS Code 调试器如果报告扩展宿主 10 秒未启动，使用上面的任务启动。扩展位于 `toolchain/vscode-ext`，通过 `toolchain/stc/stc lsp` 启动语言服务器，语义高亮、诊断、补全、转到定义与重命名由语言服务器提供。
