package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.util.TypedKeyMap
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player

class PlayerState {

    var currentScript: DialogScript? = null

    var currentDialog: Dialog? = null

    val context: TypedKeyMap = TypedKeyMap()
}

object PlayerStateAttachment {

    val TYPE: AttachmentType<PlayerState> = AttachmentRegistry.createDefaulted(
        Identifier.fromNamespaceAndPath("storyteller", "player_state"),
        ::PlayerState,
    )

    fun register(): AttachmentType<PlayerState> = TYPE
}

val Player.storyState: PlayerState
    get() = (this as AttachmentTarget).getAttachedOrCreate(PlayerStateAttachment.TYPE)
