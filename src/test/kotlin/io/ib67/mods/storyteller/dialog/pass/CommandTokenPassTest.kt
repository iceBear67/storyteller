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
import org.junit.jupiter.api.Assertions.assertThrows
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

    @Test
    fun `sub dialogs are tokenized`() {
        val builder = CommandTokenRegistry.builder()
        val call = CommandCall(commands = listOf("say sub"), selector = "@a")
        val script = DialogScript(
            id = "token-sub",
            subDialogs = mapOf(
                "branch" to Dialog(
                    actions = listOf(CommandAction(CommandInvocation.Raw(call))),
                    id = "branch",
                ),
            ),
            defaultDialog = Dialog(actions = emptyList(), id = "token-sub"),
        )
        val result = CommandTokenPass(builder).apply(script)
        val action = result.subDialogs.getValue("branch").actions.single() as CommandAction
        val invocation = action.invocation as CommandInvocation.Tokenized
        assertEquals(call, builder.commandFor(invocation.token))
    }

    @Test
    fun `repeatable click commands strip the modifier`() {
        val builder = CommandTokenRegistry.builder()
        val result = runPass(
            builder,
            clickAction(CommandCall(commands = listOf("repeatable", "say hi"), selector = "@a")),
        ).single() as DialogueAction
        val token = (result.clickCommand as CommandInvocation.Tokenized).token
        assertEquals(
            CommandCall(commands = listOf("say hi"), selector = "@a"),
            builder.commandFor(token),
        )
    }

    @Test
    fun `repeatable click commands get a different token`() {
        val builder = CommandTokenRegistry.builder()
        val plain = runPass(builder, clickAction(CommandCall(commands = listOf("say hi"), selector = "@a")))
        val repeatable = runPass(
            builder,
            clickAction(CommandCall(commands = listOf("repeatable", "say hi"), selector = "@a")),
        )
        assertNotEquals(
            clickToken(plain),
            clickToken(repeatable),
        )
    }

    @Test
    fun `repeatable click command without a command is rejected`() {
        val builder = CommandTokenRegistry.builder()
        val action = clickAction(CommandCall(commands = listOf("repeatable"), selector = "@a"))
        assertThrows(IllegalStateException::class.java) { runPass(builder, action) }
    }

    @Test
    fun `repeatable in a command action is rejected`() {
        val builder = CommandTokenRegistry.builder()
        val action = CommandAction(
            CommandInvocation.Raw(CommandCall(commands = listOf("repeatable", "say hi"), selector = "@a")),
        )
        assertThrows(IllegalStateException::class.java) { runPass(builder, action) }
    }

    private fun clickAction(call: CommandCall): DialogueAction =
        DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandInvocation.Raw(call),
        )

    private fun clickToken(actions: List<Action>): String =
        ((actions.single() as DialogueAction).clickCommand as CommandInvocation.Tokenized).token

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
