# 触发实体

对话除了在聊天栏用 `goto` 开始，还能由地图里的行为触发。模组注册了三个不显示外观的触发实体，用原版 `/summon` 加 NBT 生成。事件发生时，实体以触发玩家为执行者运行命令，命令的写法与对话脚本一致：裸写的流程命令（`goto`、`expect`、`update`、`get`、`check`、`delete`、`return`、`wait`）会自动加上 `storyteller:` 前缀，权限为管理员级别，空字符串不执行任何命令。

## collision_trigger：走进区域

玩家进入或离开一个区域时运行命令。NBT 字段：

| 字段 | 含义 |
| --- | --- |
| `x1`、`y1`、`z1`、`x2`、`y2`、`z2` | 区域两个角点相对实体位置的偏移 |
| `enter` | 玩家进入区域时运行的命令 |
| `leave` | 玩家离开区域时运行的命令 |

实体每个游戏刻用玩家包围盒与区域求交。非旁观玩家进入区域时运行一次 `enter`，离开时运行一次 `leave`：

```text
/summon storyteller:collision_trigger 0 64 0 {x1:-3.0d,y1:0.0d,z1:-3.0d,x2:3.0d,y2:3.0d,z2:3.0d,enter:"goto intro:intro",leave:""}
```

这条命令在坐标 `(0, 64, 0)` 生成一个 6×3×6 的区域，玩家走进去时以该玩家为执行者运行 `goto intro:intro`。

配合 `check` 可以做成只触发一次的门：

```text
/summon storyteller:collision_trigger 0 64 0 {x1:-3.0d,y1:0.0d,z1:-3.0d,x2:3.0d,y2:3.0d,z2:3.0d,enter:"check quest_accepted == null; goto intro:intro",leave:""}
```

已经接过委托的玩家再走进区域时，`check` 失败，序列终止，对话不会重新开始。

区域内玩家的记录只保存在内存里：实体所在的区块重新加载后，仍在区域里的玩家会再次收到一次 `enter`；玩家断线时不会运行 `leave`。

## interact_trigger：右键与攻击

玩家与实体交互或攻击实体时运行命令。NBT 字段：

| 字段 | 含义 |
| --- | --- |
| `runInteract` | 玩家右键实体时运行的命令 |
| `runAttack` | 玩家攻击实体时运行的命令 |

```text
/summon storyteller:interact_trigger 100 65 -30 {runInteract:"goto quest:offer",runAttack:"goto quest:rejected"}
```

这个实体不显示外观，但有一个 1×1 的碰撞判定，右键或攻击它所在的位置即可触发。做 NPC 时，可以在同一位置放一个盔甲架做外观，玩家对着盔甲架的位置右键就会触发 `runInteract`。交互会阻止原版处理，实体不受伤；交互距离按原版规则检查，旁观玩家跳过。

## block_trigger：放置与破坏方块

玩家在实体所在的方块位置放置或破坏方块时运行命令。NBT 字段：

| 字段 | 含义 |
| --- | --- |
| `runPlace` | 方块被放置时运行的命令 |
| `runBreak` | 方块被破坏时运行的命令 |

```text
/summon storyteller:block_trigger 100 64 -30 {runPlace:"goto quest:blocked",runBreak:"goto quest:cleared"}
```

方块位置取实体的 `blockPosition()`，实体生成在哪个方块里就监听哪个方块。破坏事件在方块移除之后运行；放置事件只在手持方块物品、方块类型确实发生变化且新方块对应该物品时触发，并在服务器任务队列中运行。

## 调试棒与线框

执行 `/st debug` 开启调试后，加载中的触发实体会在世界里画出线框：碰撞区域、交互实体的包围盒与方块位置使用不同颜色。此时用调试棒与实体交互，会把实体的属性发到聊天栏，方便核对 NBT 字段；`collision_trigger` 与 `block_trigger` 在调试模式下也可以用调试棒选中。`block_trigger` 还支持直接对它所在的方块使用调试棒查看属性。

调试的更多用法见[调试与排错](./debugging.md)。
