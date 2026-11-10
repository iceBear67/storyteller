package io.ib67.mods.storyteller

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class StorytellerCommands(
    private val debugToggle: DebugToggleEvent,
    commandRegistration: Event<CommandRegistrationCallback>,
) {

    init {
        commandRegistration.register { dispatcher, _, _ -> register(dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("storyteller")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("debug")
                        .executes { context ->
                            debugToggle.toggle()
                            val state = if (debugToggle.enabled) "enabled" else "disabled"
                            context.source.sendSuccess(
                                { Component.literal("Storyteller debug mode $state") },
                                false,
                            )
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
    }
}
