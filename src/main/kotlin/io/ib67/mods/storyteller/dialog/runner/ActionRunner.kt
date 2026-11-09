package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.DividerAction
import io.ib67.mods.storyteller.dialog.TextSegment
import io.ib67.mods.storyteller.state.StateManager
import net.minecraft.network.chat.ClickEvent
import net.minecraft.server.level.ServerPlayer

private const val DIALOGUE_BASE_TICKS = 20
private const val DIALOGUE_TICKS_PER_CHARACTER = 1
private const val DIALOGUE_MAX_TICKS = 200

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
    return (DIALOGUE_BASE_TICKS + length * DIALOGUE_TICKS_PER_CHARACTER)
        .coerceAtMost(DIALOGUE_MAX_TICKS)
}

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
        if (ticksSinceAction >= action.commands.size) {
            return true
        }
        commandExecutor.run(player, action.selector, action.commands[ticksSinceAction])
        return ticksSinceAction + 1 >= action.commands.size
    }

    private fun sendDialogue(player: ServerPlayer, action: DialogueAction) {
        val component = textRenderer.render(action) { commands -> commandExecutor.capture(player, commands) }
        val clickCommand = action.clickCommand
        if (clickCommand != null) {
            val token = DialogScriptRegistry.tokenFor(clickCommand)
            component.setStyle(
                component.style.withClickEvent(ClickEvent.RunCommand("$RUN_ACTION_COMMAND $token")),
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
