package io.ib67.mods.storyteller.entity

import io.ib67.mods.storyteller.DebugToggleEvent
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory

internal fun registerTriggerEntities(commandExecutor: CommandExecutor, debugToggle: DebugToggleEvent) {
    register(
        "collision_trigger",
        { type, level -> CollisionTrigger(type, level, commandExecutor, debugToggle.enabled) },
        1f,
        1f,
    )
    register("interact_trigger", { type, level -> InteractTrigger(type, level, debugToggle.enabled) }, 1f, 1f)
    register("block_trigger", { type, level -> BlockTrigger(type, level, debugToggle.enabled) }, 1f, 1f)
}

private fun <T : Entity> register(
    id: String,
    factory: EntityType.EntityFactory<T>,
    width: Float,
    height: Float,
): EntityType<T> {
    val identifier = Identifier.fromNamespaceAndPath("storyteller", id)
    val key = ResourceKey.create(Registries.ENTITY_TYPE, identifier)
    val type = EntityType.Builder.of(factory, MobCategory.MISC)
        .sized(width, height)
        .noLootTable()
        .fireImmune()
        .build(key)
    return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, type)
}
