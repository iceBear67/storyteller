package io.ib67.mods.storyteller.dialog

fun interface DialogScriptReloadListener {
    fun onDialogScriptsReloaded(previousChecksum: String, currentChecksum: String)
}

class DialogScriptReloadEvent {

    private val listeners = mutableListOf<DialogScriptReloadListener>()

    fun register(listener: DialogScriptReloadListener) {
        listeners += listener
    }

    internal fun fire(previousChecksum: String, currentChecksum: String) {
        for (listener in listeners) {
            listener.onDialogScriptsReloaded(previousChecksum, currentChecksum)
        }
    }
}
