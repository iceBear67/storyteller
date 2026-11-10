package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style

class DefaultDialogueTextRenderer : DialogueTextRenderer {

    private val speakerColors = mutableMapOf<String, ChatFormatting>()
    private var nextSpeakerColor = 0

    override fun render(action: DialogueAction, inlineOutput: (List<String>) -> String): MutableComponent {
        val result = Component.empty()
        val baseStyle = Style.EMPTY.applyLegacyFormat(
            if (action.speaker == null) ChatFormatting.GRAY else ChatFormatting.WHITE,
        )
        action.speaker?.let { speaker ->
            result.append(Component.literal("$speaker: ").withStyle(speakerColor(speaker)))
        }
        var style = baseStyle
        for (segment in action.text) {
            when (segment) {
                is TextSegment.Literal -> result.append(Component.literal(segment.text).setStyle(style))
                is TextSegment.Color -> {
                    val format = checkNotNull(ChatFormatting.getByCode(segment.code)) {
                        "Invalid color code: ${segment.code}"
                    }
                    style = if (format == ChatFormatting.RESET) baseStyle else style.applyLegacyFormat(format)
                }
                is TextSegment.InlineCommand -> result.append(
                    Component.literal(inlineOutput(segment.commands)).setStyle(style),
                )
            }
        }
        return result
    }

    private fun speakerColor(speaker: String): ChatFormatting =
        speakerColors.getOrPut(speaker) {
            SPEAKER_COLORS[nextSpeakerColor++ % SPEAKER_COLORS.size]
        }

    private companion object {
        val SPEAKER_COLORS = listOf(
            ChatFormatting.AQUA,
            ChatFormatting.GOLD,
            ChatFormatting.GREEN,
            ChatFormatting.LIGHT_PURPLE,
            ChatFormatting.RED,
            ChatFormatting.YELLOW,
            ChatFormatting.BLUE,
        )
    }
}
