package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.ActionCommands
import io.ib67.mods.storyteller.dialog.runner.ActionRunner
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.DialogueTextRenderer
import io.ib67.mods.storyteller.dialog.runner.StubConditionEvaluator
import io.ib67.mods.storyteller.dialog.session.DialogSessionRunner
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType

private const val DEFAULT_DIALOG_INTERVAL_TICKS = 30L

class Storyteller : ModInitializer {

    private val commandExecutor = CommandExecutor()
    private val textRenderer = DialogueTextRenderer()
    private val conditionEvaluator = StubConditionEvaluator()
    private val actionRunner = ActionRunner(conditionEvaluator, commandExecutor, textRenderer)
    private val sessionRunner = DialogSessionRunner(actionRunner) { DEFAULT_DIALOG_INTERVAL_TICKS }

    override fun onInitialize() {
        PlayerStateAttachment.register()
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            Identifier.fromNamespaceAndPath("storyteller", "dialog_scripts"),
            DialogScriptRegistry,
        )
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            ActionCommands(commandExecutor).register(dispatcher)
        }
        ServerTickEvents.END_SERVER_TICK.register(sessionRunner::tick)
        ServerPlayConnectionEvents.JOIN.register { listener, _, _ ->
            sessionRunner.onJoin(listener.player)
        }
    }
}
