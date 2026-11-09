package io.ib67.mods.storyteller.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CommandTokenRegistryTest {

    @Test
    fun `identical calls share a token`() {
        val builder = CommandTokenRegistry.builder()
        val first = builder.register(CommandCall(commands = listOf("say hi"), selector = "@a"))
        val second = builder.register(CommandCall(commands = listOf("say hi"), selector = "@a"))
        assertEquals(first, second)
    }

    @Test
    fun `different calls get different tokens`() {
        val builder = CommandTokenRegistry.builder()
        assertNotEquals(
            builder.register(CommandCall(commands = listOf("say a"), selector = "@a")),
            builder.register(CommandCall(commands = listOf("say b"), selector = "@a")),
        )
    }

    @Test
    fun `installed tokens can be resolved`() {
        val builder = CommandTokenRegistry.builder()
        val call = CommandCall(commands = listOf("say hi"), selector = "@a")
        val token = builder.register(call)
        CommandTokenRegistry.install(builder)
        assertEquals(call, CommandTokenRegistry.commandFor(token))
    }

    @Test
    fun `reload event clears installed tokens`() {
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(CommandCall(commands = listOf("say hi"), selector = "@a"))
        CommandTokenRegistry.install(builder)
        DialogScriptRegistry.reloadEvent.fire("previous", "current")
        assertNull(CommandTokenRegistry.commandFor(token))
    }
}
