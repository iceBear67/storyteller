package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.ib67.mods.storyteller.LOGGER
import io.ib67.mods.storyteller.commands.CheckFailedException
import net.minecraft.commands.CommandSource
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.permissions.LevelBasedPermissionSet

class CommandExecutor {

    fun run(player: ServerPlayer, selector: String, command: String): Boolean =
        run(player, selector, listOf(command))

    fun run(player: ServerPlayer, selector: String, commands: List<String>): Boolean {
        val source = scriptSource(player)
        val entities = try {
            EntityArgument.entities().parse(StringReader(selector)).findEntities(source)
        } catch (exception: CommandSyntaxException) {
            handle(exception, player)
            return true
        } catch (exception: RuntimeException) {
            LOGGER.error("Command selector failed for {}: {}", player.gameProfile.name, selector, exception)
            player.sendSystemMessage(Component.literal(exception.message ?: "Command selector failed"))
            return true
        }
        for (entity in entities) {
            val entitySource = source.withEntity(entity)
            for (command in commands) {
                try {
                    execute(entitySource, command)
                } catch (exception: CommandSyntaxException) {
                    handle(exception, player)
                } catch (exception: CheckFailedException) {
                    player.sendSystemMessage(Component.literal(exception.message ?: "Check failed"))
                    return false
                } catch (exception: RuntimeException) {
                    LOGGER.error("Command execution failed for {}: {}", player.gameProfile.name, command, exception)
                    player.sendSystemMessage(Component.literal(exception.message ?: "Command execution failed"))
                    return false
                }
            }
        }
        return true
    }

    fun capture(player: ServerPlayer, commands: List<String>): String {
        val capturingSource = CapturingCommandSource()
        val source = scriptSource(player).withSource(capturingSource)
        for (command in commands) {
            try {
                execute(source, command)
            } catch (exception: CommandSyntaxException) {
                capturingSource.sendSystemMessage(Component.literal(exception.message ?: "Invalid command"))
            } catch (exception: RuntimeException) {
                LOGGER.error("Inline command failed for {}: {}", player.gameProfile.name, command, exception)
                capturingSource.sendSystemMessage(Component.literal(exception.message ?: "Command execution failed"))
            }
        }
        return capturingSource.text()
    }

    private fun scriptSource(player: ServerPlayer): CommandSourceStack =
        player.createCommandSourceStack()
            .withMaximumPermission(LevelBasedPermissionSet.GAMEMASTER)

    private fun handle(exception: CommandSyntaxException, player: ServerPlayer) {
        player.sendSystemMessage(Component.literal(exception.message ?: "Invalid command"))
    }

    private fun execute(source: CommandSourceStack, command: String) {
        val dispatcher = source.server.commands.dispatcher
        val prefixTrimmed = Commands.trimOptionalPrefix(command)
        val trimmed = prefixTrimmed.trimStart()
        val firstWord = trimmed.takeWhile { !it.isWhitespace() }
        val normalized = if (firstWord in FLOW_COMMAND_NAMES) "storyteller:$trimmed" else prefixTrimmed
        dispatcher.execute(dispatcher.parse(normalized, source))
    }
}

private val FLOW_COMMAND_NAMES = setOf("expect", "check", "update", "get", "goto", "delete", "return")

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
