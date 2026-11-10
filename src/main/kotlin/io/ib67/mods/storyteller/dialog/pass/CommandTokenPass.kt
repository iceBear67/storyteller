package io.ib67.mods.storyteller.dialog.pass

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogueAction

class CommandTokenPass(
    private val builder: CommandTokenRegistry.Builder,
) : DialogScriptPass {

    override val priority: Int = 0

    override fun apply(script: DialogScript): DialogScript =
        script.copy(
            defaultDialog = applyToDialog(script.defaultDialog),
            subDialogs = script.subDialogs.mapValues { (_, dialog) -> applyToDialog(dialog) },
        )

    private fun applyToDialog(dialog: Dialog): Dialog =
        dialog.copy(actions = dialog.actions.map(::applyToAction))

    private fun applyToAction(action: Action): Action =
        when (action) {
            is CommandAction -> action.copy(invocation = tokenizeCommand(action.invocation))
            is DialogueAction -> action.copy(clickCommand = action.clickCommand?.let(::tokenizeClick))
            else -> action
        }

    private fun tokenizeCommand(invocation: CommandInvocation): CommandInvocation =
        when (invocation) {
            is CommandInvocation.Raw -> {
                check(invocation.call.commands.firstOrNull() != "repeatable") {
                    "repeatable can only be used in a click command"
                }
                CommandInvocation.Tokenized(builder.register(invocation.call))
            }
            is CommandInvocation.Tokenized -> invocation
        }

    private fun tokenizeClick(invocation: CommandInvocation): CommandInvocation =
        when (invocation) {
            is CommandInvocation.Raw -> {
                val call = invocation.call
                if (call.commands.firstOrNull() != "repeatable") {
                    CommandInvocation.Tokenized(builder.register(call))
                } else {
                    check(call.commands.size > 1) { "repeatable must be followed by a command" }
                    CommandInvocation.Tokenized(
                        builder.register(call.copy(commands = call.commands.drop(1)), repeatable = true),
                    )
                }
            }
            is CommandInvocation.Tokenized -> invocation
        }
}
