# 工具链

Storyteller 的开发工具链由两部分组成：VS Code 扩展提供对话脚本的编辑体验，`stc` 命令行提供语言服务器、工作区检查与打包部署。

## VS Code 扩展

### 安装与设置

安装 Storyteller Dialog LSP 扩展后，`data/*/dialog/` 下的 `.txt` 文件自动识别为对话脚本，提供语义高亮、诊断、转到定义与重命名。

`storyteller.stcPath` 设置指定 `stc` 可执行文件的位置：

- 留空：在 `PATH` 里查找 `stc`；
- 绝对路径：直接使用；
- 带分隔符的相对路径：按第一个工作区文件夹解析；
- 不带分隔符的命令名：在 `PATH` 里查找该命令。

修改设置后语言服务器自动重启，不需要重载窗口。

### 条件表达式的类型支持

条件表达式内部由 VS Code 内置的 TypeScript 语言服务处理。扩展把脚本转换成带 `// @ts-check` 与 JSDoc 类型声明的虚拟 JavaScript 文档，因此：

- 表达式按 JavaScript 解析，补全、悬停与诊断都可用；
- TypeScript 专属写法（类型断言、类型标注）作为语法错误报告；
- 状态键按工作区内 `update [global] 键名 值` 与 `delete [global] 键名` 的用法收集并推断类型：布尔、整数与小数都推断为 `number`，字符串为 `string`，同一键出现不同种类或只被 `delete` 时为 `any`；全局键作为 `global` 对象的属性补全。

状态键只按是否出现收集，不分析先后顺序与分支；命令读取已打开文档的内容，未保存的修改同样生效。

## stc 命令行

### 工作区

`stc` 以目录作为工作区：

```text
<工作区>/
├── pack.mcmeta
├── assets/
│   └── <命名空间>/
└── data/
    └── <命名空间>/
```

工作区的 `pack.mcmeta` 使用 stc 自己的结构：`pack.description` 由两个包共用，`pack.resourcepack` 与 `pack.datapack` 分别保存资源包与数据包的元数据：

```json
{
  "pack": {
    "description": "Example pack",
    "resourcepack": { "min_format": [97, 1], "max_format": [97, 1] },
    "datapack": { "min_format": [121, 0], "max_format": [121, 0] }
  }
}
```

构建时两个元数据各自与 `pack.description` 合并，写入生成的 `pack.mcmeta`。

### lint

```bash
stc lint [工作区]
```

检查工作区中 `data/<命名空间>/dialog/` 下的全部 `.txt` 脚本，按 `文件:行:列: 级别: 消息` 输出诊断，存在错误时以非零状态退出。跨文件引用按脚本 ID 解析：目标脚本在工作区内但对话不存在时报错误，目标脚本不在工作区内时输出警告。

### build

```bash
stc build [工作区]
```

在工作区下创建 `build/` 目录，生成两个压缩包：

- `build/assets.zip`：资源包；
- `build/data.zip`：数据包。

### 部署到游戏

```bash
stc build --minecraft <Minecraft根目录> --world <世界名> [工作区]
```

把两个包以目录形式部署到游戏目录，不生成压缩包：

- `<根目录>/resourcepacks/<工作区目录名>/`
- `<根目录>/saves/<世界名>/datapacks/<工作区目录名>/`

部署会替换目标目录的 `pack.mcmeta` 与 `assets/` 或 `data/`，保留目录里的其他文件。

### watch

```bash
stc build ... --watch
```

首次构建后继续运行，监视工作区的 `pack.mcmeta`、`assets/` 与 `data/`，文件变化停止 200 毫秒后重新构建。配合游戏内 `/reload` 使用。

## 其他编辑器接入

语言服务器通过标准输入输出通信，执行 `stc lsp` 启动。服务器不限制客户端的语言标识，为 `data/<命名空间>/dialog/` 下的 `.txt` 注册独立语言标识即可。

Neovim 0.11 及以上版本：

```lua
vim.filetype.add({
  pattern = {
    [".*/data/[^/]+/dialog/.*%.txt"] = "storyteller-dialog",
  },
})

vim.lsp.config("storyteller_dialog", {
  cmd = { "/path/to/stc", "lsp" },
  filetypes = { "storyteller-dialog" },
})

vim.lsp.enable("storyteller_dialog")
```

JetBrains 编辑器通过 LSP4IJ 添加语言服务器，命令填写 `stc` 可执行文件的绝对路径，文件关联使用 `data/*/dialog/**/*.txt` 这样的通配模式。

## 语言服务器能力

### 语义高亮

| 元素 | 标记 |
| --- | --- |
| 整行注释与行尾注释 | comment |
| 条件括号、动作方括号、分隔符、颜色代码 | operator |
| 分界线后的对话名称 | type |
| 人物名称 | property |
| 对白正文 | string |
| 脚本命令名称 | keyword |
| 普通命令名称 | function |
| 选择器中的实体选择部分 | parameter |
| 状态键名 | property |
| `repeatable` | modifier |
| `wait` 的游戏刻数 | number |

条件表达式内部的高亮由编辑器处理：VS Code 扩展用内置 JavaScript 语法着色，其他编辑器只得到条件括号的标记。

### 诊断

覆盖 `DialogParser` 的全部语法错误，以及脚本加载与命令执行阶段能提前判定的问题：分界线命名、条件与括号配对、命令动作与选择器格式、`wait` 的参数与位置限制、内联命令限制、`repeatable` 限制、脚本命令参数、保留键名、`goto` 与 `expect` 对当前脚本内对话的引用。

### 跳转与重命名

在 `goto` 与 `expect` 的对话引用上转到定义，跳到目标脚本的分界线；引用脚本本身时跳到文件开头。在分界线名称或对话引用上重命名，同时修改定义与工作区内的全部引用；`脚本:对话` 引用里的脚本部分不参与重命名。新名称不能为空、不能包含空白或冒号，也不能与同一脚本中已有的对话重名。重命名只适用于子对话，默认对话的名称就是脚本 ID，改名需要重命名脚本文件。
