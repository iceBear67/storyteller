package io.ib67.mods.storyteller.commands

import com.mojang.brigadier.CommandDispatcher
import io.ib67.mods.storyteller.DebugToggleEvent
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ScriptConditionEvaluator
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.state.StateManager
import io.ib67.mods.storyteller.util.TypedKey
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.EventFactory
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.ChatFormatting
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.RegistryAccess
import net.minecraft.network.chat.TextColor
import net.minecraft.server.Bootstrap
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.world.flag.FeatureFlagSet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class StorytellerCommandsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `debug subcommand toggles the state`() {
        val debugToggle = DebugToggleEvent()
        val dispatcher = dispatcher(debugToggle)
        val source = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER)
        assertEquals(1, dispatcher.execute("storyteller debug", source))
        assertTrue(debugToggle.enabled)
        assertEquals(1, dispatcher.execute("storyteller debug", source))
        assertFalse(debugToggle.enabled)
    }

    @Test
    fun `state dump wraps entries at fifty characters`() {
        val dump = formatStateDump(
            linkedMapOf(
                "alpha" to "1".repeat(25),
                "beta" to "2".repeat(25),
            ),
        )
        val lines = dump.string.split("\n")
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.length <= 50 })
    }

    @Test
    fun `state dump styles keys white and values gray`() {
        val dump = formatStateDump(linkedMapOf("key" to "value"))
        assertEquals("key=value", dump.string)
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), dump.siblings[0].style.color)
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.WHITE), dump.siblings[1].style.color)
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GRAY), dump.siblings[2].style.color)
    }

    @Test
    fun `empty state dump is marked`() {
        assertEquals("(empty)", formatStateDump(emptyMap()).string)
    }

    private fun dispatcher(
        debugToggle: DebugToggleEvent,
        state: StateManager = createStateManager(),
    ): CommandDispatcher<CommandSourceStack> {
        val registration = EventFactory.createArrayBacked(CommandRegistrationCallback::class.java) { listeners ->
            CommandRegistrationCallback { dispatcher, buildContext, selection ->
                for (listener in listeners) {
                    listener.register(dispatcher, buildContext, selection)
                }
            }
        }
        val director = DialogDirector(state) { 20L }
        StorytellerCommands(debugToggle, state, director, registration)
        ScriptCommands(state, director, ScriptConditionEvaluator(), debugToggle, registration)
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        registration.invoker().register(
            dispatcher,
            CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlagSet.of()),
            Commands.CommandSelection.ALL,
        )
        return dispatcher
    }

    @Test
    fun `global state keys are suggested`() {
        val state = createStateManager()
        state.globalState[TypedKey.of("alpha")] = 1
        state.globalState[TypedKey.of("beta")] = true
        val dispatcher = dispatcher(DebugToggleEvent(), state)
        val source = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER)
        val parse = dispatcher.parse("storyteller:update global ", source)
        val texts = dispatcher.getCompletionSuggestions(parse).join().list.map { it.text }
        assertTrue("alpha" in texts)
        assertTrue("beta" in texts)
    }

    private fun createStateManager(): StateManager {
        val started = EventFactory.createArrayBacked(ServerLifecycleEvents.ServerStarted::class.java) { listeners ->
            ServerLifecycleEvents.ServerStarted { server ->
                listeners.forEach { it.onServerStarted(server) }
            }
        }
        val beforeSave = EventFactory.createArrayBacked(ServerLifecycleEvents.BeforeSave::class.java) { listeners ->
            ServerLifecycleEvents.BeforeSave { server, flush, force ->
                listeners.forEach { it.onBeforeSave(server, flush, force) }
            }
        }
        val stopping = EventFactory.createArrayBacked(ServerLifecycleEvents.ServerStopping::class.java) { listeners ->
            ServerLifecycleEvents.ServerStopping { server ->
                listeners.forEach { it.onServerStopping(server) }
            }
        }
        val join = EventFactory.createArrayBacked(ServerPlayConnectionEvents.Join::class.java) { listeners ->
            ServerPlayConnectionEvents.Join { listener, sender, server ->
                listeners.forEach { it.onPlayReady(listener, sender, server) }
            }
        }
        val disconnect = EventFactory.createArrayBacked(ServerPlayConnectionEvents.Disconnect::class.java) { listeners ->
            ServerPlayConnectionEvents.Disconnect { listener, server ->
                listeners.forEach { it.onPlayDisconnect(listener, server) }
            }
        }
        return StateManager(started, beforeSave, stopping, join, disconnect, DialogScriptRegistry.reloadEvent)
    }
}
