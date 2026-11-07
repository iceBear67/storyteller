package io.ib67.mods.storyteller

import net.minecraft.nbt.NbtOps
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PlayerStateCodecTest {

    @Test
    fun `player state codec round trips session fields`() {
        val state = PlayerState().apply {
            currentScriptId = "script"
            currentDialogId = "dialog"
            actionIndex = 3
            dialogReloadId = 42L
            context[KnownKeys.SESSION_INTERVAL_TICKS] = 20L
            context[KnownKeys.SESSION_NEXT_ACTION_TICK] = 1234L
        }
        val tag = PLAYER_STATE_CODEC.encodeStart(NbtOps.INSTANCE, state).getOrThrow()
        val decoded = PLAYER_STATE_CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow()
        assertEquals("script", decoded.currentScriptId)
        assertEquals("dialog", decoded.currentDialogId)
        assertEquals(3, decoded.actionIndex)
        assertEquals(42L, decoded.dialogReloadId)
        assertEquals(20L, decoded.context[KnownKeys.SESSION_INTERVAL_TICKS])
        assertEquals(1234L, decoded.context[KnownKeys.SESSION_NEXT_ACTION_TICK])
    }
}
