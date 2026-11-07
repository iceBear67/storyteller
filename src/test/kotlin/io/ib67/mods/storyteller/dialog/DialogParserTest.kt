package io.ib67.mods.storyteller.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialogParserTest {

    private val parser = DialogParser()

    private fun parseSingle(source: String): Action = parser.parse(source).single()

    private fun parseDialogue(source: String): DialogueAction = parseSingle(source) as DialogueAction

    @Test
    fun someExample(){
        println(parser.parse("""
            A: 说了一点小猫话
            B: 说了一点龙门脏话
            一些不知道从哪里来的内心的声音
            (可能的条件), 选择一，一些声音 [update B 1; goto dialog_b]
            选择二，另一些声音 [update C 1; goto dialog_c]

            [expect dialog_b dialog_c] # 一个特殊的命令，玩家接下来只能进入这两种 dialog

            # dialog 可以在文件内插入一些具备名字的局部对白
            ---  dialog_b

            --- dialog_c
        """.trimIndent()).joinToString("\n"))
    }
    @Test
    fun `comment line is skipped`() {
        assertEquals(emptyList<Action>(), parser.parse("# 这是注释"))
    }

    @Test
    fun `comment without content is skipped`() {
        assertEquals(emptyList<Action>(), parser.parse("#"))
    }

    @Test
    fun `divider line is parsed`() {
        assertEquals(DividerAction("第一章"), parseSingle("--- 第一章"))
    }

    @Test
    fun `divider without space is parsed`() {
        assertEquals(DividerAction("第一章"), parseSingle("---第一章"))
    }

    @Test
    fun `divider without name is rejected`() {
        val error = assertThrows(DialogParseException::class.java) { parser.parse("---") }
        assertEquals(1, error.lineNumber)
    }

    @Test
    fun `blank lines are skipped`() {
        assertEquals(emptyList<Action>(), parser.parse("\n   \n\t\n"))
    }

    @Test
    fun `dialogue line is parsed`() {
        assertEquals(
            DialogueAction(
                condition = null,
                speaker = "Alice",
                text = listOf(TextSegment.Literal("你好，世界")),
                clickCommand = null,
            ),
            parseSingle("Alice: 你好，世界"),
        )
    }

    @Test
    fun `dialogue with condition is parsed`() {
        val action = parseDialogue("(flag.done && (count > 1)) Alice: 好")
        assertEquals(Condition("flag.done && (count > 1)"), action.condition)
        assertEquals("Alice", action.speaker)
    }

    @Test
    fun `dialogue with condition and click command is parsed`() {
        val action = parseDialogue("(done) Alice: 点我 [say hi] @a")
        assertEquals(Condition("done"), action.condition)
        assertEquals(CommandAction(commands = listOf("say hi"), selector = "@a"), action.clickCommand)
    }

    @Test
    fun `condition without action is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("(done)") }
    }

    @Test
    fun `unclosed condition is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("(done Alice: 好") }
    }

    @Test
    fun `empty condition is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("() Alice: 好") }
    }

    @Test
    fun `color codes are parsed`() {
        assertEquals(
            listOf(
                TextSegment.Literal("红色"),
                TextSegment.Color('c'),
                TextSegment.Literal("文本"),
                TextSegment.Color('r'),
                TextSegment.Literal("结束"),
            ),
            parseDialogue("Alice: 红色&c文本&r结束").text,
        )
    }

    @Test
    fun `unknown color code stays literal`() {
        assertEquals(
            listOf(TextSegment.Literal("Tom & Jerry &z")),
            parseDialogue("Alice: Tom & Jerry &z").text,
        )
    }

    @Test
    fun `inline command is parsed`() {
        assertEquals(
            listOf(
                TextSegment.Literal("分数："),
                TextSegment.InlineCommand(listOf("score", "echo hi")),
                TextSegment.Literal(" 分"),
            ),
            parseDialogue("Alice: 分数：\$score; echo hi\$ 分").text,
        )
    }

    @Test
    fun `unclosed inline command is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("Alice: 分数：\$score") }
    }

    @Test
    fun `empty inline command is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("Alice: 分数：\$\$") }
    }

    @Test
    fun `click command defaults to self selector`() {
        val action = parseDialogue("Alice: 点我 [say hi; say bye]")
        assertEquals(listOf(TextSegment.Literal("点我")), action.text)
        assertEquals(CommandAction(commands = listOf("say hi", "say bye")), action.clickCommand)
    }

    @Test
    fun `click command with selector is parsed`() {
        val action = parseDialogue("Alice: 点我 [say hi] @a")
        assertEquals(CommandAction(commands = listOf("say hi"), selector = "@a"), action.clickCommand)
    }

    @Test
    fun `click command supports nested brackets`() {
        val action = parseDialogue("Alice: 去 [execute as @e[type=zombie] run say hi] @s")
        assertEquals(
            CommandAction(commands = listOf("execute as @e[type=zombie] run say hi"), selector = "@s"),
            action.clickCommand,
        )
    }

    @Test
    fun `selector with brackets is parsed`() {
        val action = parseDialogue("Alice: 去 [say hi] @e[type=zombie]")
        assertEquals(CommandAction(commands = listOf("say hi"), selector = "@e[type=zombie]"), action.clickCommand)
    }

    @Test
    fun `empty text with click command is parsed`() {
        val action = parseDialogue("Alice: [say hi]")
        assertEquals(emptyList<TextSegment>(), action.text)
        assertEquals(CommandAction(commands = listOf("say hi")), action.clickCommand)
    }

    @Test
    fun `brackets inside text stay literal`() {
        assertEquals(
            listOf(TextSegment.Literal("他说 [笑] 了一声")),
            parseDialogue("Alice: 他说 [笑] 了一声").text,
        )
    }

    @Test
    fun `unmatched closing bracket stays literal`() {
        assertEquals(
            listOf(TextSegment.Literal("嗯]")),
            parseDialogue("Alice: 嗯]").text,
        )
    }

    @Test
    fun `inline comment after dialogue is ignored`() {
        val action = parseDialogue("Alice: 你好 # 这是注释")
        assertEquals(listOf(TextSegment.Literal("你好")), action.text)
    }

    @Test
    fun `inline comment without space is ignored`() {
        val action = parseDialogue("Alice: 你好#这是注释")
        assertEquals(listOf(TextSegment.Literal("你好")), action.text)
    }

    @Test
    fun `inline comment on speakerless dialogue is ignored`() {
        val action = parseDialogue("一些声音 # 提示: 这里有冒号")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("一些声音")), action.text)
    }

    @Test
    fun `escaped hash stays in text`() {
        val action = parseDialogue("Alice: 编号 \\#100")
        assertEquals(listOf(TextSegment.Literal("编号 #100")), action.text)
    }

    @Test
    fun `escaped hash before inline comment`() {
        val action = parseDialogue("Alice: 编号 \\#100 # 注释")
        assertEquals(listOf(TextSegment.Literal("编号 #100")), action.text)
    }

    @Test
    fun `line starting with escaped hash is text`() {
        val action = parseDialogue("\\# 这不是注释")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("# 这不是注释")), action.text)
    }

    @Test
    fun `standalone command action is parsed`() {
        assertEquals(
            CommandAction(commands = listOf("say a", "say b"), selector = "@e"),
            parseSingle("[say a; say b] @e"),
        )
    }

    @Test
    fun `standalone command action defaults to self selector`() {
        assertEquals(
            CommandAction(commands = listOf("say a")),
            parseSingle("[say a]"),
        )
    }

    @Test
    fun `standalone command action with condition is parsed`() {
        assertEquals(
            CommandAction(commands = listOf("say a"), condition = Condition("flag.done")),
            parseSingle("(flag.done) [say a]"),
        )
    }

    @Test
    fun `trailing comment after command action is ignored`() {
        assertEquals(
            CommandAction(commands = listOf("expect dialog_b dialog_c")),
            parseSingle("[expect dialog_b dialog_c] # 说明"),
        )
    }

    @Test
    fun `trailing comment after command action keeps selector`() {
        assertEquals(
            CommandAction(commands = listOf("say a"), selector = "@e"),
            parseSingle("[say a] @e # 说明"),
        )
    }

    @Test
    fun `trailing content after command action is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[say a] 剩余") }
    }

    @Test
    fun `empty command block is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[]") }
    }

    @Test
    fun `empty command in sequence is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[say a;;say b]") }
    }

    @Test
    fun `unclosed command block is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[say a") }
    }

    @Test
    fun `invalid selector is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[say a] @") }
        assertThrows(DialogParseException::class.java) { parser.parse("[say a] @a b") }
    }

    @Test
    fun `dialogue without speaker is parsed`() {
        assertEquals(
            DialogueAction(
                condition = null,
                speaker = null,
                text = listOf(TextSegment.Literal("雨下得很大")),
                clickCommand = null,
            ),
            parseSingle("雨下得很大"),
        )
    }

    @Test
    fun `dialogue without speaker with condition is parsed`() {
        val action = parseDialogue("(raining) 雨下得很大")
        assertEquals(Condition("raining"), action.condition)
        assertNull(action.speaker)
    }

    @Test
    fun `dialogue without speaker with click command is parsed`() {
        val action = parseDialogue("雨下得很大 [say hi] @a")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("雨下得很大")), action.text)
        assertEquals(CommandAction(commands = listOf("say hi"), selector = "@a"), action.clickCommand)
    }

    @Test
    fun `dialogue with empty speaker is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse(": 你好") }
    }

    @Test
    fun `actions keep line order`() {
        assertEquals(
            listOf(
                DividerAction("第一章"),
                DialogueAction(
                    condition = null,
                    speaker = "Alice",
                    text = listOf(TextSegment.Literal("你好")),
                    clickCommand = null,
                ),
                CommandAction(commands = listOf("say a")),
            ),
            parser.parse(
                """
                # 注释
                --- 第一章
                Alice: 你好
                [say a]
                """.trimIndent(),
            ),
        )
    }

    @Test
    fun `error reports line number`() {
        val error = assertThrows(DialogParseException::class.java) {
            parser.parse(
                """
                # 注释
                []
                """.trimIndent(),
            )
        }
        assertEquals(2, error.lineNumber)
    }

    @Test
    fun `parseDialogs splits actions by divider`() {
        val script = parser.parseDialogs(
            """
            A: 开场
            --- 第一章
            B: 第二节
            """.trimIndent(),
            "main",
        )
        assertEquals("main", script.id)
        assertEquals("main", script.defaultDialog.id)
        assertEquals(listOf("第一章"), script.subDialogs.keys.toList())
        assertEquals(
            listOf(DialogueAction(null, "A", listOf(TextSegment.Literal("开场")), null)),
            script.defaultDialog.actions,
        )
        assertEquals("第一章", script.subDialogs.getValue("第一章").id)
        assertEquals(
            listOf(DialogueAction(null, "B", listOf(TextSegment.Literal("第二节")), null)),
            script.subDialogs.getValue("第一章").actions,
        )
    }

    @Test
    fun `parseDialogs excludes divider from actions`() {
        val script = parser.parseDialogs("A: 开场\n--- 第一章", "main")
        assertEquals(emptyList<Action>(), script.subDialogs.getValue("第一章").actions)
    }

    @Test
    fun `parseDialogs skips comments`() {
        val script = parser.parseDialogs(
            """
            # 顶部注释
            --- 第一章
            # 章节注释
            A: 你好
            """.trimIndent(),
            "main",
        )
        assertEquals(emptyList<Action>(), script.defaultDialog.actions)
        assertEquals(
            listOf(DialogueAction(null, "A", listOf(TextSegment.Literal("你好")), null)),
            script.subDialogs.getValue("第一章").actions,
        )
    }

    @Test
    fun `parseDialogs without divider keeps top level dialog`() {
        val script = parser.parseDialogs("A: 你好", "file")
        assertEquals(emptySet<String>(), script.subDialogs.keys)
        assertEquals(1, script.defaultDialog.actions.size)
    }

    @Test
    fun `parseDialogs of empty source returns empty top level dialog`() {
        assertEquals(
            DialogScript(
                id = "main",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(actions = emptyList(), id = "main"),
            ),
            parser.parseDialogs("", "main"),
        )
    }

    @Test
    fun `parseDialogs rejects duplicate divider names`() {
        val error = assertThrows(DialogParseException::class.java) {
            parser.parseDialogs("--- 第一章\n--- 第一章", "main")
        }
        assertEquals(2, error.lineNumber)
    }

    @Test
    fun `parseDialogs rejects divider name equal to top level name`() {
        val error = assertThrows(DialogParseException::class.java) {
            parser.parseDialogs("A: 你好\n--- main", "main")
        }
        assertEquals(2, error.lineNumber)
    }
}
