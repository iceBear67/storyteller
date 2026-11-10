package io.ib67.mods.storyteller.state

import io.ib67.mods.storyteller.KnownKeys.ACTION_INDEX
import io.ib67.mods.storyteller.KnownKeys.ACTION_TICKS
import io.ib67.mods.storyteller.KnownKeys.CURRENT_DIALOG_ID
import io.ib67.mods.storyteller.KnownKeys.CURRENT_SCRIPT_ID
import io.ib67.mods.storyteller.KnownKeys.DIALOG_SCRIPT_CHECKSUM
import io.ib67.mods.storyteller.KnownKeys.DIALOG_STACK
import io.ib67.mods.storyteller.KnownKeys.DIALOG_SUSPENDED
import io.ib67.mods.storyteller.KnownKeys.SESSION_INTERVAL_TICKS
import io.ib67.mods.storyteller.KnownKeys.SESSION_NEXT_ACTION_TICK
import io.ib67.mods.storyteller.dialog.session.DialogFrame
import io.ib67.mods.storyteller.util.TypedKeyMap

class PlayerState : TypedKeyMap() {

    val dialogFrames: List<DialogFrame>
        get() = this[DIALOG_STACK] ?: emptyList()

    val currentFrame: DialogFrame?
        get() = dialogFrames.lastOrNull()

    val isSuspended: Boolean
        get() = this[DIALOG_SUSPENDED] ?: false

    internal var dialogScriptChecksum: String?
        get() = this[DIALOG_SCRIPT_CHECKSUM]
        set(value) {
            if (value == null) {
                remove(DIALOG_SCRIPT_CHECKSUM)
            } else {
                this[DIALOG_SCRIPT_CHECKSUM] = value
            }
        }

    internal fun markSuspended() {
        this[DIALOG_SUSPENDED] = true
    }

    internal fun clearSuspension() {
        remove(DIALOG_SUSPENDED)
    }

    internal fun useScriptChecksum(checksum: String) {
        if (dialogScriptChecksum == checksum) {
            return
        }
        setFrames(emptyList())
        clearSuspension()
        dialogScriptChecksum = checksum
    }

    internal fun resetDialogState(checksum: String) {
        setFrames(emptyList())
        clearSuspension()
        if (dialogScriptChecksum != checksum) {
            dialogScriptChecksum = checksum
        }
    }

    internal fun pushFrame(frame: DialogFrame) {
        setFrames(dialogFrames + frame)
    }

    internal fun popFrame(): DialogFrame {
        val frames = dialogFrames
        check(frames.isNotEmpty()) { "No dialog frame to pop" }
        setFrames(frames.dropLast(1))
        return frames.last()
    }

    internal fun replaceFrame(expected: DialogFrame, updated: DialogFrame): Boolean {
        val frames = dialogFrames
        val index = frames.indexOfFirst { it === expected }
        if (index < 0) {
            return false
        }
        setFrames(frames.toMutableList().also { it[index] = updated })
        return true
    }

    internal fun setFrames(frames: List<DialogFrame>) {
        if (frames.isEmpty()) {
            remove(DIALOG_STACK)
        } else {
            this[DIALOG_STACK] = frames.toList()
        }
    }

    override fun namedValues(): Map<String, Any> {
        val values = LinkedHashMap(super.namedValues())
        values.remove(DIALOG_STACK.name)
        values.remove(DIALOG_SCRIPT_CHECKSUM.name)
        values.remove(DIALOG_SUSPENDED.name)
        currentFrame?.let { frame ->
            values[CURRENT_SCRIPT_ID] = frame.scriptId
            values[CURRENT_DIALOG_ID] = frame.dialogId
            values[ACTION_INDEX] = frame.actionIndex
            frame.actionTicks?.let { values[ACTION_TICKS] = it }
            values[SESSION_INTERVAL_TICKS] = frame.intervalTicks
            values[SESSION_NEXT_ACTION_TICK] = frame.nextActionTick
        }
        return values
    }
}
