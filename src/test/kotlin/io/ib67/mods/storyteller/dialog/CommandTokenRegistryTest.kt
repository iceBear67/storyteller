package io.ib67.mods.storyteller.dialog

import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CommandTokenRegistryTest {

    private val registry = DialogScriptRegistry()
    private val player = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val otherPlayer = UUID.fromString("00000000-0000-0000-0000-000000000002")

    init {
        CommandTokenRegistry.bind(registry.reloadEvent)
    }

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
        registry.reloadEvent.fire("previous", "current")
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
    fun `reload clears consumed tokens`() {
        val call = CommandCall(commands = listOf("reload-clears-consumed"), selector = "@a")
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(call)
        CommandTokenRegistry.install(builder)
        assertTrue(CommandTokenRegistry.consume(player, token))
        registry.reloadEvent.fire("previous", "current")
        val reinstall = CommandTokenRegistry.builder()
        reinstall.register(call)
        CommandTokenRegistry.install(reinstall)
        assertTrue(CommandTokenRegistry.consume(player, token))
    }

    @Test
    fun `unknown tokens cannot be consumed`() {
        assertFalse(CommandTokenRegistry.consume(player, "missing-token"))
    }

    @Test
    fun `allowed tokens are tracked per player`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-per-player"))
        assertTrue(CommandTokenRegistry.isAllowed(player, "allowed-per-player"))
        assertFalse(CommandTokenRegistry.isAllowed(otherPlayer, "allowed-per-player"))
    }

    @Test
    fun `replacing allowed tokens discards earlier ones`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-old"))
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-new"))
        assertFalse(CommandTokenRegistry.isAllowed(player, "allowed-old"))
        assertTrue(CommandTokenRegistry.isAllowed(player, "allowed-new"))
    }

    @Test
    fun `empty replacement clears allowed tokens`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-cleared"))
        CommandTokenRegistry.replaceAllowed(player, emptyList())
        assertFalse(CommandTokenRegistry.isAllowed(player, "allowed-cleared"))
    }

    @Test
    fun `added allowed tokens are kept`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-first"))
        CommandTokenRegistry.allow(player, "allowed-second")
        assertTrue(CommandTokenRegistry.isAllowed(player, "allowed-first"))
        assertTrue(CommandTokenRegistry.isAllowed(player, "allowed-second"))
    }

    @Test
    fun `reload with changed checksum clears allowed tokens`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-reload-changed"))
        registry.reloadEvent.fire("previous", "current")
        assertFalse(CommandTokenRegistry.isAllowed(player, "allowed-reload-changed"))
    }

    @Test
    fun `reload with unchanged checksum keeps allowed tokens`() {
        CommandTokenRegistry.replaceAllowed(player, listOf("allowed-reload-same"))
        registry.reloadEvent.fire("same", "same")
        assertTrue(CommandTokenRegistry.isAllowed(player, "allowed-reload-same"))
    }

    @Test
    fun `install drops allowed tokens missing from the new table`() {
        val call = CommandCall(commands = listOf("allow-prune"), selector = "@a")
        val builder = CommandTokenRegistry.builder()
        val token = builder.register(call)
        CommandTokenRegistry.install(builder)
        CommandTokenRegistry.replaceAllowed(player, listOf(token, "allowed-stale"))
        val reinstall = CommandTokenRegistry.builder()
        reinstall.register(call)
        CommandTokenRegistry.install(reinstall)
        assertTrue(CommandTokenRegistry.isAllowed(player, token))
        assertFalse(CommandTokenRegistry.isAllowed(player, "allowed-stale"))
    }
}
