package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class DebugActionRenderer(
    private val delegate: ActionRenderer,
) : ActionRenderer {

    override fun render(action: Action, inlineOutput: (List<String>) -> String): MutableComponent? {
        val base = delegate.render(action, inlineOutput)
        if (!needsDebug(action)) {
            return base
        }
        val debugLine = Component.literal("  ↘ $action").withStyle(ChatFormatting.GRAY)
        return if (base == null) debugLine else base.append(Component.literal("\n")).append(debugLine)
    }

    private fun needsDebug(action: Action): Boolean =
        when (action) {
            is DialogueAction -> action.condition != null ||
                action.clickCommand != null ||
                action.text.any { it !is TextSegment.Literal }
            else -> true
        }
}
