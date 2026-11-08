package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

internal const val RUN_ACTION_COMMAND = "storyteller:run_action"

class ActionCommands(private val commandExecutor: CommandExecutor) {
    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(RUN_ACTION_COMMAND)
                .then(
                    Commands.argument("token", StringArgumentType.word())
                        .executes { context ->
                            val token = StringArgumentType.getString(context, "token")
                            val action = DialogScriptRegistry.commandFor(token)
                                ?: throw SimpleCommandExceptionType(
                                    Component.literal("Unknown action token: $token"),
                                ).create()
                            commandExecutor.run(
                                context.source.playerOrException,
                                action.selector,
                                action.commands,
                            )
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
    }
}
