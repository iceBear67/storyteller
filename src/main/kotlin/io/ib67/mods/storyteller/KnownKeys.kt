package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.session.DialogFrame
import io.ib67.mods.storyteller.util.TypedKey

object KnownKeys {

    val DIALOG_STACK: TypedKey<List<DialogFrame>> = TypedKey.of("dialogStack")

    val DIALOG_SCRIPT_CHECKSUM: TypedKey<String> = TypedKey.of("dialogScriptChecksum")

    val DIALOG_INTERVAL_TICKS: TypedKey<Number> = TypedKey.of("dialogIntervalTicks")
}
