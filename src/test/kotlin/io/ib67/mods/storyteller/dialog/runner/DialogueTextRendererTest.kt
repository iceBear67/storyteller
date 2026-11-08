package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DialogueTextRendererTest {

    private val renderer = DialogueTextRenderer()

    @Test
    fun `speaker and literal text are rendered`() {
        val action = DialogueAction(
            condition = null,
            speaker = "Alice",
            text = listOf(TextSegment.Literal("Hello, world")),
            clickCommand = null,
        )
        assertEquals(
            Component.empty()
                .append(Component.literal("Alice: "))
                .append(Component.literal("Hello, world")),
            renderer.render(action) { error("Inline command must not be called") },
        )
    }

    @Test
    fun `color codes change style`() {
        val action = DialogueAction(
            condition = null,
            speaker = "Alice",
            text = listOf(
                TextSegment.Literal("red"),
                TextSegment.Color('c'),
                TextSegment.Literal("text"),
                TextSegment.Color('r'),
                TextSegment.Literal("end"),
            ),
            clickCommand = null,
        )
        assertEquals(
            Component.empty()
                .append(Component.literal("Alice: "))
                .append(Component.literal("red"))
                .append(Component.literal("text").setStyle(Style.EMPTY.applyLegacyFormat(ChatFormatting.RED)))
                .append(Component.literal("end")),
            renderer.render(action) { error("Inline command must not be called") },
        )
    }
}
