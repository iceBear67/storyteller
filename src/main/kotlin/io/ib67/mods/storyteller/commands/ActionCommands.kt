package io.ib67.mods.storyteller.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ib67.mods.storyteller.dialog.CommandTokenRegistry
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

private val USED_TOKEN_ERROR = SimpleCommandExceptionType(Component.literal("Action token was already used"))

class ActionCommands(
    private val commandExecutor: CommandExecutor,
    private val stateManager: StateManager,
    private val director: DialogDirector,
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
                            val player = context.source.playerOrException
                            val token = StringArgumentType.getString(context, "token")
                            val call = CommandTokenRegistry.commandFor(token)
                                ?: throw unknownToken(token)
                            if (stateManager.checkCommandScope && !isCurrentActionToken(player, token)) {
                                throw unknownToken(token)
                            }
                            if (!CommandTokenRegistry.consume(player.uuid, token)) {
                                throw USED_TOKEN_ERROR.create()
                            }
                            commandExecutor.run(player, call.selector, call.commands)
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
    }

    private fun isCurrentActionToken(player: ServerPlayer, token: String): Boolean =
        director.currentFrame(player)?.poppedCommands?.contains(token) ?: false

    private fun unknownToken(token: String): CommandSyntaxException =
        SimpleCommandExceptionType(Component.literal("Unknown action token: $token")).create()
}
