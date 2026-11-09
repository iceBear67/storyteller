package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

class ActionCommands(
    private val commandExecutor: CommandExecutor,
    commandRegistration: Event<CommandRegistrationCallback>,
) {

    init {
        commandRegistration.register { dispatcher, _, _ -> register(dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("storyteller:run_action")
                .then(
                    Commands.argument("token", StringArgumentType.word())
                        .executes { context ->
                            val token = StringArgumentType.getString(context, "token")
                            val call = CommandTokenRegistry.commandFor(token)
                                ?: throw SimpleCommandExceptionType(
                                    Component.literal("Unknown action token: $token"),
                                ).create()
                            commandExecutor.run(
                                context.source.playerOrException,
                                call.selector,
                                call.commands,
                            )
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
    }
}
