# 脚本命令

七个脚本命令与 `/storyteller:reset` 的完整参数与语义。教程见[对话脚本](../tutorial/scripting.md)。

七个命令都注册在 Minecraft 命令系统里，全名带 `storyteller:` 前缀。写在对话脚本或触发实体里时可以裸写名字。

## 执行环境

**在对话脚本与触发实体里执行**（命令动作、点击命令、内联命令、触发命令）：命令以管理员权限执行，执行者是触发事件的玩家；选择器对每个匹配的实体执行一遍整条序列；裸写的命令名自动加上 `storyteller:` 前缀。

**在聊天栏直接执行**：权限各不相同：

| 命令 | 聊天栏权限 |
| --- | --- |
| `storyteller:expect` | 管理员 |
| `storyteller:update` | 管理员 |
| `storyteller:goto` | 管理员 |
| `storyteller:delete` | 管理员 |
| `storyteller:get` | 所有玩家 |
| `storyteller:check` | 所有玩家 |
| `storyteller:return` | 所有玩家 |
| `storyteller:reset` | 所有玩家 |

对话引用的格式是 `[脚本名:]对话名`。省略脚本名时使用当前对话所在的脚本；玩家没有任何进行中的对话时必须写全 `脚本名:对话名`。

## expect

```text
expect 引用 { 引用 }
```

替换当前对话的允许列表，设置后只能进入列表中的对话。引用列表为空时取消限制。每个引用都要求目标脚本与对话存在。没有进行中的对话时命令失败。

## check

```text
check 表达式
```

求值表达式，结果为 JavaScript 假值时命令失败，同一条命令序列里后续命令不再执行。通常放在序列开头作为前提条件。表达式变量与[条件表达式](./conditions.md)相同。

## update

```text
update [global] 键名 值
```

写入当前玩家的状态；带 `global` 时写入全局状态。值是键名之后的全部文本，可以包含空格，字面分号写成 `\;`。首尾成对的引号（单引号或双引号）会被去掉，然后按字面量解析：

| 写法 | 类型 |
| --- | --- |
| `true` / `false` | 布尔 |
| `42`、`-7` | 整数（超出 `Int` 范围时按 `Long`） |
| `3.14`、`1e3` | 小数 |
| 其余 | 字符串 |

整数或小数字面量非法或超出范围时命令失败。`dialogStack`、`dialogScriptChecksum`、`dialogSuspended` 是保留键，写入报错。

## get

```text
get [global] 键名
```

读取值并把文本发给命令来源。放在内联命令里可以把值拼进对白文本。键不存在时命令失败；保留键同样报错。

## goto

```text
goto 引用
```

把目标对话压到当前对话前面。目标对话结束弹出后，原对话从下一条动作继续。当前对话设置了 `expect` 限制且目标不在列表里时命令失败。玩家没有进行中的对话时，带完整脚本名的 `goto` 可以直接开始一个对话。

## delete

```text
delete [global] 键名
```

删除对应的键值对，键不存在时忽略。保留键报错。

## return

```text
return [all]
```

不带 `all` 时弹出当前对话；带 `all` 时清空全部对话。玩家没有进行中的对话时不做任何事。同一条命令序列里写在 `return` 之后的命令不会执行，加载脚本时会被截断。

## /storyteller:reset

```text
/storyteller:reset
```

让执行玩家回到没有对话的状态：清空对话 frame 列表、清空点击 token 的允许集合、把脚本校验和写成当前值。所有玩家可用，常用于重跑测试。

## /storyteller:run_action

```text
run_action <token>
```

点击对白文本时由客户端自动执行的内部命令，用 token 查回命令序列并执行。玩家直接输入没有意义：token 只对当前对话展示过的文本有效，且每个玩家对同一个 token 默认只能成功使用一次。`repeatable` 注册的 token 不受次数限制。

## 速查表

| 命令 | 语法 | 作用 |
| --- | --- | --- |
| `expect` | `expect 引用...` | 限定允许进入的对话 |
| `check` | `check 表达式` | 条件不满足时终止序列 |
| `update` | `update [global] 键 值` | 写入状态 |
| `get` | `get [global] 键` | 读取状态值 |
| `goto` | `goto 引用` | 进入对话 |
| `delete` | `delete [global] 键` | 删除状态键 |
| `return` | `return [all]` | 结束当前（或全部）对话 |
| `reset` | `/storyteller:reset` | 清空执行玩家的对话进度 |
