# stc

Storyteller 的开发工具，提供对话脚本的语言服务器、工作区检查与打包命令。语言服务器通过标准输入输出与编辑器通信，功能包括语义高亮、语法检查、错误报告、转到定义与重命名。

## 构建

```bash
cd toolchain/stc
go build -o stc .
```

产物是单个可执行文件。执行 `stc lsp` 启动语言服务器，通过标准输入输出通信，不需要配置文件。

## 工作区结构

`stc` 以目录作为工作区，根目录下放置 `pack.mcmeta`，与它平行的是资源包目录 `assets/` 和数据包目录 `data/`：

```
<工作区>/
├── pack.mcmeta
├── assets/
│   └── <命名空间>/
└── data/
    └── <命名空间>/
```

工作区的 `pack.mcmeta` 使用 `stc` 自己的结构。`pack.description` 由两个包共用，`pack.resourcepack` 与 `pack.datapack` 分别保存资源包与数据包的元数据：

```json
{
  "pack": {
    "description": "Example pack",
    "resourcepack": {
      "min_format": [97, 1],
      "max_format": [97, 1]
    },
    "datapack": {
      "min_format": [121, 0],
      "max_format": [121, 0]
    }
  }
}
```

构建时，`pack.resourcepack` 与 `pack.datapack` 会各自与 `pack.description` 合并，写入生成的 `pack.mcmeta`。元数据的具体字段由 Minecraft 版本决定，`stc` 不解释这些字段。

`assets/` 按标准资源包结构组织，`data/` 按标准数据包结构组织。命令接受一个可选的工作区路径，省略时使用当前目录。

`stc lint [workspace]` 检查工作区中 `data/<命名空间>/dialog/` 下的全部 `.txt` 脚本，按 `文件:行:列: 级别: 消息` 输出诊断，存在错误时以非零状态退出。跨文件引用按脚本 ID 解析：目标脚本在工作区内但对话不存在时报错，目标脚本不在工作区内时输出警告。

`stc build [workspace]` 在工作区下创建 `build/` 目录，生成两个压缩包：

- `build/assets.zip`：资源包，根目录包含 `assets/` 的内容与生成的 `pack.mcmeta`，元数据取自 `pack.resourcepack`。
- `build/data.zip`：数据包，根目录包含 `data/` 的内容与生成的 `pack.mcmeta`，元数据取自 `pack.datapack`。

`stc build --minecraft <Minecraft 根目录> --world <世界名称> [workspace]` 把两个包以目录形式部署到游戏目录，此时不生成压缩包：

- `<Minecraft 根目录>/resourcepacks/<工作区目录名>/`：资源包。
- `<Minecraft 根目录>/saves/<世界名称>/datapacks/<工作区目录名>/`：数据包。

部署会替换目标目录中的 `pack.mcmeta` 与对应的 `assets/` 或 `data/` 目录，保留目录里的其他文件。

`stc build ... --watch` 在首次构建后继续运行，监视工作区的 `pack.mcmeta`、`assets/` 与 `data/`，文件变化停止 200 毫秒后重新构建。

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

VS Code 可以使用仓库里的 `toolchain/vscode-ext` 扩展；仓库根目录的 `.vscode` 已经配置好启动方式，打开仓库根目录后按 F5，新窗口会打开 `testpack/`。扩展通过 `storyteller.stcPath` 设置或 `PATH` 查找 `stc`。条件表达式内部的语法高亮由扩展贡献的 TextMate 语法包含内置 JavaScript 语法完成，补全、悬停与 JavaScript 诊断由 VS Code 内置的 TypeScript 语言服务提供。扩展把条件放进带 `// @ts-check` 与 JSDoc 类型声明的虚拟 JavaScript 文件，TypeScript 专属的断言与类型标注会作为语法错误报告；状态键来自 `storyteller.stateSymbols` 命令读取的项目里 `update` 命令。

## 高亮

服务器通过语义标记提供高亮，覆盖以下元素：

| 元素 | 标记类型 |
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

条件表达式内部不再由语言服务器提供语义标记。VS Code 扩展使用内置的 JavaScript 语法为括号内的内容着色；其他编辑器只会得到条件括号的 operator 标记。

## 跳转

在 `goto` 与 `expect` 的对话引用上使用转到定义，会跳转到目标脚本的分界线。引用脚本本身时跳到对应文件开头。目标脚本从当前文件所在 `data/` 目录的各命名空间中查找，同一脚本 ID 出现在多个命名空间时返回全部位置。

## 重命名

在分界线名称或 `goto`、`expect` 的对话引用上使用重命名，会同时修改定义与工作区内的全部引用。`script:dialog` 引用中的脚本 ID 部分不参与重命名。重命名只适用于子对话；默认对话的名称就是脚本 ID，改名需要重命名脚本文件。新名称不能为空、不能包含空白或冒号，也不能与同一脚本中已有的对话重名。

## 状态键查询

VS Code 扩展通过 `workspace/executeCommand` 调用 `storyteller.stateSymbols`，参数是当前脚本的 URI。服务器从该脚本所在的 `data` 目录遍历全部命名空间，收集所有 `update [global] 键名 值` 与 `delete [global] 键名` 的目标，按脚本出现顺序合并后返回：

```json
{
  "player": [
    { "name": "score", "type": "number" },
    { "name": "visited", "type": "boolean" },
    { "name": "label", "type": "string" },
    { "name": "mixed", "type": "any" }
  ],
  "global": [
    { "name": "chapter", "type": "number" }
  ]
}
```

类型按运行时 `update` 的取值规则推断：先去掉首尾空白与成对的单引号或双引号，`true`、`false` 为布尔类型，整数与小数都写成 `number`，其余文本为字符串类型。同一键出现整数与小数时仍为 `number`；出现不同种类时使用 `any`。只被 `delete`、从未被 `update` 的键也使用 `any`。扩展把玩家键声明为条件作用域内的变量，把全局键声明为 `global` 对象的属性，悬停与补全详情会显示推断出的类型。命令读取已打开文档的内容，未保存的修改同样会被收集。

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

语言服务器只处理当前文档，带脚本名的跨文件引用不做检查；`stc lint` 会检查工作区内的引用。
