package io.ib67.mods.storyteller.entity

import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB

class InteractTrigger(
    type: EntityType<InteractTrigger>,
    level: Level,
    initialDebug: Boolean,
) : Entity(type, level) {

    internal var attackCommand: String = ""
    internal var interactCommand: String = ""

    var isDebugMode: Boolean
        get() = entityData.get(DATA_DEBUG)
        internal set(value) {
            entityData.set(DATA_DEBUG, value)
        }

    init {
        noPhysics = true
        setNoGravity(true)
        isDebugMode = initialDebug
    }

    override fun isPickable(): Boolean = true

    fun debugBox(): AABB = boundingBox

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        builder.define(DATA_DEBUG, false)
    }

    protected override fun handlePortal() {}

    override fun isPushedByFluid(): Boolean = false

    override fun getPistonPushReaction(): PushReaction = PushReaction.IGNORE_ENTITY

    override fun isIgnoringBlockTriggers(): Boolean = true

    override fun readAdditionalSaveData(input: ValueInput) {
        attackCommand = input.getStringOr("runAttack", "")
        interactCommand = input.getStringOr("runInteract", "")
    }

    override fun addAdditionalSaveData(output: ValueOutput) {
        output.putString("runAttack", attackCommand)
        output.putString("runInteract", interactCommand)
    }

    override fun hurtServer(level: ServerLevel, source: DamageSource, amount: Float): Boolean = false

    companion object {
        private val DATA_DEBUG: EntityDataAccessor<Boolean> =
            SynchedEntityData.defineId(InteractTrigger::class.java, EntityDataSerializers.BOOLEAN)
    }
}
