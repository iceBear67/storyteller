package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Condition

fun interface ConditionEvaluator {
    fun evaluate(variables: Map<String, Any>, condition: Condition): Boolean
}
