package io.ib67.mods.storyteller.client

import io.ib67.mods.storyteller.entity.BlockTrigger
import io.ib67.mods.storyteller.entity.CollisionTrigger
import io.ib67.mods.storyteller.entity.InteractTrigger
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.event.Event
import net.minecraft.client.Minecraft
import net.minecraft.gizmos.GizmoStyle
import net.minecraft.gizmos.Gizmos

class TriggerDebugRenderer(
    levelRenderEvents: Event<LevelRenderEvents.BeforeGizmos>,
) {

    init {
        levelRenderEvents.register(::render)
    }

    private fun render(context: LevelRenderContext) {
        val level = Minecraft.getInstance().level ?: return
        for (entity in level.entitiesForRendering()) {
            when (entity) {
                is CollisionTrigger -> {
                    if (entity.isDebugMode) {
                        Gizmos.cuboid(entity.debugBox(), COLLISION_STYLE)
                    }
                }
                is InteractTrigger -> {
                    if (entity.isDebugMode) {
                        Gizmos.cuboid(entity.debugBox(), INTERACT_STYLE)
                    }
                }
                is BlockTrigger -> {
                    if (entity.isDebugMode) {
                        Gizmos.cuboid(entity.debugBox(), BLOCK_STYLE)
                    }
                }
            }
        }
    }

    companion object {
        private val COLLISION_STYLE: GizmoStyle = GizmoStyle.stroke(0xFFFF4040.toInt())
        private val INTERACT_STYLE: GizmoStyle = GizmoStyle.stroke(0xFF40FF40.toInt())
        private val BLOCK_STYLE: GizmoStyle = GizmoStyle.stroke(0xFF4090FF.toInt())
    }
}
