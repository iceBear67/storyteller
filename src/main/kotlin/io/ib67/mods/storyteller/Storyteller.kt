package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ActionCommands
import io.ib67.mods.storyteller.dialog.runner.ActionRunner
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.DialogRefArgument
import io.ib67.mods.storyteller.dialog.runner.DialogueTextRenderer
import io.ib67.mods.storyteller.dialog.runner.ScriptCommands
import io.ib67.mods.storyteller.dialog.runner.ScriptConditionEvaluator
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.dialog.session.DialogSessionRunner
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType

class Storyteller : ModInitializer {

    private val stateManager = StateManager(
        ServerLifecycleEvents.SERVER_STARTED,
        ServerLifecycleEvents.BEFORE_SAVE,
        ServerLifecycleEvents.SERVER_STOPPING,
        ServerPlayConnectionEvents.JOIN,
        ServerPlayConnectionEvents.DISCONNECT,
        DialogScriptRegistry.reloadEvent,
    )
    private val commandExecutor = CommandExecutor(stateManager)
    private val textRenderer = DialogueTextRenderer()
    private val conditionEvaluator = ScriptConditionEvaluator()
    private val actionRunner = ActionRunner(conditionEvaluator, commandExecutor, textRenderer, stateManager)
    private val director = DialogDirector(stateManager) {
        when (val value = stateManager.globalState[KnownKeys.DIALOG_INTERVAL_TICKS]) {
            null -> 30L
            is Byte, is Short, is Int, is Long -> value.toLong()
            else -> throw IllegalStateException("Dialog interval must be an integer: $value")
        }
    }

    init {
        DialogSessionRunner(
            actionRunner,
            stateManager,
            director,
            ServerTickEvents.END_SERVER_TICK,
        )
        ScriptCommands(stateManager, director, CommandRegistrationCallback.EVENT)
        ActionCommands(commandExecutor, stateManager, director, CommandRegistrationCallback.EVENT)
    }

    override fun onInitialize() {
        ArgumentTypeRegistry.registerArgumentType(
            Identifier.fromNamespaceAndPath("storyteller", "dialog_ref"),
            DialogRefArgument::class.java,
            SingletonArgumentInfo.contextFree { DialogRefArgument() },
        )
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            Identifier.fromNamespaceAndPath("storyteller", "dialog_scripts"),
            DialogScriptRegistry,
        )
    }
}
