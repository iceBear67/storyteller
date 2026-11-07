package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import io.ib67.mods.storyteller.dialog.runner.RunnerCommands
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType

class Storyteller : ModInitializer {

    private val commandExecutor = CommandExecutor()

    override fun onInitialize() {
        PlayerStateAttachment.register()
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            Identifier.fromNamespaceAndPath("storyteller", "dialog_scripts"),
            DialogScriptRegistry,
        )
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            RunnerCommands(commandExecutor).register(dispatcher)
        }
    }
}
