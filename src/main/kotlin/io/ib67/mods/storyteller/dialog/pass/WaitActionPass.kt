package io.ib67.mods.storyteller.dialog.pass

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.WAIT_COMMAND
import io.ib67.mods.storyteller.dialog.WaitAction
import io.ib67.mods.storyteller.dialog.firstWord
import io.ib67.mods.storyteller.dialog.flowWord

class WaitActionPass : DialogScriptPass {

    override val priority: Int = 100

    override fun apply(script: DialogScript): DialogScript =
        script.copy(
            defaultDialog = applyToDialog(script.defaultDialog),
            subDialogs = script.subDialogs.mapValues { (_, dialog) -> applyToDialog(dialog) },
        )

    private fun applyToDialog(dialog: Dialog): Dialog =
        dialog.copy(actions = dialog.actions.flatMap(::applyToAction))

    private fun applyToAction(action: Action): List<Action> =
        when (action) {
            is CommandAction -> expandCommand(action)
            is DialogueAction -> listOf(truncateClickCommand(action))
            else -> listOf(action)
        }

    private fun expandCommand(action: CommandAction): List<Action> {
        val invocation = action.invocation
        check(invocation is CommandInvocation.Raw) { "Command action is already tokenized" }
        val commands = truncateAfterReturn(invocation.call.commands)
        if (commands.none { firstWord(it) == WAIT_COMMAND }) {
            return listOf(
                if (commands === invocation.call.commands) {
                    action
                } else {
                    action.copy(invocation = CommandInvocation.Raw(invocation.call.copy(commands = commands)))
                },
            )
        }
        val expanded = mutableListOf<Action>()
        var start = 0
        for (index in commands.indices) {
            if (firstWord(commands[index]) != WAIT_COMMAND) {
                continue
            }
            if (index > start) {
                expanded += action.copy(
                    invocation = CommandInvocation.Raw(
                        invocation.call.copy(commands = commands.subList(start, index).toList()),
                    ),
                )
            }
            val timeout = commands[index].trim().removePrefix(WAIT_COMMAND).trim()
            expanded += WaitAction(action.condition, if (timeout.isEmpty()) null else timeout.toInt())
            start = index + 1
        }
        if (start < commands.size) {
            expanded += action.copy(
                invocation = CommandInvocation.Raw(
                    invocation.call.copy(commands = commands.subList(start, commands.size).toList()),
                ),
            )
        }
        return expanded
    }

    private fun truncateClickCommand(action: DialogueAction): DialogueAction {
        val invocation = action.clickCommand ?: return action
        check(invocation is CommandInvocation.Raw) { "Click command is already tokenized" }
        val commands = truncateAfterReturn(invocation.call.commands)
        if (commands === invocation.call.commands) {
            return action
        }
        return action.copy(
            clickCommand = CommandInvocation.Raw(invocation.call.copy(commands = commands)),
        )
    }

    private fun truncateAfterReturn(commands: List<String>): List<String> {
        val index = commands.indexOfFirst { flowWord(it) == "return" }
        return if (index < 0) commands else commands.subList(0, index + 1).toList()
    }
}
