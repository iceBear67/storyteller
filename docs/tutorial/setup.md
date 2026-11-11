# 安装与第一段对话

这一章从零开始：安装模组与编辑器扩展，建一个最小的数据包，写三行对白，然后在游戏里看着它按游戏刻依次显示。

## 安装模组

Storyteller 运行在 Fabric 上。准备一台 Minecraft 26.3 的客户端或服务器，装好 Fabric 加载器，然后在 `mods/` 文件夹放入三个 jar：

- **storyteller**：本模组，从发布页下载；
- **Fabric API**：Fabric 的基础 API 库；
- **Fabric Language Kotlin**：Kotlin 语言支持，Storyteller 用它编写。

三个文件都放在 `mods/` 下即可，版本以发布页标注的为准。

## 安装 VS Code 扩展

在 VS Code 里安装 Storyteller Dialog LSP 扩展。装好之后，凡是放在 `data/*/dialog/` 下的 `.txt` 文件都会被识别成对话脚本，获得语法高亮、错误诊断与自动补全。后续章节的脚本全部用 VS Code 编写。

## 建数据包

Storyteller 的对话脚本放在数据包里。打开你的存档文件夹，进入 `datapacks/`，新建一个文件夹作为数据包，结构如下：

```
saves/<你的世界>/datapacks/
└── village/
    ├── pack.mcmeta
    └── data/
        └── village/
            └── dialog/
                └── intro.txt
```

`pack.mcmeta` 是数据包的元数据文件，内容如下：

```json
{
  "pack": {
    "pack_format": 121,
    "description": "村庄任务"
  }
}
```

`intro.txt` 是第一个对话脚本，先写三行对白：

```text
Bob: 欢迎来到灰松村。
Bob: 村口的路不好走，慢点走。
Bob: 要是有空，来东边找我，有事情要拜托你。
```

每行对白以「人物名称:」开头，冒号前是人物名称，冒号后是正文。不带人物名称的行会作为旁白显示，样式在下一章调整。

## 在游戏里播放

进入游戏（或进入服务器），执行：

```text
/reload
```

`/reload` 会重新加载全部数据包。没有报错就说明脚本已经加载。然后用管理员账号执行：

```text
/storyteller:goto intro:intro
```

`intro:intro` 是对话引用，格式为 `脚本:对话`。脚本 ID 来自文件路径相对 `dialog/` 的部分：`data/village/dialog/intro.txt` 的脚本 ID 是 `intro`，命名空间不参与 ID；分界线之前的动作属于默认对话，默认对话的名称与脚本相同，所以引用写成 `intro:intro`。命令的输入过程中可以用 Tab 补全对话名。

对白开始播放。每行对白显示一段时间后消失，下一行接着出现：

- 基础显示时长为 20 游戏刻（1 秒），加上人物名称与正文的长度，最多 200 游戏刻（10 秒）；
- 两行对白之间默认间隔 1 游戏刻；
- 全部对白播完，对话自动结束。

想重看一遍，先清掉当前进度：

```text
/storyteller:reset
```

再次执行 `goto` 即可。

## 出问题的时候

- **`/reload` 报错**：脚本文法错误会让整个数据包加载失败，旧脚本继续工作。错误信息带行号，按提示修改后重新 `/reload`。
- **脚本没被识别**：检查文件位置。脚本必须放在 `data/<命名空间>/dialog/` 下，扩展名是 `.txt`。
- **`goto` 提示 Unknown dialog**：确认引用写成了 `脚本:对话` 的形式，且脚本 ID 与文件路径一致。

下一章开始系统学习脚本的写法。
