package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

class DialogueTextRenderer {

    fun render(action: DialogueAction, inlineOutput: (List<String>) -> String): MutableComponent {
        val result = Component.empty()
        if (action.speaker != null) {
            result.append(Component.literal("${action.speaker}: "))
        }
        var style = Style.EMPTY
        for (segment in action.text) {
            when (segment) {
                is TextSegment.Literal -> result.append(Component.literal(segment.text).setStyle(style))
                is TextSegment.Color -> style = style.applyLegacyFormat(
                    checkNotNull(ChatFormatting.getByCode(segment.code)) { "Invalid color code: ${segment.code}" },
                )
                is TextSegment.InlineCommand -> result.append(
                    Component.literal(inlineOutput(segment.commands)).setStyle(style),
                )
            }
        }
        return result
    }
}
