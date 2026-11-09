package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.KnownKeys.DIALOG_STACK
import io.ib67.mods.storyteller.dialog.session.DialogFrame
import io.ib67.mods.storyteller.util.TypedKeyMap

class PlayerState : TypedKeyMap() {

    val dialogFrames: List<DialogFrame>
        get() = this[DIALOG_STACK] ?: emptyList()

    val currentFrame: DialogFrame?
        get() = dialogFrames.lastOrNull()

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
        currentFrame?.let { frame ->
            values["currentScriptId"] = frame.scriptId
            values["currentDialogId"] = frame.dialogId
            values["actionIndex"] = frame.actionIndex
            frame.actionTicks?.let { values["actionTicks"] = it }
            values["dialogReloadId"] = frame.reloadId
            values["sessionIntervalTicks"] = frame.intervalTicks
            values["sessionNextActionTick"] = frame.nextActionTick
        }
        return values
    }
}
