package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.commands.ActionCommands
import io.ib67.mods.storyteller.commands.DialogRefArgument
import io.ib67.mods.storyteller.commands.ScriptCommands
import io.ib67.mods.storyteller.commands.StateSelectorOptions
import io.ib67.mods.storyteller.commands.StorytellerCommands
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ActionRenderer
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.DefaultDialogueTextRenderer
import io.ib67.mods.storyteller.dialog.runner.ScriptConditionEvaluator
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import io.ib67.mods.storyteller.dialog.session.DialogSessionRunner
import io.ib67.mods.storyteller.entity.TriggerEvents
import io.ib67.mods.storyteller.entity.registerTriggerEntities
import io.ib67.mods.storyteller.state.StateManager
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.BlockEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.commands.synchronization.SingletonArgumentInfo
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import org.slf4j.LoggerFactory

internal val LOGGER = LoggerFactory.getLogger("storyteller")

class Storyteller : ModInitializer {

    private val stateManager = StateManager(
        ServerLifecycleEvents.SERVER_STARTED,
        ServerLifecycleEvents.BEFORE_SAVE,
        ServerLifecycleEvents.SERVER_STOPPING,
        ServerPlayConnectionEvents.JOIN,
        ServerPlayConnectionEvents.DISCONNECT,
        DialogScriptRegistry.reloadEvent,
    )
    private val commandExecutor = CommandExecutor()
    private val actionRenderer: ActionRenderer = DefaultDialogueTextRenderer()
    private val debugToggle = DebugToggleEvent()
    private val conditionEvaluator = ScriptConditionEvaluator()
    private val director = DialogDirector(stateManager) {
        when (val value = stateManager.globalState[KnownKeys.DIALOG_INTERVAL_TICKS]) {
            null -> 1L
            is Byte, is Short, is Int, is Long -> value.toLong()
            else -> throw IllegalStateException("Dialog interval must be an integer: $value")
        }
    }

    init {
        StateSelectorOptions(stateManager, conditionEvaluator)
        DialogSessionRunner(
            conditionEvaluator,
            commandExecutor,
            actionRenderer,
            debugToggle,
            stateManager,
            director,
            ServerTickEvents.END_SERVER_TICK,
        )
        TriggerEvents(
            commandExecutor,
            debugToggle,
            ServerLifecycleEvents.SERVER_STARTED,
            UseEntityCallback.EVENT,
            AttackEntityCallback.EVENT,
            PlayerBlockBreakEvents.AFTER,
            BlockEvents.USE_ITEM_ON,
        )
        StorytellerCommands(debugToggle, stateManager, director, CommandRegistrationCallback.EVENT)
        ScriptCommands(stateManager, director, conditionEvaluator, debugToggle, CommandRegistrationCallback.EVENT)
        ActionCommands(commandExecutor, stateManager, CommandRegistrationCallback.EVENT)
    }

    override fun onInitialize() {
        registerTriggerEntities(commandExecutor, debugToggle)
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
