package io.ib67.mods.storyteller.dialog.pass

import io.ib67.mods.storyteller.dialog.Action
import io.ib67.mods.storyteller.dialog.CommandAction
import io.ib67.mods.storyteller.dialog.CommandCall
import io.ib67.mods.storyteller.dialog.CommandInvocation
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import io.ib67.mods.storyteller.dialog.WaitAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WaitActionPassTest {

    private val pass = WaitActionPass()

    @Test
    fun `wait command becomes a wait action`() {
        assertEquals(
            listOf(WaitAction(condition = null, timeoutTicks = 20)),
            runPass(command(listOf("wait 20"))),
        )
    }

    @Test
    fun `wait without timeout becomes an indefinite wait action`() {
        assertEquals(
            listOf(WaitAction(condition = null, timeoutTicks = null)),
            runPass(command(listOf("wait"))),
        )
    }

    @Test
    fun `wait action keeps the condition`() {
        assertEquals(
            listOf(WaitAction(condition = Condition("flag"), timeoutTicks = null)),
            runPass(command(listOf("wait"), condition = Condition("flag"))),
        )
    }

    @Test
    fun `wait action drops the selector`() {
        assertEquals(
            listOf(WaitAction(condition = null, timeoutTicks = 20)),
            runPass(command(listOf("wait 20"), selector = "@a")),
        )
    }

    @Test
    fun `wait splits a command action`() {
        assertEquals(
            listOf(
                command(listOf("expect a b")),
                WaitAction(condition = null, timeoutTicks = 20),
                command(listOf("say hi")),
            ),
            runPass(command(listOf("expect a b", "wait 20", "say hi"))),
        )
    }

    @Test
    fun `goto keeps the commands after it`() {
        assertEquals(
            listOf(command(listOf("goto x", "say hi"))),
            runPass(command(listOf("goto x", "say hi"))),
        )
    }

    @Test
    fun `return drops the commands after it`() {
        assertEquals(
            listOf(command(listOf("return"))),
            runPass(command(listOf("return", "say hi"))),
        )
    }

    @Test
    fun `click command drops the commands after return`() {
        val action = DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandInvocation.Raw(
                CommandCall(commands = listOf("say hi", "return", "say bye"), selector = "@a"),
            ),
        )
        val result = runPass(action).single() as DialogueAction
        assertEquals(
            CommandInvocation.Raw(
                CommandCall(commands = listOf("say hi", "return"), selector = "@a"),
            ),
            result.clickCommand,
        )
    }

    @Test
    fun `namespaced return drops the commands after it`() {
        assertEquals(
            listOf(command(listOf("storyteller:return"))),
            runPass(command(listOf("storyteller:return", "say hi"))),
        )
    }

    @Test
    fun `click command drops the commands after a namespaced return`() {
        val action = DialogueAction(
            condition = null,
            speaker = "A",
            text = listOf(TextSegment.Literal("click")),
            clickCommand = CommandInvocation.Raw(
                CommandCall(commands = listOf("say hi", "storyteller:return", "say bye"), selector = "@a"),
            ),
        )
        val result = runPass(action).single() as DialogueAction
        assertEquals(
            CommandInvocation.Raw(
                CommandCall(commands = listOf("say hi", "storyteller:return"), selector = "@a"),
            ),
            result.clickCommand,
        )
    }

    @Test
    fun `split pieces keep the condition`() {
        val condition = Condition("flag")
        val result = runPass(command(listOf("expect a b", "wait", "say hi"), condition = condition))
        assertEquals(condition, (result[0] as CommandAction).condition)
        assertEquals(condition, (result[1] as WaitAction).condition)
        assertEquals(condition, (result[2] as CommandAction).condition)
    }

    private fun runPass(action: Action): List<Action> = runPass(listOf(action))

    private fun runPass(actions: List<Action>): List<Action> =
        pass.apply(
            DialogScript(
                id = "test",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(actions = actions, id = "test"),
            ),
        ).defaultDialog.actions

    private fun command(
        commands: List<String>,
        selector: String = CommandCall.DEFAULT_SELECTOR,
        condition: Condition? = null,
    ): CommandAction = CommandAction(
        invocation = CommandInvocation.Raw(CommandCall(commands = commands, selector = selector)),
        condition = condition,
    )
}
