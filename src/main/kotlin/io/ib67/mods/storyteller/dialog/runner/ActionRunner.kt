package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.DividerAction
import net.minecraft.network.chat.ClickEvent
import net.minecraft.server.level.ServerPlayer

class ActionRunner(
    private val conditionEvaluator: ConditionEvaluator,
    private val commandExecutor: CommandExecutor,
    private val textRenderer: DialogueTextRenderer,
) {

    fun run(player: ServerPlayer, action: Action) {
        when (action) {
            is DividerAction -> throw IllegalArgumentException("分界线不能作为动作执行：${action.name}")
            is DialogueAction -> runDialogue(player, action)
            is CommandAction -> runCommand(player, action)
        }
    }

    fun run(player: ServerPlayer, dialog: Dialog) {
        for (action in dialog.actions) {
            run(player, action)
        }
    }

    private fun runDialogue(player: ServerPlayer, action: DialogueAction) {
        if (!passes(player, action.condition)) {
            return
        }
        val component = textRenderer.render(action) { commands -> commandExecutor.capture(player, commands) }
        val clickCommand = action.clickCommand
        if (clickCommand != null) {
            component.setStyle(
                component.style.withClickEvent(ClickEvent.RunCommand(commandExecutor.clickCommand(clickCommand))),
            )
        }
        player.sendSystemMessage(component)
    }

    private fun runCommand(player: ServerPlayer, action: CommandAction) {
        if (!passes(player, action.condition)) {
            return
        }
        commandExecutor.run(player, action.selector, action.commands)
    }

    private fun passes(player: ServerPlayer, condition: Condition?): Boolean =
        condition == null || conditionEvaluator.evaluate(player, condition)
}
