# stc

Storyteller 的开发工具，当前提供对话脚本的语言服务器，通过标准输入输出与编辑器通信。功能包括语义高亮、语法检查与错误报告。

## 构建

```bash
cd toolchain
go build -o stc .
```

产物是单个可执行文件。执行 `stc lsp` 启动语言服务器，通过标准输入输出通信，不需要配置文件。

## 编辑器接入

服务器不限制客户端发出的语言标识，只要编辑器把对话脚本交给这个服务器即可。推荐为 `data/<命名空间>/dialog/` 目录下的 `.txt` 文件注册一个独立语言标识。

Neovim 0.11 及以上版本的配置示例：

```lua
vim.filetype.add({
  pattern = {
    [".*/data/[^/]+/dialog/.*%.txt"] = "storyteller-dialog",
  },
})

vim.lsp.config("storyteller_dialog", {
  cmd = { "/path/to/stc", "lsp" },
  filetypes = { "storyteller-dialog" },
  root_markers = { "gradle.properties" },
})

vim.lsp.enable("storyteller_dialog")
```

JetBrains 编辑器可以通过 LSP4IJ 添加语言服务器，命令填写可执行文件的绝对路径，文件关联使用 `data/*/dialog/**/*.txt` 这样的通配模式。

VS Code 需要先安装提供 `storyteller-dialog` 语言标识的扩展，再让扩展把该语言交给这个服务器；仅安装通用 LSP 客户端无法完成文件关联。

## 高亮

服务器通过语义标记提供高亮，覆盖以下元素：

| 元素 | 标记类型 |
| --- | --- |
| 整行注释与行尾注释 | comment |
| 条件括号、动作方括号、分隔符、颜色代码 | operator |
| 条件表达式 | variable |
| 分界线后的对话名称 | type |
| 人物名称 | property |
| 对白正文 | string |
| 脚本命令名称 | keyword |
| 普通命令名称 | function |
| 选择器中的实体选择部分 | parameter |
| 状态键名 | property |
| `repeatable` | modifier |
| `wait` 的游戏刻数 | number |

## 检查

诊断覆盖 `DialogParser` 的全部语法错误，以及脚本加载与命令执行阶段能提前判定的问题：

- 分界线缺少名称、名称包含空白或冒号、名称与脚本名称或其他子对话重复
- 条件缺少右括号、条件为空、条件后没有动作
- 命令动作缺少右括号、命令序列中有空命令、选择器格式错误、方括号后出现多余内容
- `wait` 的游戏刻数格式错误，点击命令中出现 `wait`
- 内联命令缺少结束符、内联命令为空、内联命令中出现流程命令
- 人物名称为空
- `repeatable` 出现在独立命令动作中，或者作为点击命令的第一条命令却没有后续命令
- 脚本命令参数不完整或多余，状态键名包含非法字符或是保留键
- `goto` 与 `expect` 引用了当前脚本中不存在的对话

带脚本名的跨文件引用不做检查，因为服务器只处理当前文档。
