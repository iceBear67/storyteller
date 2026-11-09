package io.ib67.mods.storyteller.dialog.pass

import io.ib67.mods.storyteller.dialog.DialogScript

interface DialogScriptPass {
    val priority: Int

    fun apply(script: DialogScript): DialogScript
}
