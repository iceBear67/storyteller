package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Condition
import net.minecraft.server.level.ServerPlayer

class StubConditionEvaluator : ConditionEvaluator {

    override fun evaluate(player: ServerPlayer, condition: Condition): Boolean =
        TODO("条件求值尚未实现：${condition.expression}")
}
