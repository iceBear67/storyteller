package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Action
import net.minecraft.network.chat.MutableComponent

fun interface ActionRenderer {

    fun render(action: Action, inlineOutput: (List<String>) -> String): MutableComponent?
}
