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

class DialogParser {

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
                    throw DialogParseException(lineNumber, "对话名称重复：$currentName")
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
                throw DialogParseException(lineNumber, "分界线缺少对话名称")
            }
            return DividerAction(name)
        }

        var rest = line
        var condition: Condition? = null
        if (rest.startsWith(CONDITION_OPEN)) {
            val closing = findClosing(rest, CONDITION_OPEN, CONDITION_CLOSE)
                ?: throw DialogParseException(lineNumber, "条件缺少右括号")
            val expression = rest.substring(1, closing).trim()
            if (expression.isEmpty()) {
                throw DialogParseException(lineNumber, "条件为空")
            }
            condition = Condition(expression)
            rest = rest.substring(closing + 1).trim()
            if (rest.isEmpty()) {
                throw DialogParseException(lineNumber, "条件之后缺少动作")
            }
        }

        if (rest.startsWith(COMMAND_OPEN)) {
            return parseCommandAction(lineNumber, condition, rest)
        }
        return parseDialogueAction(lineNumber, condition, rest)
    }

    private fun parseCommandAction(lineNumber: Int, condition: Condition?, line: String): CommandAction {
        val closing = findClosing(line, COMMAND_OPEN, COMMAND_CLOSE)
            ?: throw DialogParseException(lineNumber, "命令动作缺少右方括号")
        val commands = parseCommands(lineNumber, line.substring(1, closing))
        val trailing = line.substring(closing + 1).substringBefore(COMMENT_PREFIX).trim()
        val selector = if (trailing.isEmpty()) {
            CommandAction.DEFAULT_SELECTOR
        } else {
            if (!trailing.startsWith(COMMAND_TARGET_PREFIX)) {
                throw DialogParseException(lineNumber, "命令动作之后存在多余内容：$trailing")
            }
            val name = trailing.substring(COMMAND_TARGET_PREFIX.length)
            if (name.isEmpty() || name.any { it.isWhitespace() }) {
                throw DialogParseException(lineNumber, "选择器无效：$trailing")
            }
            trailing
        }
        return CommandAction(commands = commands, selector = selector, condition = condition)
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
                throw DialogParseException(lineNumber, "人物为空")
            }
            body = content.substring(colon + 1).trim()
        }
        val (textSource, clickCommand) = splitClickCommand(lineNumber, body)
        return DialogueAction(
            condition = condition,
            speaker = speaker,
            text = parseText(lineNumber, textSource),
            clickCommand = clickCommand,
        )
    }

    private fun splitClickCommand(lineNumber: Int, body: String): Pair<String, CommandAction?> {
        val trimmed = body.trimEnd()
        var source = trimmed
        var selector = CommandAction.DEFAULT_SELECTOR

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
        return source.substring(0, opening).trimEnd() to CommandAction(commands = commands, selector = selector)
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
            val char = source[index]
            when {
                char == ESCAPE_CHAR && index + 1 < source.length && source[index + 1] == COMMENT_PREFIX -> {
                    literal.append(COMMENT_PREFIX)
                    index += 2
                }
                char == COLOR_PREFIX && index + 1 < source.length && source[index + 1] in COLOR_CODES -> {
                    flushLiteral()
                    segments += TextSegment.Color(source[index + 1])
                    index += 2
                }
                char == INLINE_COMMAND_DELIMITER -> {
                    val closing = source.indexOf(INLINE_COMMAND_DELIMITER, index + 1)
                    if (closing < 0) {
                        throw DialogParseException(lineNumber, "内联命令缺少结束的 \$")
                    }
                    flushLiteral()
                    segments += TextSegment.InlineCommand(
                        parseCommands(lineNumber, source.substring(index + 1, closing)),
                    )
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
        val commands = source.split(';').map { it.trim() }
        if (commands.any { it.isEmpty() }) {
            throw DialogParseException(lineNumber, "命令为空")
        }
        return commands
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
    IllegalArgumentException("第 $lineNumber 行：$message")
