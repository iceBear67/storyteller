package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.state.PlayerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogSessionStateTest {

    @Test
    fun `checksum change clears the frame list and suspension`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "old"
            pushFrame(frame())
            markSuspended()
        }
        state.useScriptChecksum("new")
        assertTrue(state.dialogFrames.isEmpty())
        assertFalse(state.isSuspended)
        assertEquals("new", state.dialogScriptChecksum)
    }

    @Test
    fun `matching checksum keeps the frame list and suspension`() {
        val frame = frame()
        val state = PlayerState().apply {
            dialogScriptChecksum = "same"
            pushFrame(frame)
            markSuspended()
        }
        state.useScriptChecksum("same")
        assertEquals(listOf(frame), state.dialogFrames)
        assertTrue(state.isSuspended)
    }

    @Test
    fun `reset clears the frame list and suspension`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "same"
            pushFrame(frame())
            markSuspended()
        }
        state.resetDialogState("current")
        assertTrue(state.dialogFrames.isEmpty())
        assertFalse(state.isSuspended)
        assertEquals("current", state.dialogScriptChecksum)
    }

    @Test
    fun `reset clears the frame list when the checksum is unchanged`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "same"
            pushFrame(frame())
        }
        state.resetDialogState("same")
        assertTrue(state.dialogFrames.isEmpty())
        assertEquals("same", state.dialogScriptChecksum)
    }

    private fun frame(): DialogFrame =
        DialogFrame(
            scriptId = "session-script",
            dialogId = "session-dialog",
            actionIndex = 0,
            actionTicks = null,
            intervalTicks = 20L,
            nextActionTick = 0L,
        )
}
