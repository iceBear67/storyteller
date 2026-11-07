package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.CommandAction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CommandExecutorTest {

    private val executor = CommandExecutor()

    @Test
    fun `click command uses self selector by default`() {
        assertEquals(
            "storyteller:run_commands @s say hi",
            executor.clickCommand(CommandAction(commands = listOf("say hi"))),
        )
    }

    @Test
    fun `click command keeps selector and joins commands`() {
        assertEquals(
            "storyteller:run_commands @a say hi;say bye",
            executor.clickCommand(CommandAction(commands = listOf("say hi", "say bye"), selector = "@a")),
        )
    }
}
