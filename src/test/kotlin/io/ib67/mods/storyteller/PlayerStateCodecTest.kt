package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.KnownKeys.SESSION_INTERVAL_TICKS
import io.ib67.mods.storyteller.KnownKeys.SESSION_NEXT_ACTION_TICK
import io.ib67.mods.storyteller.state.TypedKeyMapCodec
import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerStateCodecTest {

    private val codec = TypedKeyMapCodec

    @Test
    fun `player state codec round trips session fields`() {
        val state = PlayerState().apply {
            currentScriptId = "script"
            currentDialogId = "dialog"
            actionIndex = 3
            actionTicks = 4
            dialogReloadId = 42L
            this[SESSION_INTERVAL_TICKS] = 20L
            this[SESSION_NEXT_ACTION_TICK] = 1234L
        }
        val tag = codec.encode(state)
        val decoded = codec.decodeInto(tag, PlayerState())
        assertEquals("script", decoded.currentScriptId)
        assertEquals("dialog", decoded.currentDialogId)
        assertEquals(3, decoded.actionIndex)
        assertEquals(4, decoded.actionTicks)
        assertEquals(42L, decoded.dialogReloadId)
        assertEquals(20L, decoded[SESSION_INTERVAL_TICKS])
        assertEquals(1234L, decoded[SESSION_NEXT_ACTION_TICK])
    }

    @Test
    fun `global state codec round trips named values`() {
        val state = TypedKeyMap().apply {
            this[SESSION_INTERVAL_TICKS] = 15L
        }
        val tag = codec.encode(state)
        val decoded = codec.decodeInto(tag, TypedKeyMap())
        assertEquals(15L, decoded[SESSION_INTERVAL_TICKS])
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
