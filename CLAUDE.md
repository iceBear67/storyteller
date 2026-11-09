# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

Fabric 模组，使用 Kotlin 编写，目标 Minecraft 26.3。模组实现由数据包脚本驱动的对话系统：解析对话脚本，按游戏刻顺序执行对白与命令，并把玩家进度保存到世界存档。构建系统为 Gradle Kotlin DSL，配合 Fabric Loom 插件。

## 构建与运行

仓库里只有 `gradle/wrapper/gradle-wrapper.properties`，没有 wrapper jar，直接使用 sdkman 提供的 Gradle：

```bash
export JAVA_HOME=~/.sdkman/candidates/java/25.0.1-amzn
export PATH=$JAVA_HOME/bin:~/.sdkman/candidates/gradle/current/bin:$PATH

gradle build          # 编译并打包，产物在 build/libs
gradle runClient      # 启动开发客户端
gradle runServer      # 启动开发服务器
gradle compileKotlin  # 只编译 Kotlin 源码
gradle remapJar       # 生成可分发的中介名 jar
```

`build.gradle.kts` 指定 Java 25 工具链，编译前必须把 `JAVA_HOME` 指向 Java 25，否则工具链解析失败。测试使用 JUnit 6，通过 `gradle test` 运行。

条件表达式由 GraalJS 求值。`build.gradle.kts` 里的 `graalJsModules` 列出需要引入的 GraalJS 模块，版本统一由 `graalJsVersion` 指定；这些模块同时通过 `include` 打进模组 jar，升级时必须保持所有模块版本一致。

## 版本配置

Minecraft、Fabric 相关版本全部集中在 `gradle.properties`：`minecraft_version`、`loader_version`、`kotlin_loader_version`、`fabric_version`。

两处版本需要同步修改：

- `build.gradle.kts` 里 Kotlin 插件版本为 2.4.21，`kotlin_loader_version` 必须以 `+kotlin.2.4.21` 结尾，两者不一致时语言层会在启动时拒绝加载。
- 升级 Fabric 版本时，按 https://modmuss50.me/fabric.html 的对应关系同时更新上述四个属性。

## 源集与包结构

`loom { splitEnvironmentSourceSets() }` 把代码分成两个源集，二者在 `loom.mods` 中共同注册为模组 `storyteller`：

- `src/main`：通用代码，服务端与客户端都会加载。入口类 `io.ib67.mods.storyteller.Storyteller` 实现 `ModInitializer`。
- `src/client`：仅客户端代码。入口类 `io.ib67.mods.storyteller.client.StorytellerClient` 实现 `ClientModInitializer`，目前没有客户端逻辑。

入口点在 `src/main/resources/fabric.mod.json` 的 `entrypoints` 中声明。通用代码不能引用客户端源集里的类，客户端源集可以引用通用源集。

`src/main/kotlin/io/ib67/mods/storyteller` 下的包：

- `dialog`：对话模型（`Action`、`Dialog`、`DialogScript`）、`DialogParser` 与 `DialogScriptRegistry`。
- `dialog/runner`：单个动作的执行、条件求值、文本渲染、命令执行与点击命令触发的服务端命令。
- `dialog/session`：对话 frame、`DialogDirector` 与按游戏刻推进的对话会话。
- `state`：玩家状态与全局状态的加载、保存。
- `util`：`TypedKey` 与 `TypedKeyMap`。
- 根包：入口 `Storyteller`、`PlayerState`、`KnownKeys`。

## 对话脚本

`Storyteller.onInitialize` 把 `DialogScriptRegistry` 注册为服务端数据包的重载监听器，注册 ID 为 `storyteller:dialog_scripts`。脚本从 `data/<命名空间>/dialog/` 下读取 `.txt` 文件，可以放在子目录。脚本 ID 是文件路径相对于 `dialog/` 去掉扩展名的部分，例如 `data/storyteller/dialog/chapter/one.txt` 的 ID 是 `chapter/one`，命名空间不参与 ID。不同命名空间产生相同 ID 时加载失败。

脚本文件的完整文法写在 `DialogParser` 的 KDoc 中，修改解析行为时必须同步更新该说明与 `DialogParserTest`。一个 `DialogScript` 包含一个默认对话与若干子对话，分界线 `--- 名称` 划分章节，第一个分界线之前的动作属于默认对话。

## 状态与存档

`TypedKeyMap` 按字符串名称保存任意值，`TypedKey` 是类型化的键。`PlayerState` 继承 `TypedKeyMap`，用 `KnownKeys.DIALOG_STACK` 保存 `DialogFrame` 列表，并通过 `currentFrame` 暴露顶层 frame。新增会话字段时修改 `DialogFrame` 与 `DialogFrameCodec`；其他玩家状态字段仍先在 `KnownKeys` 注册，再在 `PlayerState` 上添加属性。`DialogScript.resolve` 按对话 ID 解析出默认对话或子对话。

`StateManager` 持有全部玩家状态与全局状态。服务器启动时加载全局状态，玩家状态在第一次访问时加载；服务器保存、服务器停止与玩家断开时写入 NBT：

- 玩家状态：世界存档 `data/storyteller/players/<uuid>.nbt`。
- 全局状态：世界存档 `data/storyteller/global.nbt`。

`TypedKeyMapCodec` 负责值与 NBT 的相互转换，支持布尔、各种数值、字符串、列表、字符串键映射与 `DialogFrame`，嵌套值同样支持。`DialogFrameCodec` 负责单个 frame 的字段编码。写入时先写临时文件，再原子替换目标文件；只有标记为 dirty 的状态才会保存。玩家离线时的进度必须全部来自 `PlayerState` 中的字段，不能在进程内保存会话状态。

## 动作执行

`Storyteller` 在初始化时创建全部组件，并通过构造函数注入依赖。`DialogDirector` 管理每个玩家的 `DialogFrame` 列表：每个对话进入时压入一个 frame，动作耗尽时弹出并恢复下层 frame。`pushDialog` 接受 `allowedContinuation`；列表非空时只能进入列表中的 `DialogRef`。`DialogSessionRunner` 每个游戏刻只处理顶层 frame，检查 `nextActionTick` 后把动作交给 `ActionRunner`，执行结束后按对象身份更新原来的 frame。

`ActionRunner` 按动作类型推进：对白动作在开始时求值条件，渲染一次文本，然后等待显示时长；显示时长为基础 20 游戏刻，加上人物名称与字面文本的长度，最多 200 游戏刻。命令动作在条件通过后每个游戏刻执行一条命令。分界线不会被执行。

条件由 `ScriptConditionEvaluator` 使用 GraalJS 求值。变量来自玩家状态的 `namedValues()`，另外附加名为 `global` 的全局状态映射。表达式必须返回布尔值，其他结果会抛出异常。编译后的表达式按源码缓存。

对白文本的点击事件设置为执行 `storyteller:run_action <token>`，token 由 `DialogScriptRegistry` 维护。玩家点击文本后，服务端按保存的选择器执行整条命令序列。只有对白动作的点击命令会生成 token。

`DialogScriptRegistry.reloadId` 在脚本重载时递增。每个 frame 记录创建时的重载编号，任一 frame 的记录与当前编号不一致，或者顶层对话已不存在时，整个 frame 列表会被清空。

## Mixin 配置

- 通用 Mixin：`src/main/resources/storyteller.mixins.json`，包名为 `io.ib67.mods.storyteller.mixin`，类登记在 `mixins` 数组。
- 客户端 Mixin：`src/client/resources/storyteller.client.mixins.json`，包名为 `io.ib67.mods.storyteller.mixin.client`，类登记在 `client` 数组。

两边的 `injectors.defaultRequire` 都是 1，`overwrites.requireAnnotations` 都是 true。新增 Mixin 类后必须在对应的 JSON 数组里登记，否则不会生效。

## 资源展开

`fabric.mod.json` 中的 `${version}`、`${minecraft_version}`、`${loader_version}`、`${kotlin_loader_version}` 由 `processResources` 任务展开。在 `fabric.mod.json` 中新增占位符时，需要同步在 `build.gradle.kts` 的 `inputs.property` 与 `expand` 中补上对应属性。

## 编码约定

- 不写行内注释与解释性注释。`DialogParser` 顶部的 KDoc 是脚本文法说明，修改文法时同步维护。
- 源代码中的字符串信息统一使用英语，包括异常消息、命令提示、日志与测试数据。
- 需要监听事件的类在构造函数中接收对应的事件对象，并在构造函数内自行注册回调，监听方法保持私有。外部组件只负责构造对象，不得代替对象注册事件监听，也不得在类上公开专供外部调用的监听方法。

## 测试

测试位于 `src/test/kotlin`，使用 JUnit 6，通过 `gradle test` 运行。测试直接构造 Minecraft 类型与临时资源包，不使用 mock。

`DialogScriptRegistry` 是进程内单例，测试中注册的脚本 ID 必须唯一，避免不同测试互相影响。

## 架构约束

- 玩家状态只存放在 `PlayerState` 中，全局状态只存放在 `StateManager.globalState` 中，其他位置不允许再保存同一份状态。
- 业务组件使用普通类，依赖通过构造函数注入，在 `Storyteller` 初始化时创建并传递。`DialogScriptRegistry` 与 `TypedKeyMapCodec` 是全局查表，以 `object` 形式存在；其他位置不要新增 `object`。
- 动作执行表现为 `PlayerState` 的状态更新，保持无状态与原子化；玩家中途离线时不能依赖进程内额外状态。
