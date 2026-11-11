package io.ib67.mods.storyteller.dialog

private const val COMMENT_PREFIX = '#'
private const val DIVIDER_PREFIX = "---"
private const val CONDITION_OPEN = '('
private const val CONDITION_CLOSE = ')'
private const val COMMAND_OPEN = '['
private const val COMMAND_CLOSE = ']'
private const val COMMAND_TARGET_PREFIX = "@"
private const val SPEAKER_SEPARATOR = ':'
private const val COLOR_PREFIX = '&'
private const val ESCAPE_CHAR = '\\'
private const val INLINE_COMMAND_DELIMITER = '$'
private const val COLOR_CODES = "0123456789abcdefklmnorABCDEFKLMNOR"
private const val COMMAND_SEPARATOR = ';'
internal const val WAIT_COMMAND = "wait"
private val FLOW_KEYWORDS = setOf("expect", "check", "update", "goto", "delete", "return", "wait")

/**
 * 对话脚本文法
 *
 * 下面描述脚本源文件的完整结构。解析有两个入口：[parse] 按行顺序返回动作
 * 列表，分界线也会作为 [DividerAction] 返回；[parseDialogs] 把分界线当作
 * 章节分隔，把整个文件拆成一个默认对话与若干子对话。
 *
 * ## 文件结构
 *
 * ```
 * 文件     ::= { 行 }
 * 行       ::= 空行 | 注释行 | 分界线 | 动作行
 * 空行     ::= 任意空白字符
 * 注释行   ::= [空白] "#" 任意字符
 * ```
 *
 * 空行与注释行不产生动作。行首的 "#" 可以用 "\#" 转义成正文。
 *
 * ## 分界线
 *
 * ```
 * 分界线   ::= [空白] "---" [空白] 对话名称
 * ```
 *
 * "---" 之后去掉两端空白得到对话名称，名称不能为空，也不能包含空白或冒号，
 * 否则引用语法无法表达这个名称。[parseDialogs] 中，
 * 第一个分界线之前的动作组成默认对话，编号为脚本名称；每个分界线开启一个
 * 子对话，编号就是分界线的名称。名称与脚本名称重复、或者与已有的子对话
 * 重复时抛出 [DialogParseException]。
 *
 * ## 动作行
 *
 * ```
 * 动作行   ::= [空白] [条件] 动作
 * 条件     ::= "(" 表达式 ")"
 * 动作     ::= 命令动作 | 对白动作
 * ```
 *
 * 条件位于行首，括号按嵌套配对，表达式去掉两端空白后不能为空。条件之后
 * 必须紧跟一个动作。去掉条件之后，行首是 "[" 时按命令动作解析，其余情况
 * 按对白动作解析。条件在动作开始时求值一次，对两种动作都适用。
 *
 * ## 命令动作
 *
 * ```
 * 命令动作 ::= "[" 命令序列 "]" [空白 选择器] [空白 行尾注释]
 * ```
 *
 * 方括号按嵌套配对，命令内部可以出现方括号，例如
 * `[execute as @e[type=zombie] run say hi]`。右侧方括号之后只允许出现选择器
 * 与行尾注释。选择器默认是 "@s"；写出时以 "@" 开头，不能包含空白，可以带
 * 方括号，例如 `@e[type=zombie]`。行尾注释从第一个 "#" 开始，方括号内部的
 * "#" 属于命令本身。
 *
 * ## 对白动作
 *
 * ```
 * 对白动作 ::= [人物 ":"] 正文 [空白 观众选择器] [点击命令]
 * 点击命令 ::= "[" 命令序列 "]" [空白 选择器]
 * ```
 *
 * 正文中第一个 ":" 用来分隔人物与正文，冒号没有转义形式。冒号之前去掉两端
 * 空白得到人物，有冒号时人物不能为空；没有冒号时人物为空，渲染结果不带人物
 * 前缀。正文先去掉行尾注释（第一个未转义的 "#" 之后的部分），再识别点击命令、
 * 观众选择器并解析文本片段。只有正文以配对的 "]" 结尾时，末尾的方括号才作为
 * 点击命令；找不到配对的 "[" 时，方括号保留为普通文本。行尾注释在识别点击命令
 * 与内联命令之前截断，因此这两者内部不能出现未转义的 "#"，需要 "#" 时请使用
 * 独立的命令动作。
 *
 * 观众选择器是正文末尾一个前面带空白、以 "@" 开头且内部没有空白的词，例如
 * `Alice: 都过来 @a[distance=..10] [goto next] @s`。同一行还有点击命令时，
 * 观众选择器写在点击命令之前；点击命令右侧的选择器属于点击命令。没有观众
 * 选择器时使用 `data/storyteller/settings.json` 中的 defaultAudienceSelector。
 * 选择器在解析动作行时解析成 `ParsedSelector`，语法错误抛出带行号的
 * [DialogParseException]。以 "@" 开头但前面没有空白的词按普通文本处理，
 * 因此正文可以出现邮箱地址。
 *
 * ## 正文与文本片段
 *
 * ```
 * 正文     ::= { 字面文本 | 颜色符号 | 内联命令 | 转义井号 | 转义美元符 }
 * 颜色符号 ::= "&" 颜色代码
 * 颜色代码 ::= "0".."9" | "a".."f" | "k".."o" | "r"
 * 内联命令 ::= "$" 命令序列 "$"
 * 转义井号 ::= "\#"
 * 转义美元符 ::= "\$"
 * ```
 *
 * 颜色代码不区分大小写。颜色符号改变后续文本的样式，未知的颜色代码按普通
 * 文本保留。"&" 没有转义形式，后面跟着合法颜色代码时一定按颜色符号解析。
 *
 * 内联命令的两个 "$" 之间不能为空。渲染时依次执行命令序列中的全部命令，
 * 把所有命令产生的文本按顺序拼接，作为这一段的文本。正文中的字面 "$" 写
 * 成 "\$"。
 *
 * "\#" 产生一个字面 "#"，可以避免行尾注释截断；行首的 "\#" 也让整行按对白
 * 动作解析。"\$" 产生一个字面 "$"，避免被当作内联命令定界符。除 "\#" 与
 * "\$" 之外的组合没有特殊含义，按原样保留。
 *
 * ## 命令序列与选择器
 *
 * ```
 * 命令序列 ::= [ "repeatable" ";" ] 命令 { ";" 命令 }
 * 选择器   ::= "@" 非空白字符序列
 * ```
 *
 * 命令之间用 ";" 分隔，每个命令去掉两端空白，不允许空命令。命令序列没有
 * 引号；命令里的 "\;" 表示一个字面 ";"，不参与切分。
 *
 * 点击命令的命令序列以单独的 repeatable 作为第一条命令时，生成的 token 可以
 * 反复使用；没有这个修饰符时，每个玩家对同一个 token 只能成功使用一次。repeatable
 * 只能出现在点击命令里，后面必须至少跟一条命令；写在独立命令动作里时脚本
 * 加载失败。修饰符不参与执行。
 *
 * 选择器随命令动作一起保存。执行时用选择器查找实体，对每个匹配的实体以该
 * 实体作为执行者运行整条命令序列。六个脚本命令交给 Brigadier 的命令注册表
 * 处理，选择器对匹配到的玩家逐个生效，匹配到非玩家实体时命令报错。
 *
 * ## 脚本命令
 *
 * 七个脚本命令写在命令序列里，名字是 expect、check、update、get、goto、delete、
 * return。执行前会把首词换成 storyteller: 前缀，交给已注册的 Minecraft 命令。
 *
 * ```
 * 脚本命令 ::= "expect" 引用 { 引用 }
 *            | "check" 表达式
 *            | "update" [ "global" ] 键名 值
 *            | "get" [ "global" ] 键名
 *            | "goto" 引用
 *            | "delete" [ "global" ] 键名
 *            | "return" [ "all" ]
 * 引用     ::= [ 脚本名 ":" ] 对话名
 * ```
 *
 * 引用按第一个 ":" 切分，省略脚本名时使用执行命令时顶层 frame 的 scriptId。
 * check 求值表达式，结果为 JavaScript falsy 值时命令失败，同一条命令序列里
 * 后续的命令不再执行。它通常放在命令序列开头，作为后续命令的前提条件。变量
 * 与动作条件相同，来自玩家状态，并附加名为 global 的全局状态映射。
 *
 * expect 整体替换顶层 frame 的 allowedContinuation，之后只能压入列表中的
 * 对话。goto 压入目标 frame，目标对话结束弹出后，原 frame 从下一条命令继续。
 * return 弹出顶层 frame，带 all 时清空整个 frame 列表；return 后面的命令没有
 * 执行机会，后处理时会被截断。裸 return 保留给脚本命令，原版 return 需要写成
 * minecraft:return 或放在 execute ... run 之后。
 *
 * update 写入玩家状态或全局状态，首参数为 global 时写全局状态。值取键名之后
 * 的全部文本，可以包含空格与分号；首尾成对的引号会被去掉，然后 true/false
 * 解析为布尔，整数解析为 Int 或 Long，小数解析为 Double，其余解析为字符串。
 * get 读取对应的值并作为文本发给命令来源；放在内联命令里时可以嵌入对白文本。
 * delete 删除对应的键值对，键名不存在时忽略。update、get 与 delete 拒绝
 * dialogStack 键名。
 *
 * wait 是动作关键字，不是 Minecraft 命令。它可以和别的命令写在同一个命令
 * 序列里，后处理阶段的 WaitActionPass 会把它拆成独立的 WaitAction。wait 后面可以跟一个非负整数，
 * 表示挂起的游戏刻数；省略时永久挂起。拆分后的动作各自保留原条件，wait 之后
 * 的命令会在 wait 完成后再等待一个动作间隔。wait 不能出现在点击命令里；
 * expect、check、update、goto、delete、return 与 wait 不能出现在内联命令里，get 可以
 * 出现在内联命令里。
 *
 * ## 后处理
 *
 * [parse] 与 [parseDialogs] 返回原始解析结果：命令动作带有
 * `CommandInvocation.Raw`，点击命令同样如此。没有写观众选择器的对白动作在
 * 解析时使用构造参数提供的默认观众选择器。`DialogScriptRegistry` 在安装
 * 脚本表时按优先级从高到低执行 pass：
 *
 * - `WaitActionPass`：截断 `return` 之后的命令，把命令序列里的 `wait` 拆成
 *   `WaitAction`，并截断点击命令里 `return` 之后的命令。
 * - `CommandTokenPass`：把 `CommandInvocation.Raw` 替换成
 *   `CommandInvocation.Tokenized`，选择器与命令序列存入
 *   `CommandTokenRegistry`。
 *
 * ## 执行语义
 *
 * 对白动作先求值条件，条件为假时跳过整个动作。文本按片段渲染，内联命令在
 * 渲染时执行，渲染结果发送给观众选择器匹配到的玩家。动作推进、条件求值与
 * 命令执行始终由执行玩家完成。命令动作与等待动作的调试文本同样发送给各自的
 * 选择器匹配到的玩家。条件求值抛出异常时，解析层把错误包装为
 * `ConditionEvaluationException`；会话层捕获后挂起玩家，记录错误日志，调试
 * 模式开启时广播给全部玩家。挂起的玩家不再推进对话，直到手动切换挂起标记、
 * `/storyteller:reset` 或脚本校验和变化。
 *
 * 独立命令动作在轮到它时求值条件，条件为真后每个游戏刻执行一条命令，命令
 * 序列执行完毕再进入下一个动作。goto 压入的 frame 会暂时挡在原 frame 前面，
 * 原 frame 的 actionTicks 保持不动；目标对话弹出后从下一条命令继续。
 *
 * 点击命令在对白文本被点击时执行，每次点击都会立即执行整条命令序列。默认
 * 情况下每个玩家对同一个 token 只能成功执行一次，repeatable 修饰的点击命令
 * 不受这个限制。
 */
class DialogParser(
    private val defaultAudience: () -> ParsedSelector = {
        ParsedSelector.parse(CommandCall.DEFAULT_SELECTOR)
    },
) {

    fun parse(source: String): List<Action> =
        source.lineSequence()
            .mapIndexedNotNull { index, line -> parseLine(index + 1, line) }
            .toList()

    fun parseDialogs(source: String, name: String): DialogScript {
        val subDialogs = linkedMapOf<String, Dialog>()
        var defaultDialog = Dialog(actions = emptyList(), id = name)
        var currentName = name
        var currentActions = mutableListOf<Action>()
        var isDefault = true

        fun finishCurrent() {
            val dialog = Dialog(actions = currentActions, id = currentName)
            if (isDefault) {
                defaultDialog = dialog
            } else {
                subDialogs[currentName] = dialog
            }
            currentActions = mutableListOf()
        }

        for ((index, rawLine) in source.lineSequence().withIndex()) {
            val lineNumber = index + 1
            val action = parseLine(lineNumber, rawLine) ?: continue
            if (action is DividerAction) {
                finishCurrent()
                currentName = action.name
                if (currentName == name || currentName in subDialogs) {
                    throw DialogParseException(lineNumber, "Duplicate dialog name: $currentName")
                }
                isDefault = false
            } else {
                currentActions += action
            }
        }

        finishCurrent()
        return DialogScript(id = name, subDialogs = subDialogs, defaultDialog = defaultDialog)
    }

    private fun parseLine(lineNumber: Int, rawLine: String): Action? {
        val line = rawLine.trim()
        if (line.isEmpty()) {
            return null
        }
        if (line.startsWith(COMMENT_PREFIX)) {
            return null
        }
        if (line.startsWith(DIVIDER_PREFIX)) {
            val name = line.substring(DIVIDER_PREFIX.length).trim()
            if (name.isEmpty()) {
                throw DialogParseException(lineNumber, "Divider is missing a dialog name")
            }
            if (name.any { it.isWhitespace() || it == ':' }) {
                throw DialogParseException(lineNumber, "Invalid dialog name: $name")
            }
            return DividerAction(name)
        }

        var rest = line
        var condition: Condition? = null
        if (rest.startsWith(CONDITION_OPEN)) {
            val closing = findClosing(rest, CONDITION_OPEN, CONDITION_CLOSE)
                ?: throw DialogParseException(lineNumber, "Condition is missing a closing parenthesis")
            val expression = rest.substring(1, closing).trim()
            if (expression.isEmpty()) {
                throw DialogParseException(lineNumber, "Condition is empty")
            }
            condition = Condition(expression)
            rest = rest.substring(closing + 1).trim()
            if (rest.isEmpty()) {
                throw DialogParseException(lineNumber, "Condition is not followed by an action")
            }
        }

        if (rest.startsWith(COMMAND_OPEN)) {
            return parseCommandAction(lineNumber, condition, rest)
        }
        return parseDialogueAction(lineNumber, condition, rest)
    }

    private fun parseCommandAction(lineNumber: Int, condition: Condition?, line: String): CommandAction {
        val closing = findClosing(line, COMMAND_OPEN, COMMAND_CLOSE)
            ?: throw DialogParseException(lineNumber, "Command action is missing a closing bracket")
        val commands = parseCommands(lineNumber, line.substring(1, closing))
        validateWaitCommands(lineNumber, commands)
        val trailing = line.substring(closing + 1).substringBefore(COMMENT_PREFIX).trim()
        val selector = if (trailing.isEmpty()) {
            CommandCall.DEFAULT_SELECTOR
        } else {
            if (!trailing.startsWith(COMMAND_TARGET_PREFIX)) {
                throw DialogParseException(lineNumber, "Unexpected content after command action: $trailing")
            }
            val name = trailing.substring(COMMAND_TARGET_PREFIX.length)
            if (name.isEmpty() || name.any { it.isWhitespace() }) {
                throw DialogParseException(lineNumber, "Invalid selector: $trailing")
            }
            trailing
        }
        return CommandAction(
            invocation = CommandInvocation.Raw(CommandCall(commands = commands, selector = selector)),
            condition = condition,
            parsedSelector = parseSelector(lineNumber, selector),
        )
    }

    private fun parseDialogueAction(lineNumber: Int, condition: Condition?, line: String): DialogueAction {
        val content = stripInlineComment(line).trim()
        val colon = content.indexOf(SPEAKER_SEPARATOR)
        val speaker: String?
        val body: String
        if (colon < 0) {
            speaker = null
            body = content
        } else {
            speaker = content.substring(0, colon).trim()
            if (speaker.isEmpty()) {
                throw DialogParseException(lineNumber, "Speaker is empty")
            }
            body = content.substring(colon + 1).trim()
        }
        val (textSource, clickCommand, audience) = splitDialogueTail(lineNumber, body)
        return DialogueAction(
            condition = condition,
            speaker = speaker,
            text = parseText(lineNumber, textSource),
            clickCommand = clickCommand,
            audience = audience,
            audienceSelector = audience?.let { parseSelector(lineNumber, it) } ?: defaultAudience(),
        )
    }

    private fun parseSelector(lineNumber: Int, selector: String): ParsedSelector =
        try {
            ParsedSelector.parse(selector)
        } catch (exception: IllegalArgumentException) {
            throw DialogParseException(lineNumber, exception.message ?: "Invalid selector: $selector")
        }

    private fun splitDialogueTail(
        lineNumber: Int,
        body: String,
    ): Triple<String, CommandInvocation?, String?> {
        val trimmed = body.trimEnd()
        val selectorStart = trailingSelectorStart(trimmed)
        if (selectorStart > 0) {
            val prefix = trimmed.substring(0, selectorStart).trimEnd()
            if (!prefix.endsWith(COMMAND_CLOSE)) {
                return Triple(prefix, null, trimmed.substring(selectorStart))
            }
        }
        val (afterClick, clickCommand) = splitClickCommand(lineNumber, trimmed)
        val (textSource, audience) = splitAudienceSelector(afterClick)
        return Triple(textSource, clickCommand, audience)
    }

    private fun splitAudienceSelector(source: String): Pair<String, String?> {
        val trimmed = source.trimEnd()
        val start = trailingSelectorStart(trimmed)
        if (start < 0) {
            return trimmed to null
        }
        return trimmed.substring(0, start).trimEnd() to trimmed.substring(start)
    }

    private fun trailingSelectorStart(source: String): Int {
        val start = source.indexOfLast { it.isWhitespace() } + 1
        if (start <= 0) {
            return -1
        }
        val token = source.substring(start)
        if (!token.startsWith(COMMAND_TARGET_PREFIX) || token.length <= COMMAND_TARGET_PREFIX.length) {
            return -1
        }
        return start
    }

    private fun splitClickCommand(lineNumber: Int, body: String): Pair<String, CommandInvocation?> {
        val trimmed = body.trimEnd()
        var source = trimmed
        var selector = CommandCall.DEFAULT_SELECTOR

        val atIndex = source.lastIndexOf(COMMAND_TARGET_PREFIX)
        if (atIndex >= 0) {
            val candidate = source.substring(atIndex + 1)
            val beforeSelector = source.substring(0, atIndex).trimEnd()
            if (candidate.isNotEmpty() && candidate.none { it.isWhitespace() } && beforeSelector.endsWith(COMMAND_CLOSE)) {
                selector = COMMAND_TARGET_PREFIX + candidate
                source = beforeSelector
            }
        }

        if (!source.endsWith(COMMAND_CLOSE)) {
            return trimmed to null
        }
        val closing = source.length - 1
        val opening = findOpening(source, closing) ?: return trimmed to null
        val commands = parseCommands(lineNumber, source.substring(opening + 1, closing))
        for (command in commands) {
            if (firstWord(command) == WAIT_COMMAND) {
                throw DialogParseException(lineNumber, "wait cannot be used in a click command")
            }
        }
        return source.substring(0, opening).trimEnd() to
            CommandInvocation.Raw(CommandCall(commands = commands, selector = selector))
    }

    private fun parseText(lineNumber: Int, source: String): List<TextSegment> {
        val segments = mutableListOf<TextSegment>()
        val literal = StringBuilder()
        var index = 0

        fun flushLiteral() {
            if (literal.isNotEmpty()) {
                segments += TextSegment.Literal(literal.toString())
                literal.clear()
            }
        }

        while (index < source.length) {
            when (val char = source[index]) {
                ESCAPE_CHAR -> {
                    val escaped = source.getOrNull(index + 1)
                    if (escaped != null && (escaped == COMMENT_PREFIX || escaped == INLINE_COMMAND_DELIMITER)) {
                        literal.append(escaped)
                        index += 2
                    } else {
                        literal.append(char)
                        index++
                    }
                }
                COLOR_PREFIX -> {
                    if (index + 1 < source.length && source[index + 1] in COLOR_CODES) {
                        flushLiteral()
                        segments += TextSegment.Color(source[index + 1])
                        index += 2
                    } else {
                        literal.append(char)
                        index++
                    }
                }
                INLINE_COMMAND_DELIMITER -> {
                    val closing = source.indexOf(INLINE_COMMAND_DELIMITER, index + 1)
                    if (closing < 0) {
                        throw DialogParseException(
                            lineNumber,
                            "Inline command is missing a closing $INLINE_COMMAND_DELIMITER",
                        )
                    }
                    flushLiteral()
                    val commands = parseCommands(lineNumber, source.substring(index + 1, closing))
                    rejectFlowCommands(lineNumber, commands)
                    segments += TextSegment.InlineCommand(commands)
                    index = closing + 1
                }
                else -> {
                    literal.append(char)
                    index++
                }
            }
        }

        flushLiteral()
        return segments
    }

    private fun stripInlineComment(source: String): String {
        for (index in source.indices) {
            if (source[index] == COMMENT_PREFIX && (index == 0 || source[index - 1] != ESCAPE_CHAR)) {
                return source.substring(0, index)
            }
        }
        return source
    }

    private fun parseCommands(lineNumber: Int, source: String): List<String> {
        val commands = mutableListOf<String>()
        val current = StringBuilder()
        var index = 0
        while (index < source.length) {
            when (val char = source[index]) {
                ESCAPE_CHAR -> {
                    if (index + 1 < source.length && source[index + 1] == COMMAND_SEPARATOR) {
                        current.append(COMMAND_SEPARATOR)
                        index += 2
                    } else {
                        current.append(char)
                        index++
                    }
                }
                COMMAND_SEPARATOR -> {
                    commands += current.toString().trim()
                    current.clear()
                    index++
                }
                else -> {
                    current.append(char)
                    index++
                }
            }
        }
        commands += current.toString().trim()
        if (commands.any { it.isEmpty() }) {
            throw DialogParseException(lineNumber, "Command is empty")
        }
        return commands
    }

    private fun validateWaitCommands(lineNumber: Int, commands: List<String>) {
        for (command in commands) {
            if (firstWord(command) != WAIT_COMMAND) {
                continue
            }
            val rest = command.trim().removePrefix(WAIT_COMMAND).trim()
            if (rest.isEmpty()) {
                continue
            }
            val timeout = rest.toIntOrNull()
            if (timeout == null || timeout < 0) {
                throw DialogParseException(lineNumber, "Invalid wait timeout: $rest")
            }
        }
    }

    private fun rejectFlowCommands(lineNumber: Int, commands: List<String>) {
        for (command in commands) {
            if (flowWord(command) in FLOW_KEYWORDS) {
                throw DialogParseException(lineNumber, "Flow command cannot be used in an inline command: $command")
            }
        }
    }

    private fun findClosing(source: String, open: Char, close: Char): Int? {
        var depth = 0
        for (index in source.indices) {
            when (source[index]) {
                open -> depth++
                close -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }

    private fun findOpening(source: String, closingIndex: Int): Int? {
        var depth = 0
        for (index in closingIndex downTo 0) {
            when (source[index]) {
                COMMAND_CLOSE -> depth++
                COMMAND_OPEN -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return null
    }
}

class DialogParseException(val lineNumber: Int, message: String) :
    IllegalArgumentException("Line $lineNumber: $message")

internal fun firstWord(command: String): String =
    command.trim().takeWhile { !it.isWhitespace() }

internal fun flowWord(command: String): String =
    firstWord(command).removePrefix("storyteller:")
