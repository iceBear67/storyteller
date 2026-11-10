package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import net.minecraft.network.chat.MutableComponent

fun interface DialogueTextRenderer {

    fun render(action: DialogueAction, inlineOutput: (List<String>) -> String): MutableComponent
}
