package io.ib67.mods.storyteller

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.util.TypedKeyMap
import java.util.Optional
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player

class PlayerState {

    var currentScriptId: String? = null

    var currentDialogId: String? = null

    var actionIndex: Int = 0

    var dialogReloadId: Long = 0

    val context: TypedKeyMap = TypedKeyMap()

    val currentScript: DialogScript?
        get() = currentScriptId?.let(DialogScriptRegistry::get)

    val currentDialog: Dialog?
        get() {
            val script = currentScript ?: return null
            val id = currentDialogId ?: return null
            return if (id == script.id) script.defaultDialog else script.subDialogs[id]
        }
}

internal val PLAYER_STATE_CODEC: Codec<PlayerState> = RecordCodecBuilder.create { instance ->
    instance.group(
        Codec.STRING.optionalFieldOf("script").forGetter { Optional.ofNullable(it.currentScriptId) },
        Codec.STRING.optionalFieldOf("dialog").forGetter { Optional.ofNullable(it.currentDialogId) },
        Codec.INT.optionalFieldOf("action_index", 0).forGetter { it.actionIndex },
        Codec.LONG.optionalFieldOf("dialog_reload_id", 0L).forGetter { it.dialogReloadId },
        Codec.LONG.optionalFieldOf("interval_ticks").forGetter {
            Optional.ofNullable(it.context[KnownKeys.SESSION_INTERVAL_TICKS])
        },
        Codec.LONG.optionalFieldOf("next_action_tick").forGetter {
            Optional.ofNullable(it.context[KnownKeys.SESSION_NEXT_ACTION_TICK])
        },
    ).apply(instance) { script, dialog, index, reloadId, interval, nextTick ->
        PlayerState().apply {
            currentScriptId = script.orElse(null)
            currentDialogId = dialog.orElse(null)
            actionIndex = index
            dialogReloadId = reloadId
            interval.ifPresent { context[KnownKeys.SESSION_INTERVAL_TICKS] = it }
            nextTick.ifPresent { context[KnownKeys.SESSION_NEXT_ACTION_TICK] = it }
        }
    }
}

object PlayerStateAttachment {

    val TYPE: AttachmentType<PlayerState> = AttachmentRegistry.create(
        Identifier.fromNamespaceAndPath("storyteller", "player_state"),
    ) { builder ->
        builder.initializer { PlayerState() }
        builder.persistent(PLAYER_STATE_CODEC)
        builder.copyOnDeath()
    }

    fun register(): AttachmentType<PlayerState> = TYPE
}

val Player.storyState: PlayerState
    get() = (this as AttachmentTarget).getAttachedOrCreate(PlayerStateAttachment.TYPE)
