package io.ib67.mods.storyteller.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class ScriptChecksumTest {

    @Test
    fun `checksum is stable across script map order`() {
        val first = script("first", emptyList())
        val second = script("second", emptyList())
        assertEquals(
            DialogScriptChecksum.compute(linkedMapOf("first" to first, "second" to second)),
            DialogScriptChecksum.compute(linkedMapOf("second" to second, "first" to first)),
        )
    }

    @Test
    fun `checksum is stable across subdialog order`() {
        val firstDialog = Dialog(actions = emptyList(), id = "first")
        val secondDialog = Dialog(actions = emptyList(), id = "second")
        val first = DialogScript(
            id = "script",
            subDialogs = linkedMapOf("first" to firstDialog, "second" to secondDialog),
            defaultDialog = Dialog(actions = emptyList(), id = "script"),
        )
        val second = DialogScript(
            id = "script",
            subDialogs = linkedMapOf("second" to secondDialog, "first" to firstDialog),
            defaultDialog = Dialog(actions = emptyList(), id = "script"),
        )
        assertEquals(
            DialogScriptChecksum.compute(mapOf("script" to first)),
            DialogScriptChecksum.compute(mapOf("script" to second)),
        )
    }

    @Test
    fun `checksum changes with script id`() {
        assertNotEquals(
            DialogScriptChecksum.compute(mapOf("first" to script("first", emptyList()))),
            DialogScriptChecksum.compute(mapOf("second" to script("second", emptyList()))),
        )
    }

    @Test
    fun `checksum changes with dialogue text`() {
        assertNotEquals(
            DialogScriptChecksum.compute(mapOf("dialogue" to script("dialogue", listOf(action("hello"))))),
            DialogScriptChecksum.compute(mapOf("dialogue" to script("dialogue", listOf(action("goodbye"))))),
        )
    }

    @Test
    fun `checksum changes with action order`() {
        val first = CommandAction(commands = listOf("say a"))
        val second = CommandAction(commands = listOf("say b"))
        assertNotEquals(
            DialogScriptChecksum.compute(mapOf("order" to script("order", listOf(first, second)))),
            DialogScriptChecksum.compute(mapOf("order" to script("order", listOf(second, first)))),
        )
    }

    @Test
    fun `checksum changes with click command`() {
        val first = DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandAction(commands = listOf("say a")),
        )
        val second = DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandAction(commands = listOf("say b")),
        )
        assertNotEquals(
            DialogScriptChecksum.compute(mapOf("click" to script("click", listOf(first)))),
            DialogScriptChecksum.compute(mapOf("click" to script("click", listOf(second)))),
        )
    }

    private fun action(text: String): DialogueAction =
        DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal(text)),
            clickCommand = null,
        )

    private fun script(id: String, actions: List<Action>): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = actions, id = id),
        )
}
