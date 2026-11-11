package io.ib67.mods.storyteller.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.renderer.entity.NoopRenderer
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType

class StorytellerClient : ClientModInitializer {

    override fun onInitializeClient() {
        registerNoopRenderer("collision_trigger")
        registerNoopRenderer("interact_trigger")
        registerNoopRenderer("block_trigger")
        TriggerDebugRenderer(LevelRenderEvents.BEFORE_GIZMOS)
    }

    @Suppress("UNCHECKED_CAST")
    private fun registerNoopRenderer(id: String) {
        val type = BuiltInRegistries.ENTITY_TYPE.getValue(
            Identifier.fromNamespaceAndPath("storyteller", id),
        ) as EntityType<Entity>
        EntityRendererRegistry.register(type) { context -> NoopRenderer(context) }
    }
}
