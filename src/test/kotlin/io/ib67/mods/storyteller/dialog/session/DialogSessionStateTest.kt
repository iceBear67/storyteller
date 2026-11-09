package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.state.PlayerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogSessionStateTest {

    @Test
    fun `checksum change clears the frame list`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "old"
            pushFrame(frame())
        }
        state.useScriptChecksum("new")
        assertTrue(state.dialogFrames.isEmpty())
        assertEquals("new", state.dialogScriptChecksum)
    }

    @Test
    fun `matching checksum keeps the frame list`() {
        val frame = frame()
        val state = PlayerState().apply {
            dialogScriptChecksum = "same"
            pushFrame(frame)
        }
        state.useScriptChecksum("same")
        assertEquals(listOf(frame), state.dialogFrames)
    }

    @Test
    fun `reset clears the frame list`() {
        val state = PlayerState().apply {
            dialogScriptChecksum = "same"
            pushFrame(frame())
        }
        state.resetDialogState("current")
        assertTrue(state.dialogFrames.isEmpty())
        assertEquals("current", state.dialogScriptChecksum)
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
