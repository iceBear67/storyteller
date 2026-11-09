package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.util.TypedKey

object KnownKeys {

    val CURRENT_SCRIPT_ID: TypedKey<String> = TypedKey.of("currentScriptId")

    val CURRENT_DIALOG_ID: TypedKey<String> = TypedKey.of("currentDialogId")

    val ACTION_INDEX: TypedKey<Int> = TypedKey.of("actionIndex")

    val ACTION_TICKS: TypedKey<Int> = TypedKey.of("actionTicks")

    val DIALOG_RELOAD_ID: TypedKey<Long> = TypedKey.of("dialogReloadId")

    val SESSION_INTERVAL_TICKS: TypedKey<Long> = TypedKey.of("sessionIntervalTicks")

    val SESSION_NEXT_ACTION_TICK: TypedKey<Long> = TypedKey.of("sessionNextActionTick")
}
