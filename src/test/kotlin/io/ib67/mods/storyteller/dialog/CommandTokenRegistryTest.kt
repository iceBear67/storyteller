package io.ib67.mods.storyteller.dialog

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandTokenRegistryTest {

    private val player = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002")

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
    fun `selector participates in the token`() {
        val builder = CommandTokenRegistry.builder()
        assertNotEquals(
            builder.register(CommandCall(commands = listOf("say hi"), selector = "@a")),
            builder.register(CommandCall(commands = listOf("say hi"), selector = "@s")),
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

    @Test
    fun `repeatable flag participates in the token`() {
        val builder = CommandTokenRegistry.builder()
        val call = CommandCall(commands = listOf("say hi"), selector = "@a")
        assertNotEquals(
            builder.register(call, repeatable = true),
            builder.register(call, repeatable = false),
        )
    }

    @Test
    fun `a token can be consumed once`() {
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(CommandCall(commands = listOf("consume-once"), selector = "@a"))
        CommandTokenRegistry.install(builder)
        assertTrue(CommandTokenRegistry.consume(player, token))
        assertFalse(CommandTokenRegistry.consume(player, token))
    }

    @Test
    fun `consumption is tracked per player`() {
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(CommandCall(commands = listOf("consume-per-player"), selector = "@a"))
        CommandTokenRegistry.install(builder)
        assertTrue(CommandTokenRegistry.consume(player, token))
        assertTrue(CommandTokenRegistry.consume(otherPlayer, token))
        assertFalse(CommandTokenRegistry.consume(player, token))
    }

    @Test
    fun `a repeatable token can be consumed repeatedly`() {
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(
            CommandCall(commands = listOf("consume-repeatable"), selector = "@a"),
            repeatable = true,
        )
        CommandTokenRegistry.install(builder)
        assertTrue(CommandTokenRegistry.consume(player, token))
        assertTrue(CommandTokenRegistry.consume(player, token))
    }

    @Test
    fun `consumed tokens stay consumed after reinstall`() {
        val call = CommandCall(commands = listOf("consume-reinstall"), selector = "@a")
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(call)
        CommandTokenRegistry.install(builder)
        assertTrue(CommandTokenRegistry.consume(player, token))
        val reinstall = CommandTokenRegistry.builder()
        reinstall.register(call)
        CommandTokenRegistry.install(reinstall)
        assertFalse(CommandTokenRegistry.consume(player, token))
    }

    @Test
    fun `unknown tokens cannot be consumed`() {
        assertFalse(CommandTokenRegistry.consume(player, "missing-token"))
    }
}
