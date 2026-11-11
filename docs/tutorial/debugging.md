# 调试与排错

对话跑得不对时，模组提供一套调试命令查看脚本、状态与进度。这一章介绍这些命令的用法与常见问题的排查方法。全部命令需要管理员权限。

## 打开调试

```text
/st debug
```

全局调试开关，再次执行关闭。开启后：

- 对白显示时会附加一行灰色的调试信息，内容包括动作的条件、点击命令与 token；带条件、点击命令、颜色代码或内联命令的对白才附加，命令动作与等待动作始终附加；
- 触发实体画出线框，可用调试棒查看属性，见[触发实体](./triggers.md)；
- 条件求值出错时向全部在线玩家广播错误信息。

## 查看状态

```text
/st debug dump
```

打印执行玩家自己的玩家状态，每个条目按 `键=值` 排列。`dump global` 打印全局状态，`dump frame` 打印当前对话 frame 的全部字段：

| 字段 | 含义 |
| --- | --- |
| `suspended` | 挂起标记 |
| `scriptId` / `dialogId` | 当前脚本与对话 |
| `actionIndex` / `actionTicks` | 当前动作下标与已进行游戏刻 |
| `intervalTicks` / `nextActionTick` | 动作间隔与下一个动作允许开始的时间 |
| `allowedContinuation` | `expect` 设置的允许列表 |
| `poppedCommands` | 已展示过的点击 token |

```text
/st debug trace
```

按 frame 顺序打印全部对话 frame，每行是 `#序号 脚本:对话 action=动作下标 ticks=已进行游戏刻`。玩家同时进行多层对话（`goto` 嵌套）时用它看清每一层的进度。

## 挂起与恢复

条件表达式抛错时（变量不存在、类型不对、表达式非法），执行动作的玩家会被**挂起**：对话停止推进，错误写入日志，调试模式下广播给全部玩家。挂起是有意的保护，防止错误条件反复执行。

恢复方式有三种：

```text
/st debug suspend
/storyteller:reset
```

`/st debug suspend` 切换执行玩家的挂起标记，修好脚本并 `/reload` 后用它恢复；`/storyteller:reset` 直接清空全部对话进度。脚本内容变化导致校验和变化时，挂起标记也会随之清空。

## 排查清单

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `/reload` 后脚本没生效 | 脚本文法错误，整个数据包加载失败，旧脚本继续工作 | 看错误信息里的行号，修改后重新加载 |
| 脚本 ID 冲突报错 | 不同命名空间产生了相同的脚本 ID | 移动文件或重命名，保持路径唯一 |
| 点击旧对话文本报 `Unknown action token` | 脚本修改后旧 token 不存在了 | 重新进入对话，点击新文本 |
| 对话卡住不动 | 条件求值出错，玩家被挂起 | `/st debug dump` 看 `suspended`，查日志里的错误，修复后 `/st debug suspend` 恢复 |
| `goto` 报 `Dialog is not allowed by the current frame` | 当前对话设置了 `expect` 限制，目标不在允许列表里 | 检查 `expect` 的引用 |
| `check` 报 `Check failed` | 表达式结果为假，序列有意终止 | 属于正常行为，检查条件是否写对 |
| 玩家断线重连后对话消失 | 断线期间脚本内容变化，校验和不一致，进度清空 | 内容变化会清空全部对话进度，让玩家重新开始 |

日志文件在服务端或客户端的 `logs/latest.log`，Storyteller 的日志带 `storyteller` 前缀。
