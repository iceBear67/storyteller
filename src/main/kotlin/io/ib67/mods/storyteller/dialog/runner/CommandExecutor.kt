package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.StringReader
import io.ib67.mods.storyteller.dialog.CommandAction
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

class CommandExecutor {

    fun run(player: ServerPlayer, selector: String, commands: List<String>) {
        val source = player.createCommandSourceStack()
        val entities = EntityArgument.entities().parse(StringReader(selector)).findEntities(source)
        for (entity in entities) {
            val entitySource = source.withEntity(entity)
            for (command in commands) {
                execute(entitySource, command)
            }
        }
    }

    fun capture(player: ServerPlayer, commands: List<String>): String {
        val capturingSource = CapturingCommandSource()
        val source = player.createCommandSourceStack().withSource(capturingSource)
        for (command in commands) {
            execute(source, command)
        }
        return capturingSource.text()
    }

    fun clickCommand(action: CommandAction): String =
        "$RUN_COMMANDS_COMMAND ${action.selector} ${action.commands.joinToString(";")}"

    private fun execute(source: CommandSourceStack, command: String) {
        val dispatcher = source.server.commands.dispatcher
        dispatcher.execute(dispatcher.parse(Commands.trimOptionalPrefix(command), source))
    }
}

private class CapturingCommandSource : CommandSource {

    private val messages = mutableListOf<Component>()

    override fun sendSystemMessage(message: Component) {
        messages += message
    }

    override fun acceptsSuccess(): Boolean = true

    override fun acceptsFailure(): Boolean = false

    override fun shouldInformAdmins(): Boolean = false

    fun text(): String = messages.joinToString("") { it.string }
}
