# Storyteller 代码审计报告

## 审计范围

- 仓库：`/home/icybear/IdeaProjects/storyteller`，分支 `master`，HEAD `2692c01`。
- 阅读范围：`src/main/kotlin` 全部生产代码、`src/test/kotlin` 全部测试、`docs/data-pack-reload.md`、`build.gradle.kts`、`gradle.properties`、`fabric.mod.json`、两个 mixin 配置。
- 测试执行：`gradle test`，16 个测试类、146 个用例、0 失败。
- 崩溃日志：`run/crash-reports/crash-2026-11-10_00.51.43-server.txt`。
- 库行为核对：Brigadier 1.3.11、Minecraft 26.3 反编译结果、Fabric 生命周期事件实现。涉及库行为的判断都读取了对应的类文件，没有只凭记忆推断。
- 每条问题都经过独立核实，核实内容包括调用方、触发条件、异常传播路径与现有测试能否发现。

## 严重问题

### 1. 打开世界时在初始保存阶段崩溃

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/state/StateManager.kt:45`、`:83`

**触发过程**：`StateManager` 在 `init` 中把 `saveDirty` 注册到 `BEFORE_SAVE`（第 45 行）。`playerDirectory` 与 `globalFile` 只在 `onServerStarted`（第 55 至 58 行）中赋值。集成服务器在 `IntegratedServer.initServer` 阶段调用 `saveEverything`，此时 `BEFORE_SAVE` 先于 `SERVER_STARTED` 触发。`saveDirty` 第 83 行在调用 `saveIfDirty` 之前读取 `globalFile`，直接抛出 `UninitializedPropertyAccessException`，世界加载中断。`saveIfDirty` 内部的 dirty 判断在读取 `globalFile` 之后，因此这个崩溃与是否有待保存内容无关。

**影响**：装配该模组后打开单人世界稳定崩溃，世界无法进入。`SERVER_STOPPING` 上注册的回调（第 46 行）随后再次触发同一个异常，崩溃日志中可以看到第二条记录。

**最小修改**：在 `saveDirty` 开头增加初始化检查，事件在 `SERVER_STARTED` 之前触发时直接返回。

```kotlin
private fun saveDirty() {
    if (!::globalFile.isInitialized) {
        return
    }
    saveIfDirty(globalFile, globalState)
    for ((uuid, state) in playerStates) {
        saveIfDirty(playerFile(uuid), state)
    }
}
```

这个检查同时覆盖 `BEFORE_SAVE` 与 `SERVER_STOPPING` 两个入口，不改变事件注册顺序，也不改变保存行为。

### 2. 非管理员玩家执行脚本命令时选择器权限检查失败

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/CommandExecutor.kt:14-15`、`:22-23`

**触发过程**：`CommandExecutor.run` 用 `player.createCommandSourceStack()` 构造命令来源，随后调用 `EntityArgument.entities().parse(StringReader(selector)).findEntities(source)` 解析选择器。`ServerPlayer.createCommandSourceStack` 使用玩家自己的权限集合，非管理员玩家的权限集合是 `LevelBasedPermissionSet.ALL`。Minecraft 26.3 的 `EntitySelector.findEntities` 会先调用 `checkPermissions`，只要选择器语法中出现 `@`（包括默认的 `@s`）并且来源没有 `commands/entity_selectors` 权限，就抛出 `ERROR_SELECTORS_NOT_ALLOWED`。这个权限在 `LevelBasedPermissionSet.hasPermission` 中要求 `GAMEMASTERS` 级别。

**影响**：

- 普通玩家触发命令动作时，选择器解析在游戏刻回调中抛出 `CommandSyntaxException`。`CommandExecutor`、`ActionRunner`、`DialogSessionRunner` 都不捕获该异常，服务器主循环接住后写出崩溃报告并停止服务器。
- 普通玩家点击带点击命令的对白时，异常由 `Commands.performCommand` 捕获并回显失败信息，点击命令不执行，但服务器继续运行。
- 即使脚本命令本身不涉及权限（例如 `[say hi]`），默认选择器 `@s` 仍然会触发这个检查，因此命令动作对非管理员玩家整体失效。
- 脚本中需要操作员权限的命令（例如 `give`、`tp`、`effect`）同样无法解析，因为 Brigadier 在解析阶段会按来源权限过滤命令节点。

**最小修改**：脚本命令由数据包作者编写，执行时使用脚本本身的权限。在 `CommandExecutor` 的两个 `run` 重载中，把来源提升到至少 `GAMEMASTER` 级别：

```kotlin
private fun scriptSource(player: ServerPlayer): CommandSourceStack =
    player.createCommandSourceStack()
        .withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER)
```

`withMaximumPermission` 会把玩家现有权限与给定权限集合合并，玩家实体保持不变，`playerOrException`、位置与朝向都不受影响。

`capture` 使用同一个来源构造方式，再替换命令来源对象：

```kotlin
val source = scriptSource(player).withSource(capturingSource)
```

`withSource` 会保留权限集合。内联命令与独立命令动作同样由脚本作者编写，使用相同的权限级别，可以避免 `$say hi$` 这类需要操作员权限的内联命令在普通玩家触发时解析失败并终止服务器。

### 3. `goto` 的参数类型拒绝文档中写明的引用形式

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/ScriptCommands.kt:74-81`

**触发过程**：`storyteller:goto` 的 `ref` 参数注册为 `StringArgumentType.word()`。Brigadier 1.3.11 的 `SINGLE_WORD` 解析走 `StringReader.readUnquotedString()`，允许的字符只有数字、ASCII 字母、下划线、连字符、点和加号。冒号与斜杠都不在允许集合内，引号也不会被读取。

而 `DialogParser` 的文法写明引用为 `[ 脚本名 ":" ] 对话名`，`parseDialogRef` 也按第一个冒号切分并支持带脚本名的形式。脚本 ID 来自文件路径，嵌套目录会产生包含斜杠的 ID（例如 `chapter/one`）。因此下面几种写法都会在解析参数时失败：

- `[goto chapter:ending]`
- `[goto other:sub]`
- `[goto chapter/one]`
- `[goto ":chapter"]`

`expect` 使用 `greedyString`，同一种引用写法在 `expect` 中可以正常使用，在 `goto` 中不可用，两个命令的行为不一致。

**影响**：使用文档化语法的脚本在命令动作执行时抛出 `CommandSyntaxException`。该异常没有被任何一层捕获，沿 `ActionRunner.tickCommand`、`DialogSessionRunner.tick`、`END_SERVER_TICK` 回调传播，服务器停止。`goto` 内部对 `parseDialogRef` 的 `IllegalArgumentException` 捕获不会生效，因为失败发生在 Brigadier 解析参数的阶段。

**最小修改**：把 `ref` 参数改为 `StringArgumentType.greedyString()`。`goto` 只接收一个引用，并且该参数位于命令末尾，`greedyString` 可以直接接受冒号、斜杠与空白，`parseDialogRef` 已经负责去掉首尾空白并校验对话名。

## 高优先级问题

### 4. 脚本命令没有权限要求

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/ScriptCommands.kt:47-111`

**触发过程**：`expect`、`update`、`get`、`goto`、`delete`、`return`、`reset` 七个命令都没有设置 `requires` 条件。Brigadier 的默认条件对任何来源返回真，Minecraft 处理聊天命令时直接使用玩家自己的命令来源，没有额外的权限检查。任何非管理员玩家都可以在聊天栏执行这些命令。

**影响**：

- `/storyteller:update global <键名> <取值>` 与 `/storyteller:delete global <键名>` 会改写共享的 `StateManager.globalState`。`ActionRunner.passes` 把 `global.namedValues()` 作为 `global` 变量提供给所有玩家的条件表达式，一个玩家修改的内容会影响其他玩家的剧情分支，并且会写入 `global.nbt`。
- `/storyteller:expect <引用>` 可以替换当前顶层 frame 的 `allowedContinuation`，随后 `/storyteller:goto <引用>` 可以进入当前脚本内原本被禁止的对话。
- `/storyteller:reset` 与 `/storyteller:return all` 只影响执行者自己的 frame，`reset` 在文档中本来就面向玩家。

**最小修改**：把由数据包脚本调用的命令限制到至少 `GAMEMASTER` 级别，给 `update`、`delete`、`goto`、`expect` 增加：

```kotlin
.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
```

第 2 条的修改会使脚本执行来源具备该权限，因此命令动作、点击命令与内联命令仍能正常使用这些命令；玩家在聊天栏直接执行时会被拒绝。`get` 只读取，`return` 只影响自身，`reset` 是文档中面向玩家的命令，`run_action` 是点击入口，这四个保持开放。

### 5. 正文中的 `$` 没有转义方式

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/DialogParser.kt:372-385`

**触发过程**：`parseText` 遇到每一个 `$` 都按内联命令定界符处理。找到下一个 `$` 时，把中间文本交给 `parseCommands` 生成 `TextSegment.InlineCommand`；找不到时抛出 `DialogParseException`。KDoc 第 101 行明确写明 `$` 没有转义形式，因此正文里无法写出字面美元符号。

**影响**：

- `Alice: it costs $5` 只有一个 `$`，解析阶段抛出 `DialogParseException`，包含该脚本的整个 `storyteller:dialog_scripts` 表加载失败。首次加载时会导致启动失败。
- `Alice: The price is $5 and $10` 会被解析为字面文本 `The price is `、内联命令 `5 and `、字面文本 `10`。对白渲染时执行命令 `5 and`，`CommandExecutor` 抛出未被捕获的 `CommandSyntaxException`，服务器停止。

价格、金额、货币符号是常见正文内容，这个限制会直接影响数据包作者。

**最小修改**：在 `parseText` 的转义分支中同时处理 `$`，并同步更新 KDoc：

```kotlin
ESCAPE_CHAR -> {
    val next = source.getOrNull(index + 1)
    if (next == COMMENT_PREFIX || next == INLINE_COMMAND_DELIMITER) {
        literal.append(next)
        index += 2
    } else {
        literal.append(char)
        index++
    }
}
```

现有脚本的行为保持不变，数据包作者可以用 `\$` 写出字面美元符号。

## 中等问题

### 6. 带 `storyteller:` 前缀的流程命令绕过内联限制与 `return` 截断

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/DialogParser.kt:455-461`、`src/main/kotlin/io/ib67/mods/storyteller/dialog/pass/WaitActionPass.kt:85-88`

**触发过程**：`rejectFlowCommands` 与 `truncateAfterReturn` 都只把命令首词与裸名称比较。`storyteller:expect`、`storyteller:update`、`storyteller:goto`、`storyteller:delete`、`storyteller:return` 的首词不在集合中，会被放行。`normalizeFlowCommand` 只给裸名称补前缀，对已经带前缀的写法原样下发，`ScriptCommands` 注册的字面量本身就包含 `storyteller:` 前缀，Brigadier 按完整字符串匹配，因此这些命令确实会执行。

**影响**：

- `$storyteller:update flag true$` 在对白渲染时改写玩家状态，违反 KDoc 第 154 行对内联命令的限制。
- `$storyteller:goto missing$` 在渲染时调用 `goto`，`requireDialog` 抛出异常，异常沿渲染与游戏刻回调传播，服务器停止。
- `$storyteller:return$` 在渲染时弹出当前 frame，对白文本仍然发送，随后 `replaceFrame` 找不到原 frame，`DialogSessionRunner` 忽略这个返回值，frame 就此消失。
- 点击命令中的 `[storyteller:return; give @s minecraft:diamond 1]` 不会被截断。点击后 `return` 弹出 frame，循环继续执行 `give`。裸写法 `[return; give ...]` 会被截断，两种写法行为不同。

**最小修改**：在比较首词之前去掉可选的 `storyteller:` 前缀，两个位置共用同一个辅助函数。

```kotlin
internal fun flowWord(command: String): String =
    firstWord(command).removePrefix("storyteller:")
```

`rejectFlowCommands` 使用 `flowWord(command) in FLOW_KEYWORDS`，`truncateAfterReturn` 使用 `flowWord(it) == "return"`。`execute run storyteller:update ...` 这类间接调用仍然无法被首词检查覆盖，如果需要在加载阶段完全拦截，应该在 `CommandTokenPass` 中对命令序列执行一次语法校验，让非法数据包在加载阶段失败。

### 7. 内联 `get` 的返回值没有进入对白文本

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/CommandExecutor.kt:32-39`、`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/ScriptCommands.kt:167-172`

**触发过程**：`capture` 用 `withSource(capturingSource)` 替换命令来源，但保留了 `CommandSourceStack.entity`，该实体仍然是玩家。Minecraft 26.3 的 `CommandSourceStack.sendSystemMessage` 先检查 `getPlayer()`，非空时直接调用玩家的 `sendSystemMessage`，只有玩家为空时才转发给 `source` 字段。`ScriptCommands.get` 使用 `context.source.sendSystemMessage` 输出取值，因此：

- 取值被发送到玩家聊天栏，成为一条单独的聊天消息。
- `CapturingCommandSource` 收不到任何内容，`capture` 返回空字符串。
- 对白中的 `$get questStage$` 渲染为空。

**影响**：文档写明内联 `get` 可以把取值嵌入对白文本，实际功能不可用，玩家还会额外收到一条只包含数值的聊天消息。

**最小修改**：`get` 改用 `sendSuccess` 输出。`sendSuccess` 直接使用 `source` 字段发送消息，不经过玩家实体，因此会被 `CapturingCommandSource` 捕获；作为普通命令执行时仍然发送给玩家。

```kotlin
context.source.sendSuccess({ Component.literal(value.toString()) }, false)
```

### 8. 推进循环遍历在线玩家列表期间执行命令

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/session/DialogSessionRunner.kt:21-39`

**触发过程**：`server.playerList.players` 返回的是 `PlayerList` 内部的可变 `ArrayList`，`DialogSessionRunner` 直接对它做 `for` 循环。循环内部执行脚本命令。`kick` 或 `ban` 这类命令会同步调用 `player.connection.disconnect`，`disconnect` 通过 `server.executeBlocking` 在服务器主循环中直接执行 `handleDisconnection`，随后 `PlayerList.remove` 从同一个列表里删除玩家。迭代器在下一次读取时抛出 `ConcurrentModificationException`，服务器停止。

**触发条件**：执行命令的玩家具备管理员权限，并且脚本中包含 `kick` 或 `ban`。单人世界调用 `kick` 会先被 `isPublished` 检查拒绝，专用服务器与局域网世界可以触发。

**最小修改**：遍历列表的副本。

```kotlin
for (player in server.playerList.players.toList()) {
```

副本只用于遍历，玩家状态与命令执行逻辑不变。

### 9. 命令动作选择器中的 `#` 被当作注释截断

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/DialogParser.kt:266`

**触发过程**：`parseCommandAction` 对右方括号之后的文本先执行 `substringBefore('#')`，再校验选择器。选择器里的 `#` 属于选择器内容，例如 `[say hi] @e[name="a#b"]` 会被截成 `@e[name="a`。校验只检查以 `@` 开头且不含空白，残缺选择器被接受并保存。执行时 `EntityArgument.entities().parse` 因引号与方括号未闭合抛出 `CommandSyntaxException`，该异常未被捕获，服务器停止。

**影响**：包含 `#` 的选择器无法使用，残缺选择器会在运行期导致服务器停止。

**最小修改**：在选择器解析完成之后再处理行尾注释。扫描右方括号之后的文本时记录方括号深度与引号状态，第一个位于方括号之外且不在引号内的 `#` 才是注释起点。现有 `@a # 注释` 与 `@a#注释` 两种写法保持原来的解析结果。

### 10. 点击命令选择器不要求前置空白

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/DialogParser.kt:309-337`，判定位于第 318 行

**触发过程**：`splitClickCommand` 用 `lastIndexOf('@')` 定位选择器，只要求 `@` 之前的内容以 `]` 结尾，没有检查 `]` 与 `@` 之间是否存在空白。KDoc 第 76 行把点击命令写成 `"[" 命令序列 "]" [空白 选择器]`，空白是选择器出现的前提。因此 `Alice: look [here]@player` 会被解析成正文 `look`、点击命令 `here`、选择器 `@player`。

**影响**：脚本作者按文法写出的普通文本被截断并变成可点击命令。玩家点击后执行不存在的命令，客户端收到 `command.failed` 提示，服务器继续运行。

**最小修改**：在判定条件中增加前置空白检查：

```kotlin
if (atIndex > 0 && source[atIndex - 1].isWhitespace() && candidate.isNotEmpty() &&
    candidate.none { it.isWhitespace() } && beforeSelector.endsWith(COMMAND_CLOSE)
) {
```

### 11. `allowedContinuation` 类型错误时被当作空列表

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/state/DialogFrameCodec.kt:37-53`

**触发过程**：`decode` 用 `tag.getList(ALLOWED_CONTINUATION).orElse(null)` 读取限制列表。`CompoundTag.getList` 在标签类型不是列表时返回空 `Optional`，这一行把缺失与类型错误都变成 `null`，最后得到空列表。`DialogDirector.canPushDialog` 在 `allowedContinuation` 为空时对所有引用返回真，脚本作者设置的限制被静默取消。文档第 194 行写明字段类型错误会抛出 `IllegalArgumentException`，这个字段与文档不一致。

**影响**：手工修改或迁移过的玩家存档中，类型错误不会被发现，对话限制失效。

**最小修改**：区分标签缺失与类型错误。

```kotlin
val continuationTag = tag.get(ALLOWED_CONTINUATION)
val allowedContinuation = continuationTag?.asList()?.orElseThrow {
    IllegalArgumentException("Dialog frame allowedContinuation is not a list: $tag")
}?.let { continuation -> /* 保持现有逐项解析 */ } ?: emptyList()
```

旧存档没有该字段时仍然得到空列表，类型错误时抛出异常。

## 低风险问题

### 12. 点击 token 可以在对话结束后继续使用

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/ActionCommands.kt:23-42`

**触发过程**：`storyteller:run_action` 只按 token 从全局 `CommandTokenRegistry` 取回命令并执行，不检查调用者的当前 frame、动作下标或产生 token 的条件。token 只由选择器与命令序列计算，所有玩家相同，在整个脚本快照存续期间有效。玩家可以重复点击聊天栏中的旧对白，也可以在对话结束后继续发送该命令。

**影响**：玩家可以重复执行点击命令里的流程命令，改变自己的状态，或者重新进入已经离开的对话。在按第 2 条提升脚本命令权限之后，点击命令中的 `give` 等命令也会具备脚本权限，重放的影响会变大。

**最小修改**：在 `ActionCommands` 中校验 token 对应当前顶层 frame 的当前动作：

```kotlin
val frame = director.currentFrame(player) ?: throw /* 无活动对话 */
val dialog = DialogScriptRegistry.get(frame.scriptId)?.resolve(frame.dialogId)
val action = dialog?.actions?.getOrNull(frame.actionIndex) as? DialogueAction
val expected = (action?.clickCommand as? CommandInvocation.Tokenized)?.token
if (expected != token) {
    throw SimpleCommandExceptionType(Component.literal("Unknown action token: $token")).create()
}
```

对白显示期间重复点击仍然会重复执行命令，这是文档写明的行为；对话推进之后旧 token 不再有效。这个修改需要把 `DialogSessionRunner` 已经使用的 `DialogDirector` 注入 `ActionCommands`，不改变数据模型与 token 计算方式。

### 13. `fabric.mod.json` 声明的图标文件没有被打包

**位置**：`src/main/resources/fabric.mod.json:10`

**触发过程**：元数据声明 `"icon": "assets/storyteller/icon.png"`，但 `src/main/resources`、`src/client/resources`、`build/resources` 与打包出的 jar 里都没有 `assets` 目录，也没有任何图片文件。`processResources` 只展开占位符，不会生成图片。

**影响**：模组列表只能显示默认占位图，发布出的 jar 带有指向不存在文件的元数据。

**最小修改**：加入图标文件，或者从 `fabric.mod.json` 中删除 `icon` 字段。

### 14. `processResources` 缺少 `kotlin_loader_version` 任务输入

**位置**：`build.gradle.kts:85-98`

**触发过程**：`processResources` 的 `expand` 替换 `kotlin_loader_version`（第 96 行），但 `inputs.property` 只登记了 `version`、`minecraft_version`、`loader_version`（第 86 至 88 行）。Gradle 不会把 `expand` 使用的属性自动登记为输入。只修改 `gradle.properties` 中的 `kotlin_loader_version` 后执行 `gradle build`，`processResources` 会被判定为最新，展开后的 `fabric.mod.json` 保留旧版本，最终 jar 里的 `fabric-language-kotlin` 下限声明过期。

**影响**：本地增量构建生成的发行包声明了错误的语言加载器下限。干净构建不会出现这个问题。

**最小修改**：补上对应输入，与项目 CLAUDE.md 的要求保持一致。

```kotlin
inputs.property("kotlin_loader_version", project.property("kotlin_loader_version"))
```

### 15. 分界线名称允许空白与冒号，引用语法却无法表达

**位置**：`src/main/kotlin/io/ib67/mods/storyteller/dialog/DialogParser.kt:231-237`、`src/main/kotlin/io/ib67/mods/storyteller/dialog/runner/ScriptCommands.kt:246-265`

**触发过程**：分界线名称只去掉首尾空白，内部空白与冒号都会被接受。`parseDialogRef` 按第一个冒号切分脚本名与对话名，`expect` 又按空白拆分多个引用。名称中包含空白或冒号的子对话无法通过 `goto` 或 `expect` 指向。

**影响**：数据包作者可以写出永远无法进入的子对话，脚本在运行期才表现为找不到对话。

**最小修改**：在解析分界线时拒绝包含空白或冒号的名称，与引用文法保持一致：

```kotlin
if (name.isEmpty() || name.any { it.isWhitespace() || it == ':' }) {
    throw DialogParseException(lineNumber, "Invalid dialog name: $name")
}
```

## 单元测试质量

测试套件目前有 16 个类、146 个用例，全部通过，并且在上述启动崩溃发生之后仍然全部通过。核心问题在于测试主要覆盖纯函数与数据结构，没有覆盖组件接入与游戏刻推进，因此很多行为错误不会让任何用例失败。测试没有使用 mock，这一点符合项目约定；问题集中在覆盖范围与断言强度。

### 覆盖缺口

| 未覆盖的部分 | 现有测试的情况 | 哪种错误实现仍然可以通过 |
|---|---|---|
| `StateManager` 的生命周期、保存、载入、加入与重载回调 | 测试目录中没有出现 `StateManager` | 删除 `beforeSave` 注册、把重载校验和比较写反、把 `onJoin` 传入固定字符串，全部用例仍然通过 |
| `ActionRunner.tick` 的每游戏刻执行语义 | `ActionRunnerTimingTest` 只调用 `dialogueDuration` 与 `waitCompleted` 两个顶层函数 | 每个游戏刻重复发送对白、一次执行完整命令序列、`wait` 早一个游戏刻完成，全部用例仍然通过 |
| `DialogSessionRunner.tick` 的推进循环 | 没有任何测试构造 `DialogSessionRunner` | 把跳过条件改成 `now <= nextActionTick` 使新 frame 永久挂起，或者删除间隔判断使动作紧接执行，全部用例仍然通过 |
| `CommandExecutor.run` 与 `capture` | `CommandExecutorTest` 只测试 `normalizeFlowCommand` | 选择器解析、来源权限、内联取值收集出错，全部用例仍然通过 |
| `ScriptCommands` 的命令注册与处理函数 | `ScriptCommandsTest` 只调用 `coerceValue` 与 `parseDialogRef` | `goto` 参数类型错误、`update` 写入全局状态、`expect` 绕过限制，全部用例仍然通过 |
| `ActionCommands` 与点击命令 | 没有测试构造 `ActionCommands` | token 失效、重复使用、命令名写错，全部用例仍然通过 |
| `DialogueTextRenderer` 的内联命令分支 | 两个用例都传入 `{ error(...) }` 回调，测试数据没有 `InlineCommand` | 删除内联命令分支或忽略回调返回值，两个用例仍然通过 |
| `DialogScript.resolve` 的子对话查找 | `DialogDirectorTest` 注册的脚本 `subDialogs` 都是空映射 | 把 `resolve` 改成只返回默认对话，全部用例仍然通过 |
| `DialogScriptRegistry.prepare` 与 `apply` | 测试只调用 `load` 与 `register`，`register` 在生产代码中没有调用方 | 把 `apply` 改成安装空表，或者跳过 pass 直接安装原始脚本，全部用例仍然通过 |
| `PlayerState.replaceFrame` 的对象身份查找 | `DialogDirectorTest` 两次传入 `state.currentFrame` 返回的原对象 | 把 `it === expected` 改成结构相等，全部用例仍然通过；两个字段完全相同的 frame 会更新错误的 frame |
| `PlayerState.resetDialogState` 在校验和一致时的行为 | `reset clears the frame list` 总是传入不同的校验和 | 把 `setFrames(emptyList())` 移到校验和比较内部，用例仍然通过 |
| `ScriptConditionEvaluator` 的表达式缓存与重复调用 | 每个用例只调用一次，表达式各不相同 | 缓存第一次的 scope 或第一次的布尔结果，用例仍然通过 |
| `ScriptConditionEvaluator` 的 Long 数值 | 所有数值取值都是 `Int` | 把 `is Number` 改成 `is Int, is Double`，用例仍然通过；真实条件变量中的两个 Long 字段会触发异常 |

### 断言强度不足

- `ActionRunnerTimingTest` 的持续时间上限只比较两个远超上限的输入，任何上限值都会通过。用例没有断言基础 20 个游戏刻、人物名称长度项与 200 个游戏刻的上限值。
- `ScriptChecksumTest` 只改变脚本 ID、文本、命令内容与动作顺序。选择器、条件、人物名称、文本片段类型与 `WaitAction` 的超时都没有参与比较，删除对应的写入逻辑不会让任何用例失败。
- `CommandTokenRegistryTest` 的所有比较都使用同一个选择器，或者只改变命令内容。删除选择器参与摘要计算不会让任何用例失败。
- `PlayerStateCodecTest` 只用编码器与解码器互相验证，没有固定的 NBT 样例。把标签名同时改在编码与解码两侧不会让用例失败，旧存档在真实加载时会解析失败。时间字段只使用 1234、1250、20、15 这类小数值，`putLong` 改成 `putInt` 不会被发现。`Byte`、`Short`、`Float` 分支没有用例。
- `WaitActionPassTest` 的拆分用例都使用默认选择器 `@s`，拆分后是否保留选择器没有被检查。
- `DialogParserTest` 的内联命令限制只测试 `expect`，其余禁用词与带 `storyteller:` 前缀的形式都没有覆盖；合法的 `[wait 20]`、`[wait]` 与带条件的 `wait` 也没有解析用例。

### 没有断言的测试

`DialogParserTest.someExample`（第 30 至 50 行）把一个多行脚本交给解析器，随后只执行 `println`，没有任何断言。只要解析不抛异常，这个用例就算通过。综合示例中的条件、点击命令、独立命令动作与分界线都没有被验证。

### 测试入口与生产路径不一致

- `DialogScriptRegistry.register` 只被测试调用，生产入口是 `prepare` 与 `apply`；测试覆盖了 `register` 内部的 pass 流水线，没有覆盖真实数据包重载的入口关系。
- `DialogSessionStateTest` 直接调用 `PlayerState.useScriptChecksum`，没有经过 `StateManager.onJoin` 与 `onDialogScriptsReloaded`，这两个入口的参数来源与比较方向都没有测试约束。
- `CommandTokenRegistryTest` 直接触发全局重载事件并替换全局 token 表，测试之间没有清理逻辑。当前用例的执行顺序没有产生失败，但新增依赖 token 的测试时容易受到前面测试的影响。

### 建议补充的测试

1. 为 `StateManager` 增加可执行的测试入口。初始化检查可以提取成内部函数，用现有 JUnit 测试直接覆盖“路径未初始化时不访问文件”的行为；`SERVER_STARTED`、`BEFORE_SAVE`、玩家加入与脚本重载的事件顺序放在 Fabric GameTest 或开发服务器中验证。这一组测试会直接发现第 1 条崩溃。
2. 构造 `ActionRunner` 与 `DialogSessionRunner`，用固定游戏刻推进一个包含对白、命令、`wait` 与 `goto` 的脚本，断言每个游戏刻的消息发送次数、命令执行条数与 frame 的下标变化。这两个类的入口需要 `ServerPlayer` 与 `MinecraftServer`，适合放在 Fabric GameTest 中。
3. 为 `CommandExecutor.run` 覆盖默认选择器、带方括号的选择器、普通玩家来源与管理员来源，断言选择器解析结果与命令执行次数；为 `capture` 覆盖内联 `get`，断言渲染结果包含取值。
4. 为 `ScriptCommands` 覆盖 `expect`、`update`、`get`、`goto`、`delete`、`return` 的处理函数，包括带脚本名的引用、包含斜杠的脚本 ID、普通玩家来源与管理员来源。
5. 为 `ActionCommands` 覆盖 token 命中、token 失效、对话推进之后的旧 token，以及点击命令的执行顺序。
6. 为渲染器补充 `InlineCommand` 用例，为颜色补充格式代码 `k` 至 `o` 的用例。
7. 为校验和与 token 补充选择器、条件、人物名称、文本片段类型、`WaitAction` 超时的比较用例。
8. 为编解码补充固定的 NBT 样例与取值范围较大的 Long，覆盖 `Byte`、`Short`、`Float` 分支。
9. 为 `DialogDirector` 补充子对话注册与 `resolve` 查找，为 `replaceFrame` 补充两个结构相同但对象不同的 frame。
10. 删除 `someExample`，或者把它改成带断言的综合解析用例。

## 修改优先级

1. `StateManager.saveDirty` 的初始化检查，消除打开世界时的崩溃。
2. `CommandExecutor` 的脚本来源权限与 `ScriptCommands` 的权限要求，恢复普通玩家的命令执行并阻止聊天栏改写全局状态。
3. `goto` 参数改为 `greedyString`，恢复文档化的跨脚本引用。
4. `$` 的转义支持，避免普通文本导致脚本表加载失败或服务器停止。
5. 首词前缀归一化、内联 `get` 的输出路由、在线玩家列表副本，修复三处中等问题。
6. 选择器注释解析、点击选择器空白判定、`allowedContinuation` 类型校验，修复三处输入解析问题。
7. 点击 token 的当前动作校验，在提升脚本命令权限之后收紧重放范围。
8. 图标与 `processResources` 输入，修复打包配置。
9. 按上一节补充测试，优先覆盖 `StateManager`、`ActionRunner`、`DialogSessionRunner` 与命令执行路径。
