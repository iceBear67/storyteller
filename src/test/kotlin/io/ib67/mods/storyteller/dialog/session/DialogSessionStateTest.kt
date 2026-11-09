package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.KnownKeys.SESSION_INTERVAL_TICKS
import io.ib67.mods.storyteller.KnownKeys.SESSION_NEXT_ACTION_TICK
import io.ib67.mods.storyteller.PlayerState
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DialogSessionStateTest {

    @Test
    fun `reload clears the dialog session`() {
        val id = "session-reload"
        DialogScriptRegistry.register(script(id))
        val state = PlayerState().apply {
            currentScriptId = id
            currentDialogId = id
            actionIndex = 3
            actionTicks = 5
            dialogReloadId = DialogScriptRegistry.reloadId
            this[SESSION_INTERVAL_TICKS] = 20L
            this[SESSION_NEXT_ACTION_TICK] = 100L
        }
        assertNotNull(resolveActiveDialog(state))
        DialogScriptRegistry.register(script("session-reload-other"))
        assertNull(resolveActiveDialog(state))
        assertNull(state.currentDialogId)
        assertEquals(0, state.actionIndex)
        assertNull(state.actionTicks)
        assertNull(state[SESSION_INTERVAL_TICKS])
        assertNull(state[SESSION_NEXT_ACTION_TICK])
        assertEquals(DialogScriptRegistry.reloadId, state.dialogReloadId)
    }

    @Test
    fun `missing dialog clears the dialog session`() {
        val id = "session-missing"
        DialogScriptRegistry.register(script(id))
        val state = PlayerState().apply {
            currentScriptId = id
            currentDialogId = "missing-dialog"
            actionIndex = 2
            actionTicks = 1
            dialogReloadId = DialogScriptRegistry.reloadId
            this[SESSION_INTERVAL_TICKS] = 20L
            this[SESSION_NEXT_ACTION_TICK] = 100L
        }
        assertNull(resolveActiveDialog(state))
        assertNull(state.currentDialogId)
        assertEquals(0, state.actionIndex)
        assertNull(state.actionTicks)
        assertNull(state[SESSION_INTERVAL_TICKS])
        assertNull(state[SESSION_NEXT_ACTION_TICK])
        assertEquals(id, state.currentScriptId)
    }

    private fun script(id: String): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )
}
