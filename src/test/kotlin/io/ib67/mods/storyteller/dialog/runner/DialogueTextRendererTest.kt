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
            text = listOf(TextSegment.Literal("你好，世界")),
            clickCommand = null,
        )
        assertEquals(
            Component.empty()
                .append(Component.literal("Alice: "))
                .append(Component.literal("你好，世界")),
            renderer.render(action) { error("不应调用内联命令") },
        )
    }

    @Test
    fun `color codes change style`() {
        val action = DialogueAction(
            condition = null,
            speaker = "Alice",
            text = listOf(
                TextSegment.Literal("红色"),
                TextSegment.Color('c'),
                TextSegment.Literal("文本"),
                TextSegment.Color('r'),
                TextSegment.Literal("结束"),
            ),
            clickCommand = null,
        )
        assertEquals(
            Component.empty()
                .append(Component.literal("Alice: "))
                .append(Component.literal("红色"))
                .append(Component.literal("文本").setStyle(Style.EMPTY.applyLegacyFormat(ChatFormatting.RED)))
                .append(Component.literal("结束")),
            renderer.render(action) { error("不应调用内联命令") },
        )
    }
}
