package io.ib67.mods.storyteller.entity

import io.ib67.mods.storyteller.dialog.CommandCall
import io.ib67.mods.storyteller.dialog.runner.CommandExecutor
import java.util.UUID
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB

class CollisionTrigger(
    type: EntityType<CollisionTrigger>,
    level: Level,
    private val commandExecutor: CommandExecutor,
    initialDebug: Boolean,
) : Entity(type, level) {

    internal var x1: Double = 0.0
    internal var y1: Double = 0.0
    internal var z1: Double = 0.0
    internal var x2: Double = 0.0
    internal var y2: Double = 0.0
    internal var z2: Double = 0.0
    internal var enterCommand: String = ""
    internal var leaveCommand: String = ""

    var isDebugMode: Boolean
        get() = entityData.get(DATA_DEBUG)
        internal set(value) {
            entityData.set(DATA_DEBUG, value)
        }

    private val insidePlayers = mutableSetOf<UUID>()

    init {
        noPhysics = true
        setNoGravity(true)
        isDebugMode = initialDebug
    }

    override fun tick() {
        super.tick()
        val serverLevel = level() as? ServerLevel ?: return
        val region = region()
        val present = mutableSetOf<UUID>()
        for (player in serverLevel.players()) {
            if (player.isSpectator || !player.boundingBox.intersects(region)) {
                continue
            }
            present += player.uuid
            if (!insidePlayers.add(player.uuid)) {
                continue
            }
            runCommand(player, enterCommand)
            if (isRemoved) {
                return
            }
        }
        val server = serverLevel.server
        for (uuid in insidePlayers.toList()) {
            if (uuid in present) {
                continue
            }
            insidePlayers.remove(uuid)
            val player = server.playerList.getPlayer(uuid) ?: continue
            if (player.isSpectator) {
                continue
            }
            runCommand(player, leaveCommand)
            if (isRemoved) {
                return
            }
        }
    }

    fun debugBox(): AABB {
        val position = position()
        val sx1 = entityData.get(DATA_X1).toDouble()
        val sy1 = entityData.get(DATA_Y1).toDouble()
        val sz1 = entityData.get(DATA_Z1).toDouble()
        val sx2 = entityData.get(DATA_X2).toDouble()
        val sy2 = entityData.get(DATA_Y2).toDouble()
        val sz2 = entityData.get(DATA_Z2).toDouble()
        return AABB(
            position.x + minOf(sx1, sx2),
            position.y + minOf(sy1, sy2),
            position.z + minOf(sz1, sz2),
            position.x + maxOf(sx1, sx2),
            position.y + maxOf(sy1, sy2),
            position.z + maxOf(sz1, sz2),
        )
    }

    private fun region(): AABB {
        val position = position()
        return AABB(
            position.x + minOf(x1, x2),
            position.y + minOf(y1, y2),
            position.z + minOf(z1, z2),
            position.x + maxOf(x1, x2),
            position.y + maxOf(y1, y2),
            position.z + maxOf(z1, z2),
        )
    }

    private fun runCommand(player: ServerPlayer, command: String) {
        if (command.isEmpty() || player.hasDisconnected()) {
            return
        }
        commandExecutor.run(player, CommandCall.DEFAULT_SELECTOR, command)
    }

    override fun isPickable(): Boolean = isDebugMode

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_X1, 0f)
        builder.define(DATA_Y1, 0f)
        builder.define(DATA_Z1, 0f)
        builder.define(DATA_X2, 0f)
        builder.define(DATA_Y2, 0f)
        builder.define(DATA_Z2, 0f)
        builder.define(DATA_DEBUG, false)
    }

    protected override fun handlePortal() {}

    override fun isPushedByFluid(): Boolean = false

    override fun getPistonPushReaction(): PushReaction = PushReaction.IGNORE_ENTITY

    override fun isIgnoringBlockTriggers(): Boolean = true

    override fun readAdditionalSaveData(input: ValueInput) {
        x1 = input.getDoubleOr("x1", 0.0)
        y1 = input.getDoubleOr("y1", 0.0)
        z1 = input.getDoubleOr("z1", 0.0)
        x2 = input.getDoubleOr("x2", 0.0)
        y2 = input.getDoubleOr("y2", 0.0)
        z2 = input.getDoubleOr("z2", 0.0)
        enterCommand = input.getStringOr("enter", "")
        leaveCommand = input.getStringOr("leave", "")
        entityData.set(DATA_X1, x1.toFloat())
        entityData.set(DATA_Y1, y1.toFloat())
        entityData.set(DATA_Z1, z1.toFloat())
        entityData.set(DATA_X2, x2.toFloat())
        entityData.set(DATA_Y2, y2.toFloat())
        entityData.set(DATA_Z2, z2.toFloat())
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.putDouble("x1", x1)
        output.putDouble("y1", y1)
        output.putDouble("z1", z1)
        output.putDouble("x2", x2)
        output.putDouble("y2", y2)
        output.putDouble("z2", z2)
        output.putString("enter", enterCommand)
        output.putString("leave", leaveCommand)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean = false

    companion object {
        private val DATA_X1: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_Y1: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_Z1: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_X2: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_Y2: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_Z2: EntityDataAccessor<Float> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.FLOAT)

        private val DATA_DEBUG: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(CollisionTrigger::class.java, EntityDataSerializers.BOOLEAN)
    }
}
