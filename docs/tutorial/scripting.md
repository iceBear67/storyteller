# 对话脚本

这一章覆盖编写对话脚本需要的全部要素，分四节推进：先让对白好看，再把长故事拆成章节，然后让玩家做选择，最后让对话记住玩家的选择。四节共用一个场景：村口的 Bob 要向玩家提出一个委托，示例都写在上一章建的数据包里，脚本文件是 `data/village/dialog/quest.txt`。

完整文法参考见[脚本文法](../reference/syntax.md)，全部命令的参数见[脚本命令](../reference/commands.md)。

## 对白的写法

### 人物与配色

带人物名称的对白按「人物: 正文」的格式显示，人物名称按第一次出现的顺序分配颜色，同一人物始终同色；正文为白色。不带人物名称的行作为旁白显示，整体灰色：

```text
Bob: 欢迎来到灰松村。
远处传来几声犬吠。
```

### 颜色代码

正文里用 `&` 加颜色代码改变后续文字的样式。颜色代码有 `&0` 到 `&f` 十六个颜色，`&k` 到 `&o` 是混淆、粗体、删除线、下划线、斜体，`&r` 恢复默认：

```text
Bob: &e金色的字&r，后面恢复默认。
```

代码不区分大小写。`&` 没有转义写法，后面跟合法颜色代码时一定按颜色符号解析；跟其他字符时按普通文本保留。颜色代码只影响当前这一行。

### 注释与转义

行首的 `#` 是整行注释，不产生动作。正文里第一个未转义的 `#` 之后是行尾注释，会被截断。要显示字面的 `#` 写成 `\#`，要显示字面的 `$` 写成 `\$`：

```text
# 这是注释，不会显示
Bob: 价格是 \#1 号。    # 这是行尾注释
Bob: 一共 \$5。
```

### 内联命令

正文里用 `$命令$` 把命令的输出拼进对白。命令在显示这一行时执行一次，产生的文本按顺序拼接。最常用的是 `get`，读取一个状态值：

先用聊天栏设一个状态（需要管理员权限）：

```text
/storyteller:update name 史蒂夫
```

然后写进对白：

```text
Bob: 你好，$get name$！一路辛苦了。
```

`$` 与 `$` 之间不能为空。流程命令（`expect`、`check`、`update`、`goto`、`delete`、`return`、`wait`）不能写进内联命令，`get` 可以。

### 观众

对白默认只发送给触发对话的玩家。在正文末尾写一个前面带空白的 `@` 选择器，可以指定接收文本的玩家：

```text
Bob: 都过来听听。 @a[distance=..10]
```

同一行还有点击命令时，观众选择器写在点击命令之前，点击命令右侧的选择器属于点击命令：

```text
Bob: 都过来听听。 @a[distance=..10] [goto next] @s
```

观众只接收文本，条件求值、命令执行与对话推进仍由触发对话的玩家完成。省略观众选择器时使用数据包里的 `data/storyteller/settings.json`：

```json
{
  "defaultAudienceSelector": "@s"
}
```

## 分支与子对话

### 分界线

`--- 名称` 单独一行，从这行开始一个新的子对话。第一个分界线之前的动作是默认对话，名字与脚本 ID 相同。子对话名称不能为空，不能包含空白或冒号，也不能与脚本 ID 或已有子对话重名：

```text
Bob: 默认对话里的动作。

--- offer
Bob: 子对话里的动作。
```

对话引用 `脚本:对话` 里的「对话」指的就是分界线名称或默认对话。

### goto

`goto 引用` 把目标对话压到当前对话前面：原对话暂停，目标对话播完弹出后，原对话从下一条动作继续。引用省略脚本名时使用当前对话所在的脚本：

```text
--- offer
Bob: 委托的事情是这样的……
[goto detail]
Bob: 详情说完之后，这里继续。

--- detail
Bob: 细节都在这里。
```

`goto` 写进命令动作或点击命令里执行，也可以直接在聊天栏执行。

### expect

`expect 引用 引用...` 限定当前对话结束前只能进入列出的对话，用于收紧玩家能走的分支。列表为空时取消限制：

```text
[expect accepted rejected]
```

### return

`return` 弹出当前对话，回到下层对话；`return all` 清空全部对话。同一条命令序列里写在 `return` 之后的命令不会执行。要执行原版的 `return` 命令，写成 `minecraft:return` 或放在 `execute ... run` 之后。

### 命令动作

一行 `[命令序列]` 是独立的命令动作。条件通过后，每个游戏刻执行一条命令，整个序列执行完再进入下一个动作：

```text
Bob: 这是给你的路费。
[give @s bread 3]
Bob: 拿好，路上吃。
```

命令序列用 `;` 分隔，空命令会报错；命令里的字面分号写成 `\;`。方括号后可以写选择器（默认 `@s`），序列会对每个匹配的实体执行一遍。脚本里的命令以管理员权限执行。动作行也可以带条件：

```text
(chapter >= 2) [give @s diamond 1]
```

## 玩家选择

### 点击命令

对白正文以 `[命令序列]` 结尾时，这段文本可以点击，点击后立即执行整条序列。配合 `goto` 就做出选项：

```text
Bob: 愿意接下这个委托吗？
&f▶ 接受 [goto quest:accepted]
&f▶ 婉拒 [goto quest:rejected]
[wait]
```

选项列表的写法：每行对白带一个点击命令，逐行显示，玩家点哪行就进哪个分支。末尾的 `[wait]` 让对话停在原地等玩家点击（`wait` 是动作关键字，后面细讲）。

### 一次性 token 与 repeatable

每个玩家对同一个点击命令默认只能成功使用一次，防止重复点击重复生效。用 `repeatable` 作为序列的第一条命令可以解除限制，适合「再听一遍」这类选项：

```text
&f▶ 再听一遍 [repeatable; goto offer]
```

`repeatable` 只能用在点击命令里，后面必须至少跟一条命令。

### wait

`wait` 写在命令序列里，加载脚本时会被拆成独立的等待动作：

- `[wait]`：永久等待，直到玩家点击选项或对话被其他方式推进；
- `[wait 40]`：等待 40 游戏刻后继续。

`wait` 之后写在同一条序列里的命令会在等待结束后继续执行，每个等待动作各自保留原条件。`wait` 不能出现在点击命令里。

### 用 expect 收紧选项

选项展示后用 `expect` 限定允许进入的对话，玩家点击未列出的引用会收到报错：

```text
Bob: 愿意接下这个委托吗？
&f▶ 接受 [goto quest:accepted]
&f▶ 婉拒 [goto quest:rejected]
[expect accepted rejected]
[wait]
```

## 状态与条件

### 读写状态

状态是键值对，分玩家状态与全局状态两种。脚本命令 `update`、`get`、`delete` 负责读写：

```text
Bob: 那就这么说定了。
[update quest_accepted true]
Bob: 我等你回来。
```

- `update 键 值` 写入当前玩家的状态；`update global 键 值` 写入全局状态；
- 值是键名之后的全部文本，可以包含空格；要写字面分号用 `\;`；首尾成对的引号会被去掉；
- 值按字面量解析：`true` / `false` 是布尔，整数与小数是数字，其余是字符串；
- `get 键` 把值作为文本发给玩家，放在内联命令里可以拼进对白；
- `delete 键` 删除键值对，键不存在时忽略。

`dialogStack`、`dialogScriptChecksum`、`dialogSuspended` 是内部键，不能读写。

### 动作条件

动作行以 `(表达式)` 开头，在动作开始时求值一次，结果为假就跳过整个动作。表达式里玩家状态的键名直接作为变量，全局状态通过 `global` 对象访问：

```text
(quest_accepted == true) Bob: 任务办得怎么样了？
(quest_accepted != true) Bob: 还在考虑吗？
```

表达式用 JavaScript 求值，必须返回布尔值。可用的全部变量见[条件表达式](../reference/conditions.md)。

### check

`check 表达式` 用在命令序列里，结果为假值时这条序列立即终止，后面的命令不再执行。通常放在序列开头做前提条件：

```text
[check quest_accepted == true; give @s diamond 1]
```

### 全局状态与节奏

全局状态对全部玩家共享。除了存放剧情进度，还有一个内置键控制对话节奏：`dialogIntervalTicks` 是两个动作之间的间隔游戏刻数，默认 1。压入对话时读取当时的值：

```text
[update global dialogIntervalTicks 2]
```

这之后进入的对话会以 2 游戏刻的间隔推进。改成 0 可以让对白连续播放。

### 进度存档

玩家状态保存在 `data/storyteller/players/<玩家UUID>.nbt`，全局状态保存在 `data/storyteller/global.nbt`，在世界保存、服务器停止、玩家断线时写入。玩家中途下线再上线，对话会从上次的位置继续。

对话进度与脚本内容绑定：脚本被修改并重载后，所有玩家进行中的对话会清空，从新脚本重新开始；内容没有变化时，重载与服务器重启都保留进度。`/storyteller:reset` 让执行命令的玩家立即回到没有对话的状态。

### 用状态改变对白

状态加条件就能让同一个场景对不同玩家显示不同内容：

```text
--- offer
(quest_accepted == true) Bob: 任务办得怎么样了？
(quest_accepted == false) Bob: 上次你不是拒绝了吗？再考虑考虑？
(quest_accepted != true && quest_accepted != false) Bob: 愿意接下这个委托吗？
&f▶ 接受 [update quest_accepted true; goto quest:accepted]
&f▶ 婉拒 [update quest_accepted false; goto quest:rejected]
[expect accepted rejected]
[wait]
```

玩家状态还能进入任意原版命令，见[跟原版命令交互](./vanilla-commands.md)。
