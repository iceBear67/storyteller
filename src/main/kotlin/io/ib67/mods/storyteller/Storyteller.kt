package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ActionCommands
import io.ib67.mods.storyteller.dialog.runner.ActionRunner
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.DialogueTextRenderer
import io.ib67.mods.storyteller.dialog.runner.ScriptCommands
import io.ib67.mods.storyteller.dialog.runner.ScriptConditionEvaluator
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.dialog.session.DialogSessionRunner
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType

private const val DEFAULT_DIALOG_INTERVAL_TICKS = 30L

class Storyteller : ModInitializer {

    private val commandExecutor = CommandExecutor()
    private val stateManager = StateManager(
        ServerLifecycleEvents.SERVER_STARTED,
        ServerLifecycleEvents.BEFORE_SAVE,
        ServerLifecycleEvents.SERVER_STOPPING,
        ServerPlayConnectionEvents.JOIN,
        ServerPlayConnectionEvents.DISCONNECT,
        DialogScriptRegistry.reloadEvent,
    )
    private val textRenderer = DialogueTextRenderer()
    private val conditionEvaluator = ScriptConditionEvaluator()
    private val actionRunner = ActionRunner(conditionEvaluator, commandExecutor, textRenderer, stateManager)
    private val director = DialogDirector(stateManager) { DEFAULT_DIALOG_INTERVAL_TICKS }

    init {
        DialogSessionRunner(
            actionRunner,
            stateManager,
            director,
            ServerTickEvents.END_SERVER_TICK,
        )
        ScriptCommands(stateManager, director, CommandRegistrationCallback.EVENT)
        ActionCommands(commandExecutor, CommandRegistrationCallback.EVENT)
    }

    override fun onInitialize() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            Identifier.fromNamespaceAndPath("storyteller", "dialog_scripts"),
            DialogScriptRegistry,
        )
    }
}
