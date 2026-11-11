# 跟原版命令交互

Storyteller 的玩家状态可以进入任意原版命令。模组向原版实体选择器注册了三个字段：`st_key`、`st_value` 与 `st_predicate`。任何接受实体选择器的命令都能用它们按状态筛选玩家。

## st_key：按键名筛选

`st_key=<键名>` 匹配玩家状态里存在这个键的玩家：

```text
tellraw @a[st_key=quest_accepted] {"text":"你有任务在身。"}
```

这条命令把消息发给所有接过委托的玩家，没有这个键的玩家不会收到。

## st_value：按值筛选

`st_value=<值>` 匹配状态值转成文本后与给定字面量相等的玩家。它必须写在同一个选择器里 `st_key` 的后面，两边配合使用：

```text
give @a[st_key=chapter,st_value=2] diamond 1
```

这条命令给所有 `chapter` 值为 `2` 的玩家发一颗钻石。值是字符串时按字面量比较；值里包含空格或逗号时用引号包裹。

## st_predicate：按表达式筛选

`st_predicate=<表达式>` 对每个玩家求值条件表达式，结果为真才匹配。表达式里的变量与对话脚本的动作条件相同：玩家状态的键名直接作为变量，全局状态通过 `global` 对象访问：

```text
execute if entity @a[st_predicate="quest_accepted == true"] run say 有人接了委托
tp @a[st_predicate="chapter == 1"] 100 64 -30
```

表达式包含空格、逗号或比较运算符时用引号包裹。

## 规则

- 三个字段只匹配玩家，其他实体一律不匹配；
- `st_value` 必须写在同一个选择器里 `st_key` 之后，写在前面会解析失败；
- `st_key` 拒绝内部状态键（`dialogStack`、`dialogScriptChecksum`、`dialogSuspended`）；
- 每个字段在同一条命令里只能出现一次。

## 在对话脚本里使用

这三个字段是原版选择器的一部分，所以直接写进对话脚本的命令动作或点击命令即可。比如委托完成后，给所有完成过任务的玩家发奖励：

```text
Bob: 干得漂亮！
[give @a[st_key=quest_done,st_value=true] diamond 1]
Bob: 收下吧，村里人都感谢你。
```
