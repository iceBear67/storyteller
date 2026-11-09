# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概况

Fabric 模组，使用 Kotlin 编写，目标 Minecraft 26.3。构建系统为 Gradle Kotlin DSL，配合 Fabric Loom 插件。

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

## 版本配置

Minecraft、Fabric 相关版本全部集中在 `gradle.properties`：`minecraft_version`、`loader_version`、`kotlin_loader_version`、`fabric_version`。

两处版本需要同步修改：

- `build.gradle.kts` 里 Kotlin 插件版本为 2.4.21，`kotlin_loader_version` 必须以 `+kotlin.2.4.21` 结尾，两者不一致时语言层会在启动时拒绝加载。
- 升级 Fabric 版本时，按 https://modmuss50.me/fabric.html 的对应关系同时更新上述四个属性。

## 源集划分

`loom { splitEnvironmentSourceSets() }` 把代码分成两个源集，二者在 `loom.mods` 中共同注册为模组 `storyteller`：

- `src/main`：通用代码，服务端与客户端都会加载。入口类 `io.ib67.mods.storyteller.Storyteller` 实现 `ModInitializer`。
- `src/client`：仅客户端代码。入口类 `io.ib67.mods.storyteller.client.StorytellerClient` 实现 `ClientModInitializer`。

入口点在 `src/main/resources/fabric.mod.json` 的 `entrypoints` 中声明。通用代码不能引用客户端源集里的类，客户端源集可以引用通用源集。

## Mixin 配置

- 通用 Mixin：`src/main/resources/storyteller.mixins.json`，包名为 `io.ib67.mods.storyteller.mixin`，类登记在 `mixins` 数组。
- 客户端 Mixin：`src/client/resources/storyteller.client.mixins.json`，包名为 `io.ib67.mods.storyteller.mixin.client`，类登记在 `client` 数组。

两边的 `injectors.defaultRequire` 都是 1，`overwrites.requireAnnotations` 都是 true。新增 Mixin 类后必须在对应的 JSON 数组里登记，否则不会生效。

## 资源展开

`fabric.mod.json` 中的 `${version}`、`${minecraft_version}`、`${loader_version}`、`${kotlin_loader_version}` 由 `processResources` 任务展开。在 `fabric.mod.json` 中新增占位符时，需要同步在 `build.gradle.kts` 的 `inputs.property` 与 `expand` 中补上对应属性。

## 编码约定

- 不要写注释。
- 源代码中的字符串信息统一使用英语，包括异常消息、命令提示、日志与测试数据。
- 需要监听事件的类在构造函数中接收对应的事件对象，并在构造函数内自行注册回调，监听方法保持私有。外部组件只负责构造对象，不得代替对象注册事件监听，也不得在类上公开专供外部调用的监听方法。

## 架构约束

- `PlayerState` 是玩家全部状态的唯一来源。其他状态通过属性 getter/setter 从 `PlayerState` 导出，不允许在其他位置再保存同一份状态。
- 不允许使用 `object`。所有组件使用普通类，依赖通过构造函数注入，在 `Storyteller` 初始化时创建并传递。
- 动作执行尽量表现为 `PlayerState` 的状态更新，保持无状态与原子化；玩家中途离线时不能依赖进程内额外状态。
