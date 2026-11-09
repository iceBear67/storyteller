package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.dialog.DialogRef
import io.ib67.mods.storyteller.dialog.session.DialogFrame
import io.ib67.mods.storyteller.state.TypedKeyMapCodec
import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerStateCodecTest {

    private val codec = TypedKeyMapCodec

    @Test
    fun `player state codec round trips dialog frames`() {
        val parent = DialogFrame(
            scriptId = "codec-script",
            dialogId = "codec-script",
            actionIndex = 3,
            actionTicks = 4,
            intervalTicks = 20L,
            nextActionTick = 1234L,
            allowedContinuation = listOf(DialogRef("codec-script", "branch")),
        )
        val child = DialogFrame(
            scriptId = "codec-other",
            dialogId = "codec-child",
            actionIndex = 0,
            actionTicks = null,
            intervalTicks = 15L,
            nextActionTick = 1250L,
        )
        val state = PlayerState().apply {
            pushFrame(parent)
            pushFrame(child)
        }
        val decoded = codec.decodeInto(codec.encode(state), PlayerState())
        assertEquals(listOf(parent, child), decoded.dialogFrames)
        val top = checkNotNull(decoded.currentFrame)
        assertEquals("codec-other", top.scriptId)
        assertEquals("codec-child", top.dialogId)
        assertEquals(0, top.actionIndex)
        assertNull(top.actionTicks)
    }

    @Test
    fun `player state codec round trips script checksum`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "checksum-value"
        }
        val decoded = codec.decodeInto(codec.encode(state), PlayerState())
        assertEquals("checksum-value", decoded.dialogScriptChecksum)
        assertFalse(decoded.namedValues().containsKey("dialogScriptChecksum"))
    }

    @Test
    fun `named values expose the top frame and hide the frame list`() {
        val frame = DialogFrame(
            scriptId = "codec-values",
            dialogId = "codec-values",
            actionIndex = 2,
            actionTicks = 0,
            intervalTicks = 20L,
            nextActionTick = 100L,
        )
        val state = PlayerState().apply { pushFrame(frame) }
        val values = state.namedValues()
        assertEquals("codec-values", values["currentScriptId"])
        assertEquals("codec-values", values["currentDialogId"])
        assertEquals(2, values["actionIndex"])
        assertEquals(0, values["actionTicks"])
        assertEquals(20L, values["sessionIntervalTicks"])
        assertEquals(100L, values["sessionNextActionTick"])
        assertFalse(values.containsKey("dialogStack"))
        assertFalse(values.containsKey("dialogScriptChecksum"))
    }

    @Test
    fun `named values omit action ticks between actions`() {
        val frame = DialogFrame(
            scriptId = "codec-gap",
            dialogId = "codec-gap",
            actionIndex = 1,
            actionTicks = null,
            intervalTicks = 20L,
            nextActionTick = 100L,
        )
        val state = PlayerState().apply { pushFrame(frame) }
        assertFalse(state.namedValues().containsKey("actionTicks"))
    }

    @Test
    fun `global state codec round trips named values`() {
        val state = TypedKeyMap().apply {
            this[TypedKey.of("value")] = 15L
        }
        val tag = codec.encode(state)
        val decoded = codec.decodeInto(tag, TypedKeyMap())
        assertEquals(15L, decoded[TypedKey.of("value")])
    }

    @Test
    fun `codec decodes keys that were never declared`() {
        val key = TypedKey.of<String>("customName")
        val state = TypedKeyMap().apply {
            this[key] = "value"
        }
        val decoded = codec.decodeInto(codec.encode(state), TypedKeyMap())
        assertEquals("value", decoded[key])
    }

    @Test
    fun `codec round trips nested values`() {
        val key = TypedKey.of<Any>("nested")
        val value: Any = mapOf("done" to true, "items" to listOf(1L, "two", 3.5))
        val state = TypedKeyMap().apply {
            this[key] = value
        }
        val decoded = codec.decodeInto(codec.encode(state), TypedKeyMap())
        assertEquals(value, decoded[key])
    }
}
