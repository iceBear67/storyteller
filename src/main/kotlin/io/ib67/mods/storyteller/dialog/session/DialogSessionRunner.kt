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
            "对话不属于脚本：${script.id}/${dialog.id}"
        }
        val state = player.storyState
        state.currentScriptId = script.id
        state.currentDialogId = dialog.id
        state.actionIndex = 0
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
        checkNotNull(state.context[SESSION_INTERVAL_TICKS]) { "缺少对话间隔" }
        state.context[SESSION_NEXT_ACTION_TICK] = player.level().gameTime
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
            val nextActionTick = checkNotNull(state.context[SESSION_NEXT_ACTION_TICK]) {
                "缺少下一次动作的执行时间"
            }
            if (now < nextActionTick) {
                continue
            }
            val dialogIdBefore = state.currentDialogId
            val indexBefore = state.actionIndex
            actionRunner.run(player, dialog.actions[index])
            if (state.currentDialogId != dialogIdBefore || state.actionIndex != indexBefore) {
                continue
            }
            state.actionIndex = indexBefore + 1
            if (state.actionIndex < dialog.actions.size) {
                val interval = checkNotNull(state.context[SESSION_INTERVAL_TICKS]) { "缺少对话间隔" }
                check(interval >= 0) { "对话间隔不能为负数：$interval" }
                state.context[SESSION_NEXT_ACTION_TICK] = now + interval
            }
        }
    }

    fun stop(player: ServerPlayer) {
        val state = player.storyState
        state.currentScriptId = null
        state.currentDialogId = null
        state.actionIndex = 0
        state.context.remove(SESSION_INTERVAL_TICKS)
        state.context.remove(SESSION_NEXT_ACTION_TICK)
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
    state.context.remove(SESSION_INTERVAL_TICKS)
    state.context.remove(SESSION_NEXT_ACTION_TICK)
}
