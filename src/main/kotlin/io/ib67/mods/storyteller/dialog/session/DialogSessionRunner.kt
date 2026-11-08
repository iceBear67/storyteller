package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.KnownKeys.SESSION_INTERVAL_TICKS
import io.ib67.mods.storyteller.KnownKeys.SESSION_NEXT_ACTION_TICK
import io.ib67.mods.storyteller.PlayerState
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ActionRunner
import io.ib67.mods.storyteller.storyState
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class DialogSessionRunner(
    private val actionRunner: ActionRunner,
    private val initialIntervalTicks: () -> Long,
) {

    fun enter(player: ServerPlayer, script: DialogScript, dialog: Dialog) {
        check(script.defaultDialog === dialog || script.subDialogs[dialog.id] === dialog) {
            "Dialog does not belong to script: ${script.id}/${dialog.id}"
        }
        val state = player.storyState
        state.currentScriptId = script.id
        state.currentDialogId = dialog.id
        state.actionIndex = 0
        state.actionTicks = null
        state.dialogReloadId = DialogScriptRegistry.reloadId
        state.context[SESSION_INTERVAL_TICKS] = initialIntervalTicks()
        state.context[SESSION_NEXT_ACTION_TICK] = player.level().gameTime
    }

    fun onJoin(player: ServerPlayer) {
        val state = player.storyState
        val dialog = resolveActiveDialog(state) ?: return
        if (state.actionIndex >= dialog.actions.size) {
            return
        }
        checkNotNull(state.context[SESSION_INTERVAL_TICKS]) { "Missing dialog interval" }
        if (state.actionTicks == null && state.context[SESSION_NEXT_ACTION_TICK] == null) {
            state.context[SESSION_NEXT_ACTION_TICK] = player.level().gameTime
        }
    }

    fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        for (player in server.playerList.players) {
            val state = player.storyState
            val dialog = resolveActiveDialog(state) ?: continue
            val index = state.actionIndex
            if (index >= dialog.actions.size) {
                continue
            }
            val action = dialog.actions[index]
            val ticks = state.actionTicks
            if (ticks == null) {
                val nextActionTick = checkNotNull(state.context[SESSION_NEXT_ACTION_TICK]) {
                    "Missing next action tick"
                }
                if (now < nextActionTick) {
                    continue
                }
            }
            val dialogIdBefore = state.currentDialogId
            val indexBefore = state.actionIndex
            val completed = actionRunner.tick(player, action, ticks ?: 0)
            if (state.currentDialogId != dialogIdBefore || state.actionIndex != indexBefore) {
                continue
            }
            if (completed) {
                completeAction(state, now)
            } else {
                state.actionTicks = if (ticks == null) 1 else ticks + 1
            }
        }
    }

    fun stop(player: ServerPlayer) {
        val state = player.storyState
        state.currentScriptId = null
        state.currentDialogId = null
        state.actionIndex = 0
        state.actionTicks = null
        state.context.remove(SESSION_INTERVAL_TICKS)
        state.context.remove(SESSION_NEXT_ACTION_TICK)
    }

    private fun completeAction(state: PlayerState, now: Long) {
        state.actionTicks = null
        state.actionIndex += 1
        val interval = checkNotNull(state.context[SESSION_INTERVAL_TICKS]) { "Missing dialog interval" }
        check(interval >= 0) { "Dialog interval cannot be negative: $interval" }
        state.context[SESSION_NEXT_ACTION_TICK] = now + interval
    }
}

internal fun resolveActiveDialog(state: PlayerState): Dialog? {
    if (state.dialogReloadId != DialogScriptRegistry.reloadId) {
        clearDialogSession(state)
        state.dialogReloadId = DialogScriptRegistry.reloadId
        return null
    }
    val dialog = state.currentDialog
    if (state.currentDialogId != null && dialog == null) {
        clearDialogSession(state)
        return null
    }
    return dialog
}

internal fun clearDialogSession(state: PlayerState) {
    state.currentDialogId = null
    state.actionIndex = 0
    state.actionTicks = null
    state.context.remove(SESSION_INTERVAL_TICKS)
    state.context.remove(SESSION_NEXT_ACTION_TICK)
}
