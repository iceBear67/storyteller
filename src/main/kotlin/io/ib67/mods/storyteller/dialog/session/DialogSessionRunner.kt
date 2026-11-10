package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.DividerAction
import io.ib67.mods.storyteller.dialog.TextSegment
import io.ib67.mods.storyteller.dialog.WaitAction
import io.ib67.mods.storyteller.DebugToggleEvent
import io.ib67.mods.storyteller.LOGGER
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.ConditionEvaluationException
import io.ib67.mods.storyteller.dialog.runner.ConditionEvaluator
import io.ib67.mods.storyteller.dialog.runner.DebugDialogueTextRenderer
import io.ib67.mods.storyteller.dialog.runner.DialogueTextRenderer
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

class DialogSessionRunner(
    private val conditionEvaluator: ConditionEvaluator,
    private val commandExecutor: CommandExecutor,
    private val textRenderer: DialogueTextRenderer,
    private val debugToggle: DebugToggleEvent,
    private val stateManager: StateManager,
    private val director: DialogDirector,
    serverTick: Event<ServerTickEvents.EndTick>,
) {

    private val debugTextRenderer = DebugDialogueTextRenderer(textRenderer)
    private var renderer: DialogueTextRenderer = textRenderer

    init {
        debugToggle.register { enabled -> renderer = if (enabled) debugTextRenderer else textRenderer }
        serverTick.register(::tick)
    }

    private fun tick(server: MinecraftServer) {
        val now = server.overworld().gameTime
        for (player in server.playerList.players.toList()) {
            if (player.hasDisconnected()) {
                continue
            }
            val state = stateManager.playerState(player)
            if (state.isSuspended) {
                continue
            }
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
            val completed = tickAction(server, player, frame, action, frame.actionTicks ?: 0)
            if (state.isSuspended) {
                continue
            }
            director.applyActionProgress(state, frame, completed, now)
        }
    }

    private fun tickAction(
        server: MinecraftServer,
        player: ServerPlayer,
        frame: DialogFrame,
        action: Action,
        ticksSinceAction: Int,
    ): Boolean =
        try {
            when (action) {
                is DividerAction -> throw IllegalArgumentException("Divider cannot be executed as an action: ${action.name}")
                is DialogueAction -> tickDialogue(player, action, ticksSinceAction)
                is CommandAction -> tickCommand(player, action, ticksSinceAction)
                is WaitAction -> tickWait(player, action, ticksSinceAction)
            }
        } catch (exception: ConditionEvaluationException) {
            stateManager.playerState(player).markSuspended()
            val reason = "${frame.scriptId}/${frame.dialogId} action ${frame.actionIndex}: ${exception.message}"
            LOGGER.error("Condition evaluation failed for {} at {}", player.gameProfile.name, reason, exception)
            if (debugToggle.enabled) {
                val message = Component.literal("[Storyteller] Condition evaluation failed: $reason")
                    .withStyle(ChatFormatting.RED)
                for (online in server.playerList.players) {
                    online.sendSystemMessage(message)
                }
            }
            false
        }

    private fun tickDialogue(player: ServerPlayer, action: DialogueAction, ticksSinceAction: Int): Boolean {
        if (ticksSinceAction == 0) {
            if (!passes(player, action.condition)) {
                return true
            }
            sendDialogue(player, action)
        }
        var length = 0
        val speaker = action.speaker
        if (speaker != null) {
            length += speaker.length + 2
        }
        for (segment in action.text) {
            if (segment is TextSegment.Literal) {
                length += segment.text.length
            }
        }
        return ticksSinceAction + 1 >= (20 + length * 1).coerceAtMost(200)
    }

    private fun tickCommand(player: ServerPlayer, action: CommandAction, ticksSinceAction: Int): Boolean {
        if (ticksSinceAction == 0 && !passes(player, action.condition)) {
            return true
        }
        val call = when (val invocation = action.invocation) {
            is CommandInvocation.Tokenized -> CommandTokenRegistry.commandFor(invocation.token)
                ?: throw IllegalStateException("Unknown command token: ${invocation.token}")
            is CommandInvocation.Raw -> throw IllegalStateException("Command action is not tokenized")
        }
        if (ticksSinceAction >= call.commands.size) {
            return true
        }
        if (!commandExecutor.run(player, call.selector, call.commands[ticksSinceAction])) {
            return true
        }
        return ticksSinceAction + 1 >= call.commands.size
    }

    private fun tickWait(player: ServerPlayer, action: WaitAction, ticksSinceAction: Int): Boolean =
        ticksSinceAction == 0 && !passes(player, action.condition) ||
            action.timeoutTicks != null && ticksSinceAction + 1 >= action.timeoutTicks

    private fun sendDialogue(player: ServerPlayer, action: DialogueAction) {
        val component = renderer.render(action) { commands -> commandExecutor.capture(player, commands) }
        val clickCommand = action.clickCommand
        if (clickCommand != null) {
            val token = when (clickCommand) {
                is CommandInvocation.Tokenized -> clickCommand.token
                is CommandInvocation.Raw -> throw IllegalStateException("Click command is not tokenized")
            }
            component.style = component.style.withClickEvent(
                ClickEvent.RunCommand("storyteller:run_action $token"),
            )
        }
        player.sendSystemMessage(component)
    }

    private fun passes(player: ServerPlayer, condition: Condition?): Boolean {
        if (condition == null) {
            return true
        }
        val variables = HashMap<String, Any>(stateManager.playerState(player).namedValues())
        variables["global"] = stateManager.globalState.namedValues()
        return conditionEvaluator.evaluate(variables, condition)
    }
}
