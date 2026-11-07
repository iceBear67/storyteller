package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands

internal const val RUN_COMMANDS_COMMAND = "storyteller:run_commands"

class RunnerCommands(private val commandExecutor: CommandExecutor) {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal(RUN_COMMANDS_COMMAND)
                .then(
                    Commands.argument("payload", StringArgumentType.greedyString())
                        .executes { context ->
                            val payload = StringArgumentType.getString(context, "payload")
                            val separator = payload.indexOf(' ')
                            check(separator > 0) { "缺少选择器" }
                            val commands = payload.substring(separator + 1)
                                .split(';')
                                .map { it.trim() }
                            check(commands.isNotEmpty() && commands.none { it.isEmpty() }) { "命令为空" }
                            commandExecutor.run(
                                context.source.playerOrException,
                                payload.substring(0, separator),
                                commands,
                            )
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
    }
}
