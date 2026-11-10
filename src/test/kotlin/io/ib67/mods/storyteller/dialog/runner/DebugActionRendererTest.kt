package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandCall
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import io.ib67.mods.storyteller.dialog.WaitAction
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.TextColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DebugActionRendererTest {

    private val renderer = DebugActionRenderer(DefaultDialogueTextRenderer())

    @Test
    fun `debug output adds the action text on the next line`() {
        val action = DialogueAction(
            condition = Condition("flag"),
            speaker = "Alice",
            text = listOf(TextSegment.Literal("hi")),
            clickCommand = null,
        )
        val component = renderer.render(action) { error("Inline command must not be called") }!!
        assertEquals("Alice: hi\n  ↘ (flag) Alice: hi", component.string)
        assertEquals(
            TextColor.fromLegacyFormat(ChatFormatting.GRAY),
            component.siblings.last().style.color,
        )
    }

    @Test
    fun `debug output renders command actions`() {
        val action = CommandAction(
            CommandInvocation.Raw(CommandCall(commands = listOf("say hi"), selector = "@a")),
        )
        val component = renderer.render(action) { error("Inline command must not be called") }!!
        assertEquals("  ↘ [say hi @a]", component.string)
        assertEquals(
            TextColor.fromLegacyFormat(ChatFormatting.GRAY),
            component.style.color,
        )
    }

    @Test
    fun `debug output renders wait actions`() {
        val component = renderer.render(WaitAction(condition = null, timeoutTicks = 20)) {
            error("Inline command must not be called")
        }!!
        assertEquals("  ↘ [wait 20]", component.string)
    }

    @Test
    fun `plain dialogue actions have no debug line`() {
        val action = DialogueAction(
            condition = null,
            speaker = "Alice",
            text = listOf(TextSegment.Literal("hi")),
            clickCommand = null,
        )
        val component = renderer.render(action) { error("Inline command must not be called") }!!
        assertEquals("Alice: hi", component.string)
    }

    @Test
    fun `dialogue actions stringify to script text`() {
        val action = DialogueAction(
            condition = Condition("done"),
            speaker = "Alice",
            text = listOf(
                TextSegment.Literal("cost "),
                TextSegment.Color('c'),
                TextSegment.Literal("5"),
                TextSegment.InlineCommand(listOf("get score")),
            ),
            clickCommand = CommandInvocation.Raw(
                CommandCall(commands = listOf("say hi", "say bye"), selector = "@a"),
            ),
        )
        assertEquals("(done) Alice: cost &c5\$get score\$ [say hi; say bye @a]", action.toString())
    }
}
