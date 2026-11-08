package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.util.TypedKey

object KnownKeys {

    val SESSION_INTERVAL_TICKS: TypedKey<Long> = TypedKey.of("sessionIntervalTicks")

    val SESSION_NEXT_ACTION_TICK: TypedKey<Long> = TypedKey.of("sessionNextActionTick")
}
