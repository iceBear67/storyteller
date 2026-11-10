package io.ib67.mods.storyteller

fun interface DebugToggleListener {
    fun onDebugToggled(enabled: Boolean)
}

class DebugToggleEvent {

    private val listeners = mutableListOf<DebugToggleListener>()

    @Volatile
    var enabled: Boolean = false
        private set

    fun register(listener: DebugToggleListener) {
        listeners += listener
    }

    fun toggle() {
        enabled = !enabled
        val current = enabled
        for (listener in listeners) {
            listener.onDebugToggled(current)
        }
    }
}
