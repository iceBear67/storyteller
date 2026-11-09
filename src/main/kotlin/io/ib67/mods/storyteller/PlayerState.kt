package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.KnownKeys.ACTION_INDEX
import io.ib67.mods.storyteller.KnownKeys.ACTION_TICKS
import io.ib67.mods.storyteller.KnownKeys.CURRENT_DIALOG_ID
import io.ib67.mods.storyteller.KnownKeys.CURRENT_SCRIPT_ID
import io.ib67.mods.storyteller.KnownKeys.DIALOG_RELOAD_ID
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.util.TypedKeyMap

class PlayerState : TypedKeyMap() {

    var currentScriptId: String?
        get() = this[CURRENT_SCRIPT_ID]
        set(value) {
            if (value == null) {
                remove(CURRENT_SCRIPT_ID)
            } else {
                this[CURRENT_SCRIPT_ID] = value
            }
        }

    var currentDialogId: String?
        get() = this[CURRENT_DIALOG_ID]
        set(value) {
            if (value == null) {
                remove(CURRENT_DIALOG_ID)
            } else {
                this[CURRENT_DIALOG_ID] = value
            }
        }

    var actionIndex: Int
        get() = this[ACTION_INDEX] ?: 0
        set(value) {
            this[ACTION_INDEX] = value
        }

    var actionTicks: Int?
        get() = this[ACTION_TICKS]
        set(value) {
            if (value == null) {
                remove(ACTION_TICKS)
            } else {
                this[ACTION_TICKS] = value
            }
        }

    var dialogReloadId: Long
        get() = this[DIALOG_RELOAD_ID] ?: 0L
        set(value) {
            this[DIALOG_RELOAD_ID] = value
        }

    val currentScript: DialogScript?
        get() = currentScriptId?.let(DialogScriptRegistry::get)

    val currentDialog: Dialog?
        get() {
            val script = currentScript ?: return null
            val id = currentDialogId ?: return null
            return if (id == script.id) script.defaultDialog else script.subDialogs[id]
        }
}
