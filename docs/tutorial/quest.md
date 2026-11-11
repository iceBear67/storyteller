# 实战教程：村口任务

这一章用前面五章的全部能力，从零搭一条完整的任务线：玩家走进村口，自动看到一段对白；村长 Bob 提出委托，玩家选择接受或拒绝；接受后去瞭望塔调查，回来向 Bob 复命领奖。每一步做完都能进游戏验证。

沿用[安装与第一段对话](./setup.md)建好的 `village` 数据包，两个脚本文件都在 `data/village/dialog/` 下。

## 任务设计

先想清楚流程，再写脚本：

```text
走进村口 ──触发──▶ intro（村口对白）
                      │
                      ▼
                    offer（Bob 提出委托）
                   ╱            ╲
               婉拒              接受
                │                 │
                ▼                 ▼
            rejected          accepted（去东南瞭望塔）
                                   │ 走进瞭望塔区域
                                   ▼
                          quest_done = true，回村找 Bob
                                   │ 右键 Bob
                                   ▼
                          offer（复命分支）→ 钻石奖励
```

对白与选择写在 `intro.txt` 与 `quest.txt` 两个脚本里，入口、Bob 与瞭望塔用三个触发实体接进地图。

## 写脚本

### intro.txt：村口对白

```text
远处的群山还盖着晨雾。
Bob: 又来了一位旅人，欢迎来到灰松村。
Bob: 我叫 Bob，是这里的村长。
[goto quest:offer]
```

第一行没有人物名称，作为旁白显示。最后一行是命令动作，对白播完自动进入 `quest.txt` 的 `offer`。

### quest.txt：委托与选择

```text
--- offer
(quest_done == true) Bob: 你从瞭望塔回来了！
(quest_done == true && quest_rewarded != true) [update quest_rewarded true; give @s diamond 1]
(quest_done == true && quest_rewarded != true) Bob: 这颗钻石是村里的谢礼，收下吧。
(quest_done == true && quest_rewarded == true) Bob: 你为村子做的事，大家都记着。
(quest_done == true) [tellraw @a[st_predicate="quest_accepted == true && quest_done == true"] {"text":"(公告)瞭望塔的调查完成了。"}]
(quest_done == true) [return]
(quest_done != true && quest_accepted == true) Bob: 瞭望塔在村子东南的高地上，看完就回来。
(quest_done != true && quest_accepted == true) [return]
(quest_accepted == false) Bob: 上次说的那件事，再考虑考虑？
(quest_accepted == null) Bob: 东边瞭望塔的守卫好几天没消息了，能替我去看一眼吗？
(quest_accepted == null) &f▶ 婉拒 [update quest_accepted false; goto quest:rejected]
(quest_accepted != true) &f▶ 接受 [update quest_accepted true; goto quest:accepted]

--- accepted
Bob: 太好了！瞭望塔在村子东南的高地上。
Bob: 看一眼就回来，路上小心野狼。

--- rejected
Bob: 也罢，等你想清楚了再来找我。
```

`offer` 是同一个对话的四种状态，用条件按玩家当前进度显示对应的内容：

- **复命**（`quest_done == true`）：奖励只发一次，用 `quest_rewarded` 挡住重复发放；最后的 `[return]` 结束对话。
- **进行中**（已接受未完成）：提醒一句后 `[return]`。
- **婉拒过**：劝说一句，保留「接受」选项。
- **初次见面**：完整提出委托，给出两个选项。

选项放在对话末尾，后面不需要 `wait`：对话结束后，选项文本仍然可以点击，玩家点哪个就进哪个分支。点过的选项对这个玩家失效，防止重复触发。

「接受」用 `quest_accepted != true` 而没有用 `== null`，这样婉拒过的玩家也能通过这一行重新接受。

### 手动验证

进游戏执行 `/reload`，然后手动过一遍流程：

```text
/storyteller:reset
/storyteller:goto intro:intro
```

依次验证：选择「婉拒」后重新 `goto quest:offer` 看劝说分支；选择「接受」后设置 `quest_done` 再看复命分支：

```text
/storyteller:update quest_done true
/storyteller:goto quest:offer
```

领奖后再进一次，确认钻石只发一次。验证完 `/storyteller:reset` 清干净。

## 放进地图

任务需要三个触发点：村口、Bob、瞭望塔。坐标按你的地图调整。

### 村口：走进去自动开始

```text
/summon storyteller:collision_trigger 0 64 0 {x1:-4.0d,y1:0.0d,z1:-4.0d,x2:4.0d,y2:3.0d,z2:4.0d,enter:"check quest_accepted == null; goto intro:intro",leave:""}
```

`check` 保证只对还没接过委托的玩家触发：接过的玩家再进村不会重看对白。

### Bob：右键对话

先放一个 NPC 外观——用标记模式（`Marker`）的盔甲架，它没有判定，玩家右键命中的就是触发实体：

```text
/summon minecraft:armor_stand 100 64 -30 {Marker:1b,ShowArms:1b,NoBasePlate:1b}
/summon storyteller:interact_trigger 100 64 -30 {runInteract:"goto quest:offer",runAttack:"goto quest:rejected"}
```

右键 Bob 进入 `offer`，对话会按玩家进度自动显示对应分支；攻击 Bob 会得到一句拒绝的台词。

### 瞭望塔：调查点

```text
/summon storyteller:collision_trigger 120 70 -80 {x1:-2.0d,y1:0.0d,z1:-2.0d,x2:2.0d,y2:3.0d,z2:2.0d,enter:"check quest_accepted == true; check quest_done == null; update quest_done true; tellraw @s {\"text\":\"你查看了瞭望塔的四周。\"}",leave:""}
```

两个 `check` 连用：只有接了委托且还没调查过的玩家才会推进任务。`update` 写入完成状态，`tellraw` 给玩家一句反馈。

## 全流程验证

```text
/storyteller:reset
```

然后完整走一遍：

1. 走进村口，`intro` 自动开始，播完进入 `offer`；
2. 点「婉拒」，Bob 说等你想清楚了再来；
3. 右键 Bob，看到劝说分支，点「接受」；
4. 走到瞭望塔区域，收到调查反馈；
5. 回村右键 Bob 复命，拿到钻石，收到公告；
6. 再右键 Bob，确认钻石不会重复发放。

中途随时可以用调试命令检查进度：

```text
/st debug trace
/st debug dump
```

`trace` 显示当前对话进行到哪个动作，`dump` 显示 `quest_accepted`、`quest_done`、`quest_rewarded` 这些状态键的当前值。

## 继续扩展

这条任务线可以直接加长：

- 在 `accepted` 之后加一个「寻找物品」环节：放一个 `block_trigger`，破坏指定方块写入新状态键；
- 用 `update global dialogIntervalTicks` 调整紧急桥段的节奏；
- 用 `expect` 把一段剧情锁死在指定的几个对话里；
- 复命后 `goto` 一段收尾对话，把任务串成章节。
