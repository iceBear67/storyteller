package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.runner.ActionRunner
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.MinecraftServer

class DialogSessionRunner(
    private val actionRunner: ActionRunner,
    private val stateManager: StateManager,
    private val director: DialogDirector,
    serverTick: Event<ServerTickEvents.EndTick>,
) {

    init {
        serverTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        for (player in server.playerList.players) {
            val state = stateManager.playerState(player)
            val frame = director.currentFrame(state) ?: continue
            val dialog = checkNotNull(DialogScriptRegistry.get(frame.scriptId)?.resolve(frame.dialogId)) {
                "Resolved frame has no dialog: ${frame.scriptId}/${frame.dialogId}"
            }
            if (frame.actionIndex >= dialog.actions.size) {
                director.popDialog(state)
                continue
            }
            if (frame.actionTicks == null && now < frame.nextActionTick) {
                continue
            }
            val action = dialog.actions[frame.actionIndex]
            if (frame.actionTicks == null) {
                val token = ((action as? DialogueAction)?.clickCommand as? CommandInvocation.Tokenized)?.token
                if (token != null) {
                    director.recordPoppedCommand(state, frame, token)
                }
            }
            val completed = actionRunner.tick(player, action, frame.actionTicks ?: 0)
            director.applyActionProgress(state, frame, completed, now)
        }
    }
}
