package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Condition
import net.minecraft.server.level.ServerPlayer

class StubConditionEvaluator : ConditionEvaluator {

    override fun evaluate(player: ServerPlayer, condition: Condition): Boolean =
        TODO("Condition evaluation is not implemented: ${condition.expression}")
}
