package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.DividerAction
import io.ib67.mods.storyteller.dialog.TextSegment
import io.ib67.mods.storyteller.dialog.WaitAction
import io.ib67.mods.storyteller.state.StateManager
import net.minecraft.network.chat.ClickEvent
import net.minecraft.server.level.ServerPlayer

internal fun dialogueDuration(action: DialogueAction): Int {
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
    return (20 + length * 1)
        .coerceAtMost(200)
}

internal fun waitCompleted(timeoutTicks: Int?, ticksSinceAction: Int): Boolean =
    timeoutTicks != null && ticksSinceAction + 1 >= timeoutTicks

class ActionRunner(
    private val conditionEvaluator: ConditionEvaluator,
    private val commandExecutor: CommandExecutor,
    private val textRenderer: DialogueTextRenderer,
    private val stateManager: StateManager,
) {

    fun tick(player: ServerPlayer, action: Action, ticksSinceAction: Int): Boolean =
        when (action) {
            is DividerAction -> throw IllegalArgumentException("Divider cannot be executed as an action: ${action.name}")
            is DialogueAction -> tickDialogue(player, action, ticksSinceAction)
            is CommandAction -> tickCommand(player, action, ticksSinceAction)
            is WaitAction -> tickWait(player, action, ticksSinceAction)
        }

    private fun tickDialogue(player: ServerPlayer, action: DialogueAction, ticksSinceAction: Int): Boolean {
        if (ticksSinceAction == 0) {
            if (!passes(player, action.condition)) {
                return true
            }
            sendDialogue(player, action)
        }
        return ticksSinceAction + 1 >= dialogueDuration(action)
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
        commandExecutor.run(player, call.selector, call.commands[ticksSinceAction])
        return ticksSinceAction + 1 >= call.commands.size
    }

    private fun tickWait(player: ServerPlayer, action: WaitAction, ticksSinceAction: Int): Boolean =
        ticksSinceAction == 0 && !passes(player, action.condition) ||
            waitCompleted(action.timeoutTicks, ticksSinceAction)

    private fun sendDialogue(player: ServerPlayer, action: DialogueAction) {
        val component = textRenderer.render(action) { commands -> commandExecutor.capture(player, commands) }
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
