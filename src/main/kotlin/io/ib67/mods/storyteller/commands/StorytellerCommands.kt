package io.ib67.mods.storyteller.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.context.CommandContext
import io.ib67.mods.storyteller.DebugToggleEvent
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.ChatFormatting
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent

class StorytellerCommands(
    private val debugToggle: DebugToggleEvent,
    private val stateManager: StateManager,
    private val director: DialogDirector,
    commandRegistration: Event<CommandRegistrationCallback>,
) {

    init {
        commandRegistration.register { dispatcher, _, _ -> register(dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("st")
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
                        }
                        .then(
                            Commands.literal("suspend")
                                .executes { context ->
                                    toggleSuspension(context)
                                    Command.SINGLE_SUCCESS
                                },
                        )
                        .then(
                            Commands.literal("trace")
                                .executes { context ->
                                    trace(context)
                                    Command.SINGLE_SUCCESS
                                },
                        )
                        .then(
                            Commands.literal("dump")
                                .executes { context ->
                                    dumpPlayer(context)
                                    Command.SINGLE_SUCCESS
                                }
                                .then(
                                    Commands.literal("global")
                                        .executes { context ->
                                            dumpGlobal(context)
                                            Command.SINGLE_SUCCESS
                                        },
                                )
                                .then(
                                    Commands.literal("frame")
                                        .executes { context ->
                                            dumpFrame(context)
                                            Command.SINGLE_SUCCESS
                                        },
                                ),
                        ),
                ),
        )
    }

    private fun toggleSuspension(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val state = stateManager.playerState(player)
        if (state.isSuspended) {
            state.clearSuspension()
            context.source.sendSuccess({ Component.literal("Storyteller dialogs resumed") }, false)
        } else {
            state.markSuspended()
            context.source.sendSuccess({ Component.literal("Storyteller dialogs suspended") }, false)
        }
    }

    private fun dumpPlayer(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val entries = stateManager.playerState(player).namedValues()
            .mapValues { (_, value) -> value.toString() }
            .toSortedMap()
        context.source.sendSuccess({ formatStateDump(entries) }, false)
    }

    private fun dumpGlobal(context: CommandContext<CommandSourceStack>) {
        val entries = stateManager.globalState.namedValues()
            .mapValues { (_, value) -> value.toString() }
            .toSortedMap()
        context.source.sendSuccess({ formatStateDump(entries) }, false)
    }

    private fun dumpFrame(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val frame = director.currentFrame(player)
        if (frame == null) {
            context.source.sendSuccess(
                { Component.literal("No active dialog frame").withStyle(ChatFormatting.GRAY) },
                false,
            )
            return
        }
        val entries = linkedMapOf(
            "suspended" to stateManager.playerState(player).isSuspended.toString(),
            "scriptId" to frame.scriptId,
            "dialogId" to frame.dialogId,
            "actionIndex" to frame.actionIndex.toString(),
            "actionTicks" to (frame.actionTicks?.toString() ?: "null"),
            "intervalTicks" to frame.intervalTicks.toString(),
            "nextActionTick" to frame.nextActionTick.toString(),
            "allowedContinuation" to frame.allowedContinuation.joinToString(",") { "${it.scriptId}:${it.dialogId}" },
            "poppedCommands" to frame.poppedCommands.joinToString(","),
        )
        context.source.sendSuccess({ formatStateDump(entries) }, false)
    }

    private fun trace(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val frames = stateManager.playerState(player).dialogFrames
        if (frames.isEmpty()) {
            context.source.sendSuccess(
                { Component.literal("No dialog frames").withStyle(ChatFormatting.GRAY) },
                false,
            )
            return
        }
        val result = Component.empty()
        for ((index, frame) in frames.withIndex()) {
            if (index > 0) {
                result.append(Component.literal("\n"))
            }
            result.append(Component.literal("#$index ").withStyle(ChatFormatting.WHITE))
            result.append(
                Component.literal(
                    "${frame.scriptId}:${frame.dialogId} action=${frame.actionIndex} ticks=${frame.actionTicks ?: "null"}",
                ).withStyle(ChatFormatting.GRAY),
            )
        }
        context.source.sendSuccess({ result }, false)
    }
}

internal fun formatStateDump(entries: Map<String, String>): MutableComponent {
    if (entries.isEmpty()) {
        return Component.literal("(empty)").withStyle(ChatFormatting.GRAY)
    }
    val result = Component.empty()
    var lineLength = 0
    for ((key, value) in entries) {
        val entryLength = key.length + 1 + value.length
        if (lineLength > 0 && lineLength + 1 + entryLength > 50) {
            result.append(Component.literal("\n"))
            lineLength = 0
        }
        if (lineLength > 0) {
            result.append(Component.literal(" "))
            lineLength++
        }
        result.append(Component.literal(key).withStyle(ChatFormatting.WHITE))
        result.append(Component.literal("=").withStyle(ChatFormatting.WHITE))
        result.append(Component.literal(value).withStyle(ChatFormatting.GRAY))
        lineLength += entryLength
    }
    return result
}
