package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class DebugDialogueTextRenderer(
    private val delegate: DialogueTextRenderer,
) : DialogueTextRenderer {

    override fun render(action: DialogueAction, inlineOutput: (List<String>) -> String): MutableComponent =
        delegate.render(action, inlineOutput)
            .append(Component.literal("\n  ↘ $action").withStyle(ChatFormatting.GRAY))
}
