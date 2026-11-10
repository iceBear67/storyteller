package io.ib67.mods.storyteller

import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.RegistryAccess
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
        val registration = EventFactory.createArrayBacked(CommandRegistrationCallback::class.java) { listeners ->
            CommandRegistrationCallback { dispatcher, buildContext, selection ->
                for (listener in listeners) {
                    listener.register(dispatcher, buildContext, selection)
                }
            }
        }
        StorytellerCommands(debugToggle, registration)
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        registration.invoker().register(
            dispatcher,
            CommandBuildContext.simple(RegistryAccess.EMPTY, FeatureFlagSet.of()),
            Commands.CommandSelection.ALL,
        )
        val source = Commands.createCompilationContext(LevelBasedPermissionSet.OWNER)
        assertEquals(1, dispatcher.execute("storyteller debug", source))
        assertTrue(debugToggle.enabled)
        assertEquals(1, dispatcher.execute("storyteller debug", source))
        assertFalse(debugToggle.enabled)
    }
}
