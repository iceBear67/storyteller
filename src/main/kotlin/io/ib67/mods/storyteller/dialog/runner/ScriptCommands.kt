package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ib67.mods.storyteller.KnownKeys
import io.ib67.mods.storyteller.dialog.DialogRef
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.state.StateManager
import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.Event
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

private val WHITESPACE = Regex("\\s+")
private val INTEGER_LITERAL = Regex("[+-]?\\d+")
private val DECIMAL_LITERAL = Regex("[+-]?(?:(?:\\d+\\.\\d*|\\.\\d+)(?:[eE][+-]?\\d+)?|\\d+[eE][+-]?\\d+)")

private val NO_DIALOG_ERROR = SimpleCommandExceptionType(Component.literal("No active dialog frame"))
private val EXPECT_EMPTY_ERROR = SimpleCommandExceptionType(Component.literal("expect requires at least one dialog"))
private val INVALID_REF_ERROR = SimpleCommandExceptionType(Component.literal("Invalid dialog reference"))
private val UNKNOWN_SCRIPT_ERROR = SimpleCommandExceptionType(Component.literal("Unknown dialog script"))
private val UNKNOWN_DIALOG_ERROR = SimpleCommandExceptionType(Component.literal("Unknown dialog"))
private val NOT_ALLOWED_ERROR = SimpleCommandExceptionType(Component.literal("Dialog is not allowed by the current frame"))
private val RESERVED_KEY_ERROR = SimpleCommandExceptionType(Component.literal("Cannot access a reserved state key"))
private val UNKNOWN_KEY_ERROR = SimpleCommandExceptionType(Component.literal("Unknown state key"))
private val MISSING_VALUE_ERROR = SimpleCommandExceptionType(Component.literal("update requires a value"))
private val INVALID_VALUE_ERROR = SimpleCommandExceptionType(Component.literal("Invalid literal value"))

class ScriptCommands(
    private val stateManager: StateManager,
    private val director: DialogDirector,
    commandRegistration: Event<CommandRegistrationCallback>,
) {

    init {
        commandRegistration.register { dispatcher, _, _ -> register(dispatcher) }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("storyteller:expect")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("refs", StringArgumentType.greedyString())
                        .executes { context ->
                            expect(context)
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
        dispatcher.register(
            Commands.literal("storyteller:update")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("global")
                        .then(keyAndValue { context -> update(context, global = true) }),
                )
                .then(keyAndValue { context -> update(context, global = false) }),
        )
        dispatcher.register(
            Commands.literal("storyteller:get")
                .then(
                    Commands.literal("global")
                        .then(key { context -> get(context, global = true) }),
                )
                .then(key { context -> get(context, global = false) }),
        )
        dispatcher.register(
            Commands.literal("storyteller:goto")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.argument("ref", DialogRefArgument(director))
                        .executes { context ->
                            goto(context)
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
        dispatcher.register(
            Commands.literal("storyteller:delete")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(
                    Commands.literal("global")
                        .then(key { context -> delete(context, global = true) }),
                )
                .then(key { context -> delete(context, global = false) }),
        )
        dispatcher.register(
            Commands.literal("storyteller:return")
                .executes { context ->
                    returnDialog(context, all = false)
                    Command.SINGLE_SUCCESS
                }
                .then(
                    Commands.literal("all")
                        .executes { context ->
                            returnDialog(context, all = true)
                            Command.SINGLE_SUCCESS
                        },
                ),
        )
        dispatcher.register(
            Commands.literal("storyteller:reset")
                .executes { context ->
                    reset(context)
                    Command.SINGLE_SUCCESS
                },
        )
    }

    private fun keyAndValue(action: (CommandContext<CommandSourceStack>) -> Unit) =
        Commands.argument("key", StringArgumentType.word())
            .then(
                Commands.argument("value", StringArgumentType.greedyString())
                    .executes { context ->
                        action(context)
                        Command.SINGLE_SUCCESS
                    },
            )

    private fun key(action: (CommandContext<CommandSourceStack>) -> Unit) =
        Commands.argument("key", StringArgumentType.word())
            .executes { context ->
                action(context)
                Command.SINGLE_SUCCESS
            }

    private fun expect(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val refs = StringArgumentType.getString(context, "refs")
            .split(WHITESPACE)
            .filter { it.isNotEmpty() }
        if (refs.isEmpty()) {
            throw EXPECT_EMPTY_ERROR.create()
        }
        val defaultScriptId = director.currentFrame(player)?.scriptId
            ?: throw NO_DIALOG_ERROR.create()
        val resolved = refs.map { ref ->
            val dialogRef = try {
                parseDialogRef(ref, defaultScriptId)
            } catch (_: IllegalArgumentException) {
                throw INVALID_REF_ERROR.create()
            }
            requireDialog(dialogRef)
            dialogRef
        }
        director.setAllowedContinuation(player, resolved)
    }

    private fun update(context: CommandContext<CommandSourceStack>, global: Boolean) {
        val (key, target) = stateTarget(context, global)
        val raw = StringArgumentType.getString(context, "value")
        if (raw.isEmpty()) {
            throw MISSING_VALUE_ERROR.create()
        }
        val text = raw.trim().removePairedQuotes()
        val value = try {
            when {
                text == "true" -> true
                text == "false" -> false
                INTEGER_LITERAL.matches(text) -> text.toIntOrNull() ?: text.toLongOrNull()
                    ?: throw IllegalArgumentException("Integer literal is out of range: $text")
                DECIMAL_LITERAL.matches(text) -> text.toDoubleOrNull()
                    ?: throw IllegalArgumentException("Invalid decimal literal: $text")
                else -> text
            }
        } catch (_: IllegalArgumentException) {
            throw INVALID_VALUE_ERROR.create()
        }
        target[TypedKey.of(key)] = value
    }

    private fun get(context: CommandContext<CommandSourceStack>, global: Boolean) {
        val (key, target) = stateTarget(context, global)
        val value = target[TypedKey.of(key)]
            ?: throw UNKNOWN_KEY_ERROR.create()
        context.source.sendSuccess({ Component.literal(value.toString()) }, false)
    }

    private fun stateTarget(
        context: CommandContext<CommandSourceStack>,
        global: Boolean,
    ): Pair<String, TypedKeyMap> {
        val player = context.source.playerOrException
        val key = StringArgumentType.getString(context, "key")
        if (key == KnownKeys.DIALOG_STACK.name || key == KnownKeys.DIALOG_SCRIPT_CHECKSUM.name) {
            throw RESERVED_KEY_ERROR.create()
        }
        val target = if (global) stateManager.globalState else stateManager.playerState(player)
        return key to target
    }

    private fun goto(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        val text = context.getArgument("ref", String::class.java)
        val ref = try {
            parseDialogRef(text, director.currentFrame(player)?.scriptId)
        } catch (_: IllegalArgumentException) {
            throw INVALID_REF_ERROR.create()
        }
        requireDialog(ref)
        if (!director.canPushDialog(player, ref)) {
            throw NOT_ALLOWED_ERROR.create()
        }
        director.pushDialog(player, ref)
    }

    private fun delete(context: CommandContext<CommandSourceStack>, global: Boolean) {
        val (key, target) = stateTarget(context, global)
        target.remove(TypedKey.of(key))
    }

    private fun returnDialog(context: CommandContext<CommandSourceStack>, all: Boolean) {
        val player = context.source.playerOrException
        if (all) {
            director.clearDialogs(player)
            return
        }
        if (director.currentFrame(player) != null) {
            director.popDialog(player)
        }
    }

    private fun reset(context: CommandContext<CommandSourceStack>) {
        val player = context.source.playerOrException
        stateManager.playerState(player).resetDialogState(DialogScriptRegistry.scriptsChecksum)
    }

    private fun requireDialog(ref: DialogRef): DialogRef {
        val script = DialogScriptRegistry.get(ref.scriptId)
            ?: throw UNKNOWN_SCRIPT_ERROR.create()
        if (script.resolve(ref.dialogId) == null) {
            throw UNKNOWN_DIALOG_ERROR.create()
        }
        return ref
    }
}

internal fun parseDialogRef(text: String, defaultScriptId: String?): DialogRef {
    val trimmed = text.trim()
    val separator = trimmed.indexOf(':')
    val scriptId: String
    val dialogId: String
    if (separator < 0) {
        scriptId = defaultScriptId
            ?: throw IllegalArgumentException("Dialog reference '$trimmed' has no script")
        dialogId = trimmed
    } else {
        val explicitScriptId = trimmed.substring(0, separator)
        scriptId = explicitScriptId.ifEmpty {
            defaultScriptId
                ?: throw IllegalArgumentException("Dialog reference '$trimmed' has no script")
        }
        dialogId = trimmed.substring(separator + 1)
    }
    require(dialogId.isNotEmpty()) { "Dialog reference '$trimmed' has an empty dialog id" }
    return DialogRef(scriptId, dialogId)
}

private fun String.removePairedQuotes(): String {
    if (length >= 2 && (first() == '"' && last() == '"' || first() == '\'' && last() == '\'')) {
        return substring(1, length - 1)
    }
    return this
}
