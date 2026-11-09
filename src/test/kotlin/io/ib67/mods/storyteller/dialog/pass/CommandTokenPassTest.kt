package io.ib67.mods.storyteller.dialog.pass

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandCall
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class CommandTokenPassTest {

    @Test
    fun `standalone command actions are tokenized`() {
        val builder = CommandTokenRegistry.builder()
        val call = CommandCall(commands = listOf("say hi"), selector = "@a")
        val action = runPass(builder, CommandAction(CommandInvocation.Raw(call), Condition("flag")))
            .single() as CommandAction
        val invocation = action.invocation as CommandInvocation.Tokenized
        assertEquals(call, builder.commandFor(invocation.token))
        assertEquals(Condition("flag"), action.condition)
    }

    @Test
    fun `click commands are tokenized`() {
        val builder = CommandTokenRegistry.builder()
        val call = CommandCall(commands = listOf("say hi"), selector = "@a")
        val action = DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandInvocation.Raw(call),
        )
        val result = runPass(builder, action).single() as DialogueAction
        val invocation = result.clickCommand as CommandInvocation.Tokenized
        assertEquals(call, builder.commandFor(invocation.token))
    }

    @Test
    fun `identical calls share a token`() {
        val builder = CommandTokenRegistry.builder()
        val first = tokenize(builder, CommandCall(commands = listOf("say hi"), selector = "@a"))
        val second = tokenize(builder, CommandCall(commands = listOf("say hi"), selector = "@a"))
        assertEquals(first, second)
    }

    @Test
    fun `different calls get different tokens`() {
        val builder = CommandTokenRegistry.builder()
        val first = tokenize(builder, CommandCall(commands = listOf("say a"), selector = "@a"))
        val second = tokenize(builder, CommandCall(commands = listOf("say b"), selector = "@a"))
        assertNotEquals(first, second)
    }

    private fun tokenize(builder: CommandTokenRegistry.Builder, call: CommandCall): String {
        val action = runPass(builder, CommandAction(CommandInvocation.Raw(call))).single() as CommandAction
        return (action.invocation as CommandInvocation.Tokenized).token
    }

    private fun runPass(builder: CommandTokenRegistry.Builder, action: Action): List<Action> =
        CommandTokenPass(builder).apply(
            DialogScript(
                id = "test",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(actions = listOf(action), id = "test"),
            ),
        ).defaultDialog.actions
}
