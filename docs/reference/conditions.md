# 条件表达式

条件表达式出现在三个地方：动作行开头的 `(表达式)`、脚本命令 `check 表达式`、实体选择器字段 `st_predicate=表达式`。三处使用同一套求值规则与变量。

## 求值规则

表达式用 JavaScript（GraalJS）求值，必须返回布尔值，返回其他类型视为错误。相同源码的表达式只编译一次。表达式里可以用 JavaScript 的比较、逻辑与算术运算：

```text
(chapter >= 2 && name != "Bob")
(global.quest_done == true)
quest_accepted == null
```

## 变量

### 玩家状态

执行玩家的状态键名直接作为变量。键没有设置过时，变量的值是 `undefined`，因此 `键名 == null` 可以判断「还没有这个键」：

```text
(quest_done != null) Bob: 你已经完成了。
(quest_done == null) Bob: 你还没接过这个任务。
```

`dialogStack`、`dialogScriptChecksum`、`dialogSuspended` 是内部键，不会出现在条件变量里。

### 全局状态

全局状态通过 `global` 对象访问：

```text
(global.chapter == 3)
(global.dialogIntervalTicks > 1)
```

### 对话变量

玩家有进行中的对话时，顶层对话的字段作为变量提供：

| 变量 | 含义 |
| --- | --- |
| `currentScriptId` | 当前脚本 ID |
| `currentDialogId` | 当前对话 ID |
| `actionIndex` | 当前动作下标 |
| `actionTicks` | 当前动作已进行游戏刻，动作开始前不存在 |
| `sessionIntervalTicks` | 当前对话的动作间隔 |
| `sessionNextActionTick` | 下一个动作允许开始的游戏刻 |

没有进行中的对话时这些变量不存在，引用它们的表达式会因 `undefined` 参与运算而报错（比较 `== null` 除外）。

## 值与 JavaScript 类型

| 状态值类型 | 表达式里的类型 |
| --- | --- |
| 布尔 | `boolean` |
| 整数、小数 | `number` |
| 字符串 | `string` |
| 列表 | 数组 |
| 字符串键映射 | 对象 |

`update` 写入的值是布尔、数字或字符串；列表与映射出现在手工编辑的存档里。

## 出错行为

表达式抛错（引用不存在的变量、类型不匹配、语法非法）时的表现按出现位置不同：

| 位置 | 行为 |
| --- | --- |
| 动作条件 | 玩家被挂起，对话停止推进，错误写入日志，调试模式广播 |
| `check` 命令 | 命令失败，序列终止 |
| `st_predicate` | 该玩家不匹配选择器 |

挂起的恢复见[调试与排错](../tutorial/debugging.md)。

## 编辑器支持

VS Code 扩展把条件表达式交给内置的 TypeScript 语言服务处理：补全、悬停与诊断按 JavaScript 规则工作，状态键按 `update` 的用法推断类型并作为变量补全。详见[工具链](./toolchain.md)。
