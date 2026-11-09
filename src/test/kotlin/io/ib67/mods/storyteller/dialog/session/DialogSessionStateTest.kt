package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.PlayerState
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogSessionStateTest {

    private val director = DialogDirector({ error("Player lookup is not used") }) { 20L }

    @Test
    fun `reload clears the frame list`() {
        val id = "session-reload"
        DialogScriptRegistry.register(script(id))
        val state = PlayerState().apply {
            pushFrame(frame(id, id))
        }
        assertNotNull(director.resolveTop(state))
        DialogScriptRegistry.register(script("session-reload-other"))
        assertNull(director.resolveTop(state))
        assertTrue(state.dialogFrames.isEmpty())
    }

    @Test
    fun `missing dialog clears the frame list`() {
        val id = "session-missing"
        DialogScriptRegistry.register(script(id))
        val state = PlayerState().apply {
            pushFrame(frame(id, "missing-dialog"))
        }
        assertNull(director.resolveTop(state))
        assertTrue(state.dialogFrames.isEmpty())
    }

    private fun frame(scriptId: String, dialogId: String): DialogFrame =
        DialogFrame(
            scriptId = scriptId,
            dialogId = dialogId,
            actionIndex = 0,
            actionTicks = null,
            reloadId = DialogScriptRegistry.reloadId,
            intervalTicks = 20L,
            nextActionTick = 0L,
        )

    private fun script(id: String): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )
}
