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
            is CommandAction -> action.copy(invocation = tokenize(action.invocation))
            is DialogueAction -> action.copy(clickCommand = action.clickCommand?.let(::tokenize))
            else -> action
        }

    private fun tokenize(invocation: CommandInvocation): CommandInvocation =
        when (invocation) {
            is CommandInvocation.Raw -> CommandInvocation.Tokenized(builder.register(invocation.call))
            is CommandInvocation.Tokenized -> invocation
        }
}
