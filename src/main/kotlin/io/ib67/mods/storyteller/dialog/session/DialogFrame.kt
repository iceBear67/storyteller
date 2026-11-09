package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.dialog.DialogRef

data class DialogFrame(
    val scriptId: String,
    val dialogId: String,
    val actionIndex: Int,
    val actionTicks: Int?,
    val intervalTicks: Long,
    val nextActionTick: Long,
    var allowedContinuation: List<DialogRef> = emptyList(),
)
