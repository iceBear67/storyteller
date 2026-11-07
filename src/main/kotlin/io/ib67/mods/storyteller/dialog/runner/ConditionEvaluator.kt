package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Condition
import net.minecraft.server.level.ServerPlayer

fun interface ConditionEvaluator {
    fun evaluate(player: ServerPlayer, condition: Condition): Boolean
}
