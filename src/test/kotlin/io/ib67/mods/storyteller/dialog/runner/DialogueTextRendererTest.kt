package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class DialogueTextRendererTest {

    private val renderer = DefaultDialogueTextRenderer()

    @Test
    fun `speaker and literal text are rendered`() {
        val component = renderer.render(
            dialogue("Alice", listOf(TextSegment.Literal("Hello, world"))),
        ) { error("Inline command must not be called") }
        assertEquals("Alice: Hello, world", component.string)
        assertNotEquals(WHITE, component.siblings[0].style)
        assertEquals(WHITE, component.siblings[1].style)
    }

    @Test
    fun `different speakers get different colors`() {
        val alice = renderer.render(dialogue("Alice", listOf(TextSegment.Literal("hi")))) {
            error("Inline command must not be called")
        }
        val bob = renderer.render(dialogue("Bob", listOf(TextSegment.Literal("hi")))) {
            error("Inline command must not be called")
        }
        val aliceAgain = renderer.render(dialogue("Alice", listOf(TextSegment.Literal("hi")))) {
            error("Inline command must not be called")
        }
        assertNotEquals(alice.siblings[0].style, bob.siblings[0].style)
        assertEquals(alice.siblings[0].style, aliceAgain.siblings[0].style)
    }

    @Test
    fun `system messages are gray`() {
        val component = renderer.render(
            dialogue(null, listOf(TextSegment.Literal("It is raining"))),
        ) { error("Inline command must not be called") }
        assertEquals("It is raining", component.string)
        assertEquals(GRAY, component.siblings[0].style)
    }

    @Test
    fun `color codes change style`() {
        val component = renderer.render(
            dialogue(
                "Alice",
                listOf(
                    TextSegment.Literal("red"),
                    TextSegment.Color('c'),
                    TextSegment.Literal("text"),
                    TextSegment.Color('r'),
                    TextSegment.Literal("end"),
                ),
            ),
        ) { error("Inline command must not be called") }
        assertEquals("Alice: redtextend", component.string)
        assertEquals(WHITE, component.siblings[1].style)
        assertEquals(RED, component.siblings[2].style)
        assertEquals(WHITE, component.siblings[3].style)
    }

    @Test
    fun `inline command output is appended`() {
        val component = renderer.render(
            dialogue(
                "Alice",
                listOf(TextSegment.Literal("Score: "), TextSegment.InlineCommand(listOf("get score"))),
            ),
        ) { commands ->
            assertEquals(listOf("get score"), commands)
            "42"
        }
        assertEquals("Alice: Score: 42", component.string)
        assertEquals(WHITE, component.siblings[1].style)
        assertEquals(WHITE, component.siblings[2].style)
    }

    @Test
    fun `formatting codes change style`() {
        val component = renderer.render(
            dialogue(
                null,
                listOf(
                    TextSegment.Color('l'),
                    TextSegment.Literal("bold"),
                    TextSegment.Color('r'),
                    TextSegment.Literal("plain"),
                ),
            ),
        ) { error("Inline command must not be called") }
        assertEquals("boldplain", component.string)
        assertEquals(GRAY_BOLD, component.siblings[0].style)
        assertEquals(GRAY, component.siblings[1].style)
    }

    private fun dialogue(speaker: String?, text: List<TextSegment>): DialogueAction =
        DialogueAction(condition = null, speaker = speaker, text = text, clickCommand = null)

    private companion object {
        val WHITE: Style = Style.EMPTY.applyLegacyFormat(ChatFormatting.WHITE)
        val GRAY: Style = Style.EMPTY.applyLegacyFormat(ChatFormatting.GRAY)
        val RED: Style = Style.EMPTY.applyLegacyFormat(ChatFormatting.RED)
        val GRAY_BOLD: Style = GRAY.applyLegacyFormat(ChatFormatting.BOLD)
    }
}
