package io.ib67.mods.storyteller.state

import io.ib67.mods.storyteller.dialog.DialogRef
import io.ib67.mods.storyteller.dialog.session.DialogFrame
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag

private const val SCRIPT_ID = "scriptId"
private const val DIALOG_ID = "dialogId"
private const val ACTION_INDEX = "actionIndex"
private const val ACTION_TICKS = "actionTicks"
private const val RELOAD_ID = "reloadId"
private const val INTERVAL_TICKS = "intervalTicks"
private const val NEXT_ACTION_TICK = "nextActionTick"
private const val ALLOWED_CONTINUATION = "allowedContinuation"

class DialogFrameCodec {

    fun encode(frame: DialogFrame): CompoundTag {
        val tag = CompoundTag()
        tag.putString(SCRIPT_ID, frame.scriptId)
        tag.putString(DIALOG_ID, frame.dialogId)
        tag.putInt(ACTION_INDEX, frame.actionIndex)
        frame.actionTicks?.let { tag.putInt(ACTION_TICKS, it) }
        tag.putLong(RELOAD_ID, frame.reloadId)
        tag.putLong(INTERVAL_TICKS, frame.intervalTicks)
        tag.putLong(NEXT_ACTION_TICK, frame.nextActionTick)
        val continuation = ListTag()
        for ((scriptId, dialogId) in frame.allowedContinuation) {
            val entry = CompoundTag()
            entry.putString(SCRIPT_ID, scriptId)
            entry.putString(DIALOG_ID, dialogId)
            continuation.add(entry)
        }
        tag.put(ALLOWED_CONTINUATION, continuation)
        return tag
    }

    fun decode(tag: CompoundTag): DialogFrame {
        val continuationTag = tag.getList(ALLOWED_CONTINUATION).orElse(null)
        val allowedContinuation = if (continuationTag == null) {
            emptyList()
        } else {
            continuationTag.indices.map { index ->
                val entry = continuationTag.getCompound(index).orElseThrow {
                    IllegalArgumentException("Dialog continuation is not a compound tag: $continuationTag")
                }
                DialogRef(
                    scriptId = entry.getString(SCRIPT_ID).orElseThrow {
                        IllegalArgumentException("Dialog continuation is missing scriptId: $entry")
                    },
                    dialogId = entry.getString(DIALOG_ID).orElseThrow {
                        IllegalArgumentException("Dialog continuation is missing dialogId: $entry")
                    },
                )
            }
        }
        return DialogFrame(
            scriptId = tag.getString(SCRIPT_ID).orElseThrow {
                IllegalArgumentException("Dialog frame is missing scriptId: $tag")
            },
            dialogId = tag.getString(DIALOG_ID).orElseThrow {
                IllegalArgumentException("Dialog frame is missing dialogId: $tag")
            },
            actionIndex = tag.getInt(ACTION_INDEX).orElseThrow {
                IllegalArgumentException("Dialog frame is missing actionIndex: $tag")
            },
            actionTicks = if (tag.contains(ACTION_TICKS)) {
                tag.getInt(ACTION_TICKS).orElseThrow {
                    IllegalArgumentException("Dialog frame actionTicks is not an int: $tag")
                }
            } else {
                null
            },
            reloadId = tag.getLong(RELOAD_ID).orElseThrow {
                IllegalArgumentException("Dialog frame is missing reloadId: $tag")
            },
            intervalTicks = tag.getLong(INTERVAL_TICKS).orElseThrow {
                IllegalArgumentException("Dialog frame is missing intervalTicks: $tag")
            },
            nextActionTick = tag.getLong(NEXT_ACTION_TICK).orElseThrow {
                IllegalArgumentException("Dialog frame is missing nextActionTick: $tag")
            },
            allowedContinuation = allowedContinuation,
        )
    }
}
