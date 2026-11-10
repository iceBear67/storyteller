package io.ib67.mods.storyteller.dialog.session

import io.ib67.mods.storyteller.state.PlayerState
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogRef
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DialogDirectorTest {

    private val director = DialogDirector({ error("Player lookup is not used") }) { 20L }

    @Test
    fun `push and pop preserve frame order`() {
        val root = register("director-root")
        val child = register("director-child")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 100L)
        director.pushDialog(state, ref(child), 200L)
        assertEquals(
            listOf("director-root", "director-child"),
            state.dialogFrames.map { it.scriptId },
        )
        assertEquals(200L, state.currentFrame!!.nextActionTick)
        director.popDialog(state)
        assertEquals("director-root", state.currentFrame!!.scriptId)
    }

    @Test
    fun `empty allowed continuation permits any target`() {
        val root = register("director-open")
        val child = register("director-open-child")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L)
        director.pushDialog(state, ref(child), 0L)
        assertEquals("director-open-child", state.currentFrame!!.scriptId)
    }

    @Test
    fun `non empty allowed continuation blocks other targets`() {
        val root = register("director-locked")
        val allowed = register("director-allowed")
        val blocked = register("director-blocked")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L, listOf(ref(allowed)))
        assertEquals(listOf(ref(allowed)), state.currentFrame!!.allowedContinuation)
        assertTrue(director.canPushDialog(state, ref(allowed)))
        assertFalse(director.canPushDialog(state, ref(blocked)))
        assertThrows(IllegalStateException::class.java) {
            director.pushDialog(state, ref(blocked), 0L)
        }
        director.pushDialog(state, ref(allowed), 0L)
        assertEquals("director-allowed", state.currentFrame!!.scriptId)
    }

    @Test
    fun `unknown dialog reference is rejected`() {
        val state = PlayerState()
        assertThrows(IllegalStateException::class.java) {
            director.pushDialog(state, DialogRef("director-missing-script", "missing"), 0L)
        }
        assertTrue(state.dialogFrames.isEmpty())
    }

    @Test
    fun `progress updates the expected frame by identity`() {
        val root = register("director-progress")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L)
        val frame = state.currentFrame!!
        assertTrue(director.applyActionProgress(state, frame, false, 10L))
        assertEquals(1, state.currentFrame!!.actionTicks)
        assertTrue(director.applyActionProgress(state, state.currentFrame!!, true, 30L))
        assertEquals(1, state.currentFrame!!.actionIndex)
        assertNull(state.currentFrame!!.actionTicks)
        assertEquals(50L, state.currentFrame!!.nextActionTick)
    }

    @Test
    fun `progress distinguishes identical frames by identity`() {
        val root = register("director-identity-two")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L)
        val first = state.currentFrame!!
        state.pushFrame(first.copy())
        val second = state.currentFrame!!
        assertTrue(director.applyActionProgress(state, second, false, 10L))
        assertNull(state.dialogFrames[0].actionTicks)
        assertEquals(1, state.dialogFrames[1].actionTicks)
    }

    @Test
    fun `push resolves sub dialogs`() {
        val script = DialogScript(
            id = "director-sub",
            subDialogs = mapOf("branch" to Dialog(actions = emptyList(), id = "branch")),
            defaultDialog = Dialog(actions = emptyList(), id = "director-sub"),
        )
        DialogScriptRegistry.register(script)
        val state = PlayerState()
        director.pushDialog(state, DialogRef("director-sub", "branch"), 0L)
        assertEquals("director-sub", state.currentFrame!!.scriptId)
        assertEquals("branch", state.currentFrame!!.dialogId)
    }

    @Test
    fun `set allowed continuation replaces the top frame list`() {
        val root = register("director-expect")
        val first = register("director-expect-first")
        val second = register("director-expect-second")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L)
        state.markClean()
        director.setAllowedContinuation(state, listOf(ref(first)))
        assertEquals(listOf(ref(first)), state.currentFrame!!.allowedContinuation)
        assertTrue(state.dirty)
        assertTrue(director.canPushDialog(state, ref(first)))
        assertFalse(director.canPushDialog(state, ref(second)))
        val frame = state.currentFrame!!
        assertTrue(director.applyActionProgress(state, frame, false, 10L))
        assertEquals(listOf(ref(first)), state.currentFrame!!.allowedContinuation)
    }

    @Test
    fun `progress on a removed frame returns false`() {
        val root = register("director-removed")
        val state = PlayerState()
        director.pushDialog(state, ref(root), 0L)
        val frame = state.currentFrame!!
        state.setFrames(emptyList())
        assertFalse(director.applyActionProgress(state, frame, false, 10L))
        assertTrue(state.dialogFrames.isEmpty())
    }

    private fun register(id: String): DialogScript {
        val script = DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )
        DialogScriptRegistry.register(script)
        return script
    }

    private fun ref(script: DialogScript): DialogRef =
        DialogRef(script.id, script.defaultDialog.id)
}
