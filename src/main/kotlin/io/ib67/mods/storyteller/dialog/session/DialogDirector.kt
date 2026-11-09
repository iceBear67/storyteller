package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.PlayerState
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogRef
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.state.StateManager
import net.minecraft.server.level.ServerPlayer

class DialogDirector internal constructor(
    private val stateOf: (ServerPlayer) -> PlayerState,
    private val initialIntervalTicks: () -> Long,
) {

    constructor(stateManager: StateManager, initialIntervalTicks: () -> Long) :
        this(stateManager::playerState, initialIntervalTicks)

    fun pushDialog(
        player: ServerPlayer,
        ref: DialogRef,
        allowedContinuation: List<DialogRef> = emptyList(),
    ) = pushDialog(stateOf(player), ref, player.level().gameTime, allowedContinuation)

    fun pushDialog(
        player: ServerPlayer,
        script: DialogScript,
        dialog: Dialog,
        allowedContinuation: List<DialogRef> = emptyList(),
    ) = pushDialog(stateOf(player), script, dialog, player.level().gameTime, allowedContinuation)

    fun popDialog(player: ServerPlayer) = popDialog(stateOf(player))

    fun clearDialogs(player: ServerPlayer) = clearDialogs(stateOf(player))

    fun currentFrame(player: ServerPlayer): DialogFrame? = stateOf(player).currentFrame

    fun canPushDialog(player: ServerPlayer, ref: DialogRef): Boolean =
        canPushDialog(stateOf(player), ref)

    fun setAllowedContinuation(player: ServerPlayer, refs: List<DialogRef>) =
        setAllowedContinuation(stateOf(player), refs)

    internal fun setAllowedContinuation(state: PlayerState, refs: List<DialogRef>) {
        val frame = checkNotNull(state.currentFrame) { "No active dialog frame" }
        frame.allowedContinuation = refs.toList()
        state.markDirty()
    }

    internal fun currentFrame(state: PlayerState): DialogFrame? = state.currentFrame

    internal fun pushDialog(
        state: PlayerState,
        ref: DialogRef,
        now: Long,
        allowedContinuation: List<DialogRef> = emptyList(),
    ) {
        val script = checkNotNull(DialogScriptRegistry.get(ref.scriptId)) {
            "Unknown dialog script: ${ref.scriptId}"
        }
        val dialog = checkNotNull(script.resolve(ref.dialogId)) {
            "Unknown dialog: ${ref.scriptId}/${ref.dialogId}"
        }
        pushDialog(state, script, dialog, now, allowedContinuation)
    }

    internal fun pushDialog(
        state: PlayerState,
        script: DialogScript,
        dialog: Dialog,
        now: Long,
        allowedContinuation: List<DialogRef> = emptyList(),
    ) {
        check(script.defaultDialog === dialog || script.subDialogs[dialog.id] === dialog) {
            "Dialog does not belong to script: ${script.id}/${dialog.id}"
        }
        val target = DialogRef(script.id, dialog.id)
        val current = state.currentFrame
        if (current != null && current.allowedContinuation.isNotEmpty()) {
            check(target in current.allowedContinuation) {
                "Dialog $target is not allowed by ${current.scriptId}/${current.dialogId}"
            }
        }
        val interval = initialIntervalTicks()
        check(interval >= 0) { "Dialog interval cannot be negative: $interval" }
        state.pushFrame(
            DialogFrame(
                scriptId = script.id,
                dialogId = dialog.id,
                actionIndex = 0,
                actionTicks = null,
                intervalTicks = interval,
                nextActionTick = now,
                allowedContinuation = allowedContinuation.toList(),
            ),
        )
    }

    internal fun popDialog(state: PlayerState) {
        state.popFrame()
    }

    internal fun clearDialogs(state: PlayerState) {
        state.setFrames(emptyList())
    }

    internal fun canPushDialog(state: PlayerState, ref: DialogRef): Boolean {
        val frame = state.currentFrame ?: return true
        return frame.allowedContinuation.isEmpty() || ref in frame.allowedContinuation
    }

    internal fun applyActionProgress(
        state: PlayerState,
        expected: DialogFrame,
        completed: Boolean,
        now: Long,
    ): Boolean {
        val updated = if (completed) {
            expected.copy(
                actionIndex = expected.actionIndex + 1,
                actionTicks = null,
                nextActionTick = now + expected.intervalTicks,
            )
        } else {
            expected.copy(actionTicks = (expected.actionTicks ?: 0) + 1)
        }
        return state.replaceFrame(expected, updated)
    }
}
