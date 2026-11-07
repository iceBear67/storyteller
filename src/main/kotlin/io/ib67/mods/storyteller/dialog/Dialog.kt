package io.ib67.mods.storyteller.dialog

data class Dialog(
    val actions: List<Action>,
    val id: String
)

data class DialogScript(
    val id: String,
    val subDialogs: Map<String, Dialog>,
    val defaultDialog: Dialog
)