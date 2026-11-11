package io.ib67.mods.storyteller.entity

import io.ib67.mods.storyteller.DebugToggleEvent
import io.ib67.mods.storyteller.commands.formatStateDump
import io.ib67.mods.storyteller.dialog.CommandCall
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.BlockEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult

class TriggerEvents(
    private val commandExecutor: CommandExecutor,
    private val debugToggle: DebugToggleEvent,
    serverStarted: Event<ServerLifecycleEvents.ServerStarted>,
    useEntity: Event<UseEntityCallback>,
    attackEntity: Event<AttackEntityCallback>,
    blockBreak: Event<PlayerBlockBreakEvents.After>,
    useItemOn: Event<BlockEvents.UseItemOnCallback>,
) {

    private var server: MinecraftServer? = null

    init {
        serverStarted.register { server = it }
        debugToggle.register(::onDebugToggle)
        useEntity.register(::onUseEntity)
        attackEntity.register(::onAttackEntity)
        blockBreak.register(::onBlockBreak)
        useItemOn.register(::onUseItemOn)
    }

    private fun onUseEntity(
        player: Player,
        level: Level,
        hand: InteractionHand,
        entity: Entity,
        hitResult: EntityHitResult?,
    ): InteractionResult {
        if (level !is ServerLevel || player !is ServerPlayer) {
            return InteractionResult.PASS
        }
        if (player.isSpectator || !player.isWithinEntityInteractionRange(entity.boundingBox, 3.0)) {
            return InteractionResult.PASS
        }
        if (isTrigger(entity) && player.getItemInHand(hand).item == Items.DEBUG_STICK) {
            showProperties(player, entity)
            return InteractionResult.SUCCESS
        }
        if (entity !is InteractTrigger) {
            return InteractionResult.PASS
        }
        runCommand(player, entity.interactCommand)
        return InteractionResult.SUCCESS
    }

    private fun onAttackEntity(
        player: Player,
        level: Level,
        hand: InteractionHand,
        entity: Entity,
        hitResult: EntityHitResult?,
    ): InteractionResult {
        if (level !is ServerLevel || player !is ServerPlayer) {
            return InteractionResult.PASS
        }
        if (player.isSpectator || !player.isWithinEntityInteractionRange(entity.boundingBox, 3.0)) {
            return InteractionResult.PASS
        }
        if (isTrigger(entity) && player.getItemInHand(hand).item == Items.DEBUG_STICK) {
            showProperties(player, entity)
            return InteractionResult.SUCCESS
        }
        if (entity !is InteractTrigger) {
            return InteractionResult.PASS
        }
        runCommand(player, entity.attackCommand)
        return InteractionResult.SUCCESS
    }

    private fun onBlockBreak(
        level: Level,
        player: Player,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
    ) {
        if (level !is ServerLevel || player !is ServerPlayer) {
            return
        }
        for (trigger in blockTriggersAt(level, pos)) {
            runCommand(player, trigger.breakCommand)
        }
    }

    private fun onUseItemOn(
        stack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult,
    ): InteractionResult? {
        if (level !is ServerLevel || player !is ServerPlayer) {
            return null
        }
        if (stack.item == Items.DEBUG_STICK) {
            val triggers = blockTriggersAt(level, pos)
            if (triggers.isNotEmpty()) {
                for (trigger in triggers) {
                    showProperties(player, trigger)
                }
                return InteractionResult.SUCCESS
            }
            return null
        }
        if (stack.item !is BlockItem) {
            return null
        }
        val target = BlockPlaceContext(player, hand, stack, hitResult).clickedPos
        if (blockTriggersAt(level, target).isEmpty()) {
            return null
        }
        val before = level.getBlockState(target)
        val placedItem = stack.item
        level.server.execute {
            if (player.hasDisconnected()) {
                return@execute
            }
            val after = level.getBlockState(target)
            if (after.isAir || after.block == before.block || after.block.asItem() != placedItem) {
                return@execute
            }
            for (trigger in blockTriggersAt(level, target)) {
                runCommand(player, trigger.placeCommand)
            }
        }
        return null
    }

    private fun onDebugToggle(enabled: Boolean) {
        val server = server ?: return
        for (level in server.allLevels) {
            for (entity in level.allEntities) {
                when (entity) {
                    is CollisionTrigger -> entity.isDebugMode = enabled
                    is InteractTrigger -> entity.isDebugMode = enabled
                    is BlockTrigger -> entity.isDebugMode = enabled
                }
            }
        }
    }

    private fun isTrigger(entity: Entity): Boolean =
        entity is CollisionTrigger || entity is InteractTrigger || entity is BlockTrigger

    private fun showProperties(player: ServerPlayer, entity: Entity) {
        val entries = when (entity) {
            is CollisionTrigger -> linkedMapOf(
                "type" to "collision_trigger",
                "x1" to entity.x1.toString(),
                "y1" to entity.y1.toString(),
                "z1" to entity.z1.toString(),
                "x2" to entity.x2.toString(),
                "y2" to entity.y2.toString(),
                "z2" to entity.z2.toString(),
                "enter" to entity.enterCommand,
                "leave" to entity.leaveCommand,
            )
            is InteractTrigger -> linkedMapOf(
                "type" to "interact_trigger",
                "runAttack" to entity.attackCommand,
                "runInteract" to entity.interactCommand,
            )
            is BlockTrigger -> linkedMapOf(
                "type" to "block_trigger",
                "pos" to "${entity.blockPosition().x}, ${entity.blockPosition().y}, ${entity.blockPosition().z}",
                "runPlace" to entity.placeCommand,
                "runBreak" to entity.breakCommand,
            )
            else -> return
        }
        player.sendSystemMessage(formatStateDump(entries.toSortedMap()))
    }

    private fun blockTriggersAt(level: ServerLevel, pos: BlockPos): List<BlockTrigger> =
        level.getEntitiesOfClass(BlockTrigger::class.java, AABB(pos).inflate(1.0)) {
            it.blockPosition() == pos
        }

    private fun runCommand(player: ServerPlayer, command: String) {
        if (command.isEmpty() || player.hasDisconnected()) {
            return
        }
        commandExecutor.run(player, CommandCall.DEFAULT_SELECTOR, command)
    }
}
