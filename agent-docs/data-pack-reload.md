# 数据包重载与跨服重启的加载行为

本文记录 `storyteller:dialog_scripts` 这个服务端数据包重载监听器在以下场景中的实际行为：游戏内 `/reload`、服务器重启、玩家断线重连、加载到失效对话引用时的处理方式，以及 `/storyteller:reset` 的作用范围。

## 参与组件

| 组件 | 位置 | 职责 |
|---|---|---|
| `DialogScriptRegistry` | `dialog/DialogScriptRegistry.kt` | 保存原始与处理后脚本、校验和的不可变快照，执行 pass，作为服务端数据包重载监听器，触发 `DialogScriptReloadEvent` |
| `DialogScriptPass` | `dialog/pass/DialogScriptPass.kt` | 脚本后处理接口，按 priority 从高到低执行 |
| `WaitActionPass` | `dialog/pass/WaitActionPass.kt` | 拆分 `wait`，截断 `return` 之后的命令 |
| `CommandTokenPass` | `dialog/pass/CommandTokenPass.kt` | 把原始命令调用替换成 token 形式 |
| `CommandTokenRegistry` | `dialog/CommandTokenRegistry.kt` | 保存 token 到 `CommandCall` 的映射与每个玩家允许使用的 token 集合，监听重载事件清空缓存 |
| `DialogScriptChecksum` | `dialog/DialogScriptChecksum.kt` | 将解析后的脚本表规范化为字节流并计算 SHA-256 |
| `DialogScriptReloadEvent` | `dialog/DialogScriptReloadEvent.kt` | 向其他组件传递重载前后的校验和 |
| `StateManager` | `state/StateManager.kt` | 持有玩家状态与全局状态，在玩家加入与脚本重载时校验玩家状态里的脚本版本 |
| `PlayerState` | `PlayerState.kt` | 玩家状态，`dialogStack` 键名保存 frame 列表，`dialogScriptChecksum` 键名保存玩家使用的脚本版本，`dialogSuspended` 保存挂起标记 |
| `DialogFrameCodec` | `state/DialogFrameCodec.kt` | 单个 frame 与 NBT 的相互转换 |
| `DialogDirector` | `dialog/session/DialogDirector.kt` | frame 列表的压入、弹出与顶层 frame 读取 |
| `DialogSessionRunner` | `dialog/session/DialogSessionRunner.kt` | 每个服务器 tick 推进每个玩家的顶层 frame |
| `ActionCommands` | `commands/ActionCommands.kt` | 承担点击文本里的 `storyteller:run_action <token>` |
| `ScriptCommands` | `commands/ScriptCommands.kt` | 七个脚本命令与 `/storyteller:reset` |

## 脚本版本与校验和

`DialogScriptRegistry` 为当前脚本表计算一个内容校验和：

- 按脚本 ID 排序，子对话按对话 ID 排序。
- 每个脚本写入脚本 ID、默认对话、全部子对话。
- 每个对话写入对话 ID 与动作列表，动作保持原始顺序。
- 每个动作写入类型标记与全部字段：条件、人物、文本片段、点击命令、命令序列、选择器、等待时限。
- 字符串与列表使用长度前缀，字节流开头有格式版本标记。
- 最终用 SHA-256 计算并保存为十六进制字符串。

校验和只关心解析后的脚本内容。注释、空行、缩进变化不改变校验和；脚本 ID、对话 ID、动作顺序、条件、文本或命令发生变化时会改变校验和。

`DialogScriptRegistry` 用一个不可变快照保存原始脚本、处理后脚本与校验和：

```kotlin
private data class Snapshot(
    val rawScripts: Map<String, DialogScript>,
    val scripts: Map<String, DialogScript>,
    val checksum: String,
)
```

`rawScripts` 用于计算校验和与重新执行 pass，`scripts` 是运行期使用的处理后脚本。`apply` 与 `register` 先在局部构建 token 表、执行 pass、计算校验和；任何一步抛出异常时，旧脚本快照与旧 token 表保持不变。

## 后处理 pass

`DialogParser` 返回原始解析结果：命令动作带有 `CommandInvocation.Raw`，点击命令同样如此。`DialogScriptRegistry` 安装脚本表时按 priority 从高到低执行 pass：

1. `WaitActionPass`（priority 100）：截断 `return` 之后的命令，把命令序列里的 `wait` 拆成 `WaitAction`，并截断点击命令里 `return` 之后的命令。
2. `CommandTokenPass`（priority 0）：把 `CommandInvocation.Raw` 替换成 `CommandInvocation.Tokenized`。`selector` 与命令序列注册到 `CommandTokenRegistry`，动作只保留 token 与条件。

独立命令动作与点击命令都会经过 token pass。内联命令是文本片段，不参与 token 化。

## 加载流程

### 1. 模组初始化

`Storyteller` 构造 `StateManager` 与 `DialogDirector`，随后在 `init` 块里构造 `DialogSessionRunner`、`ScriptCommands` 与 `ActionCommands`。`ScriptCommands` 与 `ActionCommands` 在构造函数里注册 Minecraft 命令。

`Storyteller` 创建 `DialogScriptRegistry` 实例，并在 `onInitialize` 把它注册为 `PackType.SERVER_DATA` 的重载监听器，注册 ID 是 `storyteller:dialog_scripts`。此时脚本表为空，快照里的校验和是空脚本表的校验和。

`StateManager` 通过构造函数接收 `DialogScriptRegistry` 实例，并订阅该实例的 `reloadEvent`。

### 2. 数据包重载

重载由 Minecraft 的资源重载流程驱动，分为准备与提交两个阶段。

准备阶段在后台执行 `DialogScriptRegistry.prepare`，调用 `load`：

1. 读取 `data/<命名空间>/dialog/` 下全部 `.txt` 文件，允许子目录。
2. 脚本 ID 是文件路径相对于 `dialog/` 去掉 `.txt` 后缀的部分，命名空间不参与 ID。
3. 每个文件交给 `DialogParser.parseDialogs` 解析。
4. 不同命名空间产生相同 ID 时，`check(loaded.put(id, script) == null)` 抛出 `IllegalStateException`。
5. 解析错误抛出 `DialogParseException`，异常里带行号。

提交阶段在服务器线程执行 `DialogScriptRegistry.apply`：

1. 计算原始脚本表的校验和。
2. 创建 `CommandTokenRegistry.Builder`，按优先级执行 pass。
3. 触发 `DialogScriptReloadEvent`，参数是重载前与重载后的校验和。`CommandTokenRegistry` 的监听器清空旧 token 表与已用集合，校验和变化时同时清空每个玩家的允许集合；`StateManager` 重新校验在线玩家。
4. 调用 `CommandTokenRegistry.install` 安装新的 token 表。
5. 用新快照替换旧的脚本快照。

事件在脚本快照与 token 表安装之前触发。安装发生在事件之后，服务器线程上没有 tick 穿插。事件监听器抛异常时，安装不会执行。

### 3. 玩家加入

`StateManager` 订阅 `ServerPlayConnectionEvents.JOIN`。玩家加入时：

1. `playerState(player)` 读取世界存档里的 `data/storyteller/players/<uuid>.nbt`。
2. `TypedKeyMapCodec.decodeInto` 解码全部键值，`dialogStack` 的每个元素交给 `DialogFrameCodec.decode`。
3. `PlayerState.useScriptChecksum(dialogScripts.scriptsChecksum)` 比较玩家状态里保存的校验和与当前脚本的校验和。
4. 一致时保留 frame 列表。
5. 不一致时清空 frame 列表，并把当前校验和写入玩家状态。

新玩家没有校验和键名，`useScriptChecksum` 直接写入当前校验和，frame 列表保持为空。

玩家状态在加入阶段加载，不再等到第一次对话。损坏的玩家存档会在加入阶段抛出异常。

### 4. 会话推进

每个服务器 tick 的结尾，`DialogSessionRunner` 对每个在线玩家读取顶层 frame：

```kotlin
val frame = director.currentFrame(state) ?: continue
val dialog = checkNotNull(dialogScripts.get(frame.scriptId)?.resolve(frame.dialogId))
```

`DialogDirector.currentFrame` 只返回 `state.currentFrame`，不再比较版本，也不再查询对话是否存在。版本由加入与重载两个入口保证；脚本与创建 frame 时一致时，对话必然存在。状态被手工破坏时，`checkNotNull` 会快速失败。

`pushDialog` 与 `popDialog` 不再调用版本检查。`pushDialog` 直接用当前顶层 frame 的 `allowedContinuation` 做限制判断。

### 5. 保存

只有 dirty 的状态才会写盘。保存时机是服务器 `BEFORE_SAVE`、服务器停止、玩家断线。写入先写临时文件，再用 `ATOMIC_MOVE` 替换目标文件。玩家状态在断线时从内存移除并保存；全局状态在服务器启动时加载，在服务器停止与保存时写入。

## 同一进程内的 `/reload`

### 内容一致

`apply` 计算出的校验和与重载前相同。`DialogScriptReloadEvent` 仍然触发，`StateManager` 发现前后校验和一致后直接返回。在线玩家的 frame 列表保留；之后加入的玩家用当前校验和校验，也会保留 frame。

### 内容变化

前后校验和不同。`StateManager` 遍历已经加载的玩家状态，对每个状态调用 `useScriptChecksum(currentChecksum)`：

- 校验和不同，frame 列表被清空，当前校验和写入状态。
- 离线玩家不在 `playerStates` 里，等他们加入时处理。

清空结果会标记 dirty，并在下一次保存时写入文件。

### 旧点击文本

对白文本的点击事件保存的是 `storyteller:run_action <token>`。token 由 `CommandCall` 的 `selector`、`commands` 与 repeatable 标记计算 SHA-256 得到，取前 32 个十六进制字符；条件不属于 token。`CommandTokenPass` 在每次安装时重新注册 token。`CommandTokenRegistry` 监听 `DialogScriptReloadEvent`，事件触发时清空旧表，随后由 `DialogScriptRegistry` 安装新表。

点击命令生成的 token 默认对每个玩家只能成功消耗一次。`CommandTokenRegistry` 保存已经消耗的玩家与 token 组合，`ActionCommands` 在 `checkCommandScope` 校验通过之后调用 `consume`；同一个玩家再次使用已经消耗的 token 时抛出 `Action token was already used`，其他玩家不受影响。命令序列以 `repeatable` 开头时，`CommandTokenPass` 去掉修饰符并以可重复方式注册 token，这类 token 不进入已用集合。`repeatable` 只允许出现在点击命令里，写在独立命令动作里时脚本加载失败。

`checkCommandScope` 的校验范围是 `CommandTokenRegistry` 为玩家维护的允许集合。`DialogSessionRunner` 在对白动作开始时把它的点击 token 加入 frame 的 `poppedCommands`，同时加入允许集合。`DialogDirector` 压入对话时用新 frame 的 `poppedCommands` 替换允许集合，新对话开始时这个列表通常为空；弹出对话时用下层 frame 的 `poppedCommands` 替换，弹出最后一层时保留结束对话的 token，因此结尾的对白文本不需要额外 `wait` 也能点击。对话推进之后，旧文本的普通 token 会进入消耗检查并报告已使用，repeatable token 可以继续执行。命令语法错误始终转换为玩家消息或内联文本，不受 `checkCommandScope` 影响。

- 新旧脚本里完全相同的命令调用会得到相同 token，未被消耗的玩家仍然可以执行。
- `DialogScriptReloadEvent` 触发时清空已用集合，随后由 `DialogScriptRegistry` 安装新的 token 表。服务器重启后已用集合同样为空。校验和变化时允许集合一并清空；校验和一致时保留，安装新表时移除新表里不存在的 token。
- 内容改变、动作删除、动作移动到别的脚本且内容不同的 token 不再存在于新表里。玩家点击旧文本时，`ActionCommands` 找不到 token，抛出 `Unknown action token` 的命令异常。
- 独立命令动作与点击命令都会生成 token。独立命令动作的 token 由 `DialogSessionRunner` 查表执行，不经过 `run_action`，因此不消耗已用状态。内联命令是文本片段，不生成 token。

### `expect` 与 `goto`

这两个命令在执行时解析引用，不在解析脚本时解析。

- 引用省略脚本名时，使用执行命令时顶层 frame 的 `scriptId`。
- 内容变化清空 frame 后，顶层 frame 不存在。此时省略脚本名的 `goto` 与 `expect` 会因为没有默认脚本而报错。
- 带显式脚本名的 `goto` 可以在 frame 列表为空时直接压入目标对话。
- `expect` 先取顶层 frame 的脚本名。frame 被清空后这一步返回 `null`，命令抛出 `No active dialog frame`，不会设置限制。

### 状态值与条件变量

`update`、`delete`、`get` 操作的是 `PlayerState` 与全局 `TypedKeyMap`，与脚本重载无关。这些键值在重载后保留。

`PlayerState.namedValues()` 会把顶层 frame 的字段展开成条件变量：`currentScriptId`、`currentDialogId`、`actionIndex`、`actionTicks`（仅非空时）、`sessionIntervalTicks`、`sessionNextActionTick`。脚本校验和与 `dialogSuspended` 不会出现在条件变量里，也不能通过 `get`、`update`、`delete` 访问。frame 列表被清空后，前面这些变量从条件作用域里消失。

### 实体选择器字段

`@a[...]`、`@e[...]` 等实体选择器额外提供三个字段：

- `st_key=<键名>`：匹配玩家状态里存在该键的玩家。
- `st_value=<值>`：必须与同一选择器里前面的 `st_key` 搭配，按状态值的 `toString()` 结果做字面量比较。
- `st_predicate=<表达式>`：对玩家求值条件表达式，变量与动作条件相同。

值包含空格、逗号或比较运算符时要用引号，例如 `@a[st_predicate="visits > 2"]`。`st_value` 出现在 `st_key` 之前会解析失败。`st_key` 不能使用 `dialogStack`、`dialogScriptChecksum`、`dialogSuspended`。`st_key` 与 `st_value` 只检查真实保存的玩家状态键；`currentScriptId` 等顶层 frame 变量用 `st_predicate` 读取。非玩家实体不会匹配这三个字段。

### 离线玩家

重载时离线的玩家，存档文件里仍然保留旧校验和与 frame。重新连接后，`onJoin` 比较校验和并清空 frame。清空后状态被标记 dirty，下一次保存写入空状态。文件在下一次保存前不会变化。

## 跨服务器重启

玩家状态里的 `dialogScriptChecksum` 会写入 NBT。新进程启动后：

1. 数据包重新加载，`DialogScriptChecksum.compute` 得到新的校验和。
2. 内容没有变化时，新校验和与玩家状态里的值相同。
3. 玩家加入时 `useScriptChecksum` 直接返回，frame 列表保留，对话从上次的动作继续。
4. 内容发生变化时，校验和不同，玩家加入时 frame 列表清空。

因此 frame 列表可以跨进程保留，前提是脚本表内容一致。普通玩家状态键与全局状态键继续按原有方式保留。

同一进程内玩家断线重连，中间没有发生内容变化时，校验和一致，frame 保留。

## 加载失败路径

### 准备阶段失败

准备阶段抛出异常时，资源重载框架不会执行提交阶段。`DialogScriptRegistry` 保留旧快照，在线对话继续使用旧脚本与旧校验和。常见原因：

- 某个 `.txt` 文件的文法错误，抛出 `DialogParseException`。
- 不同命名空间产生相同脚本 ID，抛出 `IllegalStateException`。
- 资源读取失败。

失败会由资源重载机制记录到日志，数据包不会部分生效。

### 快照构建失败

`apply` 在局部构建 token 表与校验和，并执行 pass。token 冲突、pass 异常、校验和计算异常都会在事件触发与安装之前抛出。旧脚本快照与旧 token 表保持不变，`DialogScriptReloadEvent` 不会触发。在线对话继续使用旧脚本。

### 状态解码失败

`DialogFrameCodec.decode` 只校验结构。缺字段、字段类型错误会抛出 `IllegalArgumentException`。`StateManager.onJoin` 在加载状态时没有捕获异常，损坏的玩家存档会让加入事件失败。

结构合法但数值异常的 frame 不会在解码阶段被发现：

- `actionIndex` 为负时，`DialogSessionRunner` 的 `actionIndex >= actions.size` 检查不会拦住它，随后 `dialog.actions[actionIndex]` 抛出下标越界。
- `actionTicks` 为负时，命令动作会用它作为命令下标，可能抛出下标越界。
- `intervalTicks` 为负只会让下一次动作的等待时间提前，不会抛异常。

校验和一致时脚本表与创建 frame 时相同，正常写入的 frame 不会出现这些问题；手工修改的存档仍然可能触发。

## `/storyteller:reset`

`/storyteller:reset` 是注册在 Minecraft 命令系统里的命令，作用于执行命令的玩家：

- 清空该玩家的 frame 列表。
- 清空该玩家在 `CommandTokenRegistry` 里的允许集合。
- 把该玩家状态里的校验和写成当前脚本的校验和。

它不参与脚本命令的裸名映射，与七个脚本命令分开注册。玩家可以用它主动结束当前对话并回到干净状态。

## 边缘情况清单

| 情况 | 行为 | 结果 |
|---|---|---|
| 数据包新增脚本 | 校验和变化 | 在线玩家在 `apply` 时清空 frame；离线玩家加入时清空 |
| 脚本内容修改，ID 不变 | 校验和变化 | 同上 |
| 脚本文件删除 | 校验和变化 | 同上 |
| 脚本文件改名或移动到别的目录 | 校验和变化 | frame 清空；引用旧 ID 的命令报错 |
| 子对话删除 | 校验和变化 | frame 清空 |
| 对话动作数量减少 | 校验和变化 | frame 清空，不会走到越界访问 |
| 新增命名空间但 ID 重复 | 准备阶段抛 `IllegalStateException` | 整个重载失败，旧快照继续使用 |
| 脚本文法错误 | 准备阶段抛 `DialogParseException` | 整个重载失败，旧快照继续使用 |
| token 冲突 | pass 执行阶段抛 `IllegalStateException` | 旧快照与旧 token 表继续使用，事件不触发 |
| 数据包内容完全一致的重载 | 校验和一致 | frame 保留 |
| 服务器重启，内容一致 | 玩家状态里的校验和匹配 | frame 保留 |
| 服务器重启，内容变化 | 校验和不匹配 | 玩家加入时 frame 清空 |
| 重载时玩家在线 | 事件立即校验 | 内容变化时 frame 清空 |
| 重载时玩家离线 | 文件保留旧 frame | 重新加入时按校验和清空或保留 |
| 旧点击文本的 token 仍存在 | 执行同样的 `CommandCall` | 命令可以执行 |
| 旧点击文本的 token 不存在 | `Unknown action token` | 命令报错，玩家看到错误信息 |
| `goto` 省略脚本名且 frame 已清空 | 没有默认脚本 | 命令报错 |
| `goto` 带显式脚本名且 frame 已清空 | 空列表允许压入 | 开始一个新对话 |
| `expect` 在 frame 已清空时执行 | `currentFrame` 返回 `null` | 命令报错 |
| `return` 在 frame 已清空时执行 | `currentFrame` 返回 `null` | 不带 `all` 时不做任何事；带 `all` 时清空 |
| `wait` 所在 frame 遇到内容变化 | 事件清空 frame | 等待结束 |
| 全局状态键 | 与脚本版本无关 | 跨重载、跨重启保留 |
| 玩家普通状态键 | 与脚本版本无关 | 跨重载、跨重启保留 |
| 条件依赖 `currentScriptId` 等变量 | frame 清空后变量消失 | 表达式结果可能改变 |
| 条件求值抛出异常 | 玩家被挂起 | 记录日志，调试模式广播；`/st debug suspend`、`/storyteller:reset` 或校验和变化后恢复 |
| 手工构造的 frame 带当前校验和但对话不存在 | `DialogSessionRunner` 解析对话时失败 | `checkNotNull` 快速失败 |

## 注意事项

### 给数据包作者

- 内容变化后，在线玩家的对话会在重载时立即结束，离线玩家在重新加入时结束。
- 内容一致时，重载不会清空 frame。
- 保持脚本 ID 与对话 ID 稳定。脚本 ID 来自文件路径，重命名文件或移动目录都会改变 ID。
- 不同命名空间不要使用相同的相对路径，否则整个重载会失败。
- 跨重启需要保留的普通剧情进度仍然可以写入玩家状态或全局状态；frame 列表在脚本内容一致时可以跨重启保留。
- 点击文本是临时的。内容变化后旧文本可能报 `Unknown action token`。
- `expect` 的引用在设置时不检查目标是否存在。内容变化会清空 frame，失效引用随之消失；内容一致时引用仍然有效。
- 测试重载时保留至少一个在线玩家，观察 frame 清空与点击文本的行为。

### 给服务器管理员

- 检查服务器日志里的数据包重载错误。准备阶段或快照构建阶段失败时旧脚本继续工作。
- 玩家存档里的 frame 会在校验和不匹配时自动清理，不需要手工删除 `dialogStack`。
- 玩家普通状态与全局状态不会被自动清理。数据包不再使用某个键名后，旧值仍会保留在存档里。
- `/storyteller:reset` 可以让单个玩家立刻回到没有 frame 的状态。

## 当前实现中的风险

- 校验和按整个脚本表计算。任意脚本变化都会清空所有玩家的 frame。
- 玩家状态在加入阶段加载，损坏的存档会让加入事件失败。
- 旧存档没有 `dialogScriptChecksum` 键名，玩家第一次加入时会清空一次 frame。
- 解码阶段不校验 `actionIndex`、`actionTicks`、`intervalTicks` 的范围。
- 校验和与 `dialogSuspended` 不会出现在条件变量里，也不能通过 `get`、`update`、`delete` 访问。这是有意保持的隔离。
- `DialogScriptChecksum` 的编码带格式版本标记。动作类型、字段或解析语义变化时需要递增该版本，否则旧 frame 可能在新脚本上继续使用。
- `DialogScriptReloadEvent` 在服务器线程触发，监听器直接修改在线玩家状态。事件发生在脚本快照与 token 表安装之前；监听器抛异常时安装不会执行，旧脚本快照保留，token 表可能已经被 `CommandTokenRegistry` 清空。
- 事件监听器读取 `CommandTokenRegistry` 时会看到清空后的表，新的 token 表在安装阶段写入。
- pass 管线当前固定为 `WaitActionPass` 与 `CommandTokenPass`。新增 pass 时需要确定它与这两个 pass 的优先顺序。
