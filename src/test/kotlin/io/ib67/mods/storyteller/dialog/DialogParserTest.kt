package io.ib67.mods.storyteller.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialogParserTest {

    private val parser = DialogParser()

    private fun parseSingle(source: String): Action = parser.parse(source).single()

    private fun parseDialogue(source: String): DialogueAction = parseSingle(source) as DialogueAction

    private fun command(
        commands: List<String>,
        selector: String = CommandCall.DEFAULT_SELECTOR,
        condition: Condition? = null,
    ): CommandAction = CommandAction(
        invocation = CommandInvocation.Raw(CommandCall(commands = commands, selector = selector)),
        condition = condition,
    )

    private fun invocation(
        commands: List<String>,
        selector: String = CommandCall.DEFAULT_SELECTOR,
    ): CommandInvocation = CommandInvocation.Raw(CommandCall(commands = commands, selector = selector))

    @Test
    fun someExample() {
        println(
            parser.parse(
                """
                A: says some kitten words
                B: says some dragon swear words
                Some inner voice from nowhere
                (possible condition), choice one, some voice [update B 1; goto dialog_b]
                choice two, another voice [update C 1; goto dialog_c]

                [expect dialog_b dialog_c] # a special command: the player may only enter these two dialogs next

                # dialogs can insert named local dialogues in the file
                ---  dialog_b

                --- dialog_c
                """.trimIndent(),
            ).joinToString("\n"),
        )
    }

    @Test
    fun `comment line is skipped`() {
        assertEquals(emptyList<Action>(), parser.parse("# this is a comment"))
    }

    @Test
    fun `comment without content is skipped`() {
        assertEquals(emptyList<Action>(), parser.parse("#"))
    }

    @Test
    fun `divider line is parsed`() {
        assertEquals(DividerAction("Chapter One"), parseSingle("--- Chapter One"))
    }

    @Test
    fun `divider without space is parsed`() {
        assertEquals(DividerAction("Chapter One"), parseSingle("---Chapter One"))
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
                text = listOf(TextSegment.Literal("Hello, world")),
                clickCommand = null,
            ),
            parseSingle("Alice: Hello, world"),
        )
    }

    @Test
    fun `dialogue with condition is parsed`() {
        val action = parseDialogue("(flag.done && (count > 1)) Alice: okay")
        assertEquals(Condition("flag.done && (count > 1)"), action.condition)
        assertEquals("Alice", action.speaker)
    }

    @Test
    fun `dialogue with condition and click command is parsed`() {
        val action = parseDialogue("(done) Alice: click me [say hi] @a")
        assertEquals(Condition("done"), action.condition)
        assertEquals(invocation(listOf("say hi"), "@a"), action.clickCommand)
    }

    @Test
    fun `condition without action is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("(done)") }
    }

    @Test
    fun `unclosed condition is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("(done Alice: okay") }
    }

    @Test
    fun `empty condition is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("() Alice: okay") }
    }

    @Test
    fun `color codes are parsed`() {
        assertEquals(
            listOf(
                TextSegment.Literal("red"),
                TextSegment.Color('c'),
                TextSegment.Literal("text"),
                TextSegment.Color('r'),
                TextSegment.Literal("end"),
            ),
            parseDialogue("Alice: red&ctext&rend").text,
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
                TextSegment.Literal("Score: "),
                TextSegment.InlineCommand(listOf("score", "echo hi")),
                TextSegment.Literal(" points"),
            ),
            parseDialogue($$"Alice: Score: $score; echo hi$ points").text,
        )
    }

    @Test
    fun `unclosed inline command is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse($$"Alice: Score: $score") }
    }

    @Test
    fun `empty inline command is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("""Alice: Score: $$""") }
    }

    @Test
    fun `click command defaults to self selector`() {
        val action = parseDialogue("Alice: click me [say hi; say bye]")
        assertEquals(listOf(TextSegment.Literal("click me")), action.text)
        assertEquals(invocation(listOf("say hi", "say bye")), action.clickCommand)
    }

    @Test
    fun `click command with selector is parsed`() {
        val action = parseDialogue("Alice: click me [say hi] @a")
        assertEquals(invocation(listOf("say hi"), "@a"), action.clickCommand)
    }

    @Test
    fun `click command supports nested brackets`() {
        val action = parseDialogue("Alice: go [execute as @e[type=zombie] run say hi] @s")
        assertEquals(
            invocation(listOf("execute as @e[type=zombie] run say hi"), "@s"),
            action.clickCommand,
        )
    }

    @Test
    fun `selector with brackets is parsed`() {
        val action = parseDialogue("Alice: go [say hi] @e[type=zombie]")
        assertEquals(invocation(listOf("say hi"), "@e[type=zombie]"), action.clickCommand)
    }

    @Test
    fun `empty text with click command is parsed`() {
        val action = parseDialogue("Alice: [say hi]")
        assertEquals(emptyList<TextSegment>(), action.text)
        assertEquals(invocation(listOf("say hi")), action.clickCommand)
    }

    @Test
    fun `brackets inside text stay literal`() {
        assertEquals(
            listOf(TextSegment.Literal("he said [laugh] once")),
            parseDialogue("Alice: he said [laugh] once").text,
        )
    }

    @Test
    fun `unmatched closing bracket stays literal`() {
        assertEquals(
            listOf(TextSegment.Literal("hm]")),
            parseDialogue("Alice: hm]").text,
        )
    }

    @Test
    fun `inline comment after dialogue is ignored`() {
        val action = parseDialogue("Alice: hello # this is a comment")
        assertEquals(listOf(TextSegment.Literal("hello")), action.text)
    }

    @Test
    fun `inline comment without space is ignored`() {
        val action = parseDialogue("Alice: hello#this is a comment")
        assertEquals(listOf(TextSegment.Literal("hello")), action.text)
    }

    @Test
    fun `inline comment on speakerless dialogue is ignored`() {
        val action = parseDialogue("some voice # hint: there is a colon here")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("some voice")), action.text)
    }

    @Test
    fun `escaped hash stays in text`() {
        val action = parseDialogue("Alice: id \\#100")
        assertEquals(listOf(TextSegment.Literal("id #100")), action.text)
    }

    @Test
    fun `escaped hash before inline comment`() {
        val action = parseDialogue("Alice: id \\#100 # comment")
        assertEquals(listOf(TextSegment.Literal("id #100")), action.text)
    }

    @Test
    fun `line starting with escaped hash is text`() {
        val action = parseDialogue("\\# this is not a comment")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("# this is not a comment")), action.text)
    }

    @Test
    fun `standalone command action is parsed`() {
        assertEquals(
            command(listOf("say a", "say b"), "@e"),
            parseSingle("[say a; say b] @e"),
        )
    }

    @Test
    fun `standalone command action defaults to self selector`() {
        assertEquals(
            command(listOf("say a")),
            parseSingle("[say a]"),
        )
    }

    @Test
    fun `standalone command action with condition is parsed`() {
        assertEquals(
            command(listOf("say a"), condition = Condition("flag.done")),
            parseSingle("(flag.done) [say a]"),
        )
    }

    @Test
    fun `trailing comment after command action is ignored`() {
        assertEquals(
            command(listOf("expect dialog_b dialog_c")),
            parseSingle("[expect dialog_b dialog_c] # description"),
        )
    }

    @Test
    fun `trailing comment after command action keeps selector`() {
        assertEquals(
            command(listOf("say a"), "@e"),
            parseSingle("[say a] @e # description"),
        )
    }

    @Test
    fun `trailing content after command action is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse("[say a] leftover") }
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
                text = listOf(TextSegment.Literal("It is raining heavily")),
                clickCommand = null,
            ),
            parseSingle("It is raining heavily"),
        )
    }

    @Test
    fun `dialogue without speaker with condition is parsed`() {
        val action = parseDialogue("(raining) It is raining heavily")
        assertEquals(Condition("raining"), action.condition)
        assertNull(action.speaker)
    }

    @Test
    fun `dialogue without speaker with click command is parsed`() {
        val action = parseDialogue("It is raining heavily [say hi] @a")
        assertNull(action.speaker)
        assertEquals(listOf(TextSegment.Literal("It is raining heavily")), action.text)
        assertEquals(invocation(listOf("say hi"), "@a"), action.clickCommand)
    }

    @Test
    fun `dialogue with empty speaker is rejected`() {
        assertThrows(DialogParseException::class.java) { parser.parse(": hello") }
    }

    @Test
    fun `actions keep line order`() {
        assertEquals(
            listOf(
                DividerAction("Chapter One"),
                DialogueAction(
                    condition = null,
                    speaker = "Alice",
                    text = listOf(TextSegment.Literal("hello")),
                    clickCommand = null,
                ),
                command(listOf("say a")),
            ),
            parser.parse(
                """
                # comment
                --- Chapter One
                Alice: hello
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
                # comment
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
            A: opening
            --- Chapter One
            B: second part
            """.trimIndent(),
            "main",
        )
        assertEquals("main", script.id)
        assertEquals("main", script.defaultDialog.id)
        assertEquals(listOf("Chapter One"), script.subDialogs.keys.toList())
        assertEquals(
            listOf(DialogueAction(null, "A", listOf(TextSegment.Literal("opening")), null)),
            script.defaultDialog.actions,
        )
        assertEquals("Chapter One", script.subDialogs.getValue("Chapter One").id)
        assertEquals(
            listOf(DialogueAction(null, "B", listOf(TextSegment.Literal("second part")), null)),
            script.subDialogs.getValue("Chapter One").actions,
        )
    }

    @Test
    fun `parseDialogs excludes divider from actions`() {
        val script = parser.parseDialogs("A: opening\n--- Chapter One", "main")
        assertEquals(emptyList<Action>(), script.subDialogs.getValue("Chapter One").actions)
    }

    @Test
    fun `parseDialogs skips comments`() {
        val script = parser.parseDialogs(
            """
            # top comment
            --- Chapter One
            # section comment
            A: hello
            """.trimIndent(),
            "main",
        )
        assertEquals(emptyList<Action>(), script.defaultDialog.actions)
        assertEquals(
            listOf(DialogueAction(null, "A", listOf(TextSegment.Literal("hello")), null)),
            script.subDialogs.getValue("Chapter One").actions,
        )
    }

    @Test
    fun `parseDialogs without divider keeps top level dialog`() {
        val script = parser.parseDialogs("A: hello", "file")
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
            parser.parseDialogs("--- Chapter One\n--- Chapter One", "main")
        }
        assertEquals(2, error.lineNumber)
    }

    @Test
    fun `parseDialogs rejects divider name equal to top level name`() {
        val error = assertThrows(DialogParseException::class.java) {
            parser.parseDialogs("A: hello\n--- main", "main")
        }
        assertEquals(2, error.lineNumber)
    }

    @Test
    fun `escaped semicolon stays in the command`() {
        assertEquals(
            command(listOf("say a;b")),
            parseSingle("[say a\\;b]"),
        )
    }

    @Test
    fun `wait in a click command is rejected`() {
        assertThrows(DialogParseException::class.java) {
            parser.parse("Alice: hello [wait 20]")
        }
    }

    @Test
    fun `flow command in an inline command is rejected`() {
        assertThrows(DialogParseException::class.java) {
            parser.parse($$"hello $expect a b$")
        }
    }

    @Test
    fun `get in an inline command is parsed`() {
        val action = parseDialogue($$"hello $get key$")
        assertEquals(
            listOf(
                TextSegment.Literal("hello "),
                TextSegment.InlineCommand(listOf("get key")),
            ),
            action.text,
        )
    }

    @Test
    fun `invalid wait timeout is rejected`() {
        val error = assertThrows(DialogParseException::class.java) {
            parser.parse("[wait -1]")
        }
        assertEquals(1, error.lineNumber)
        assertThrows(DialogParseException::class.java) { parser.parse("[wait x]") }
        assertThrows(DialogParseException::class.java) { parser.parse("[wait 20 extra]") }
    }
}
