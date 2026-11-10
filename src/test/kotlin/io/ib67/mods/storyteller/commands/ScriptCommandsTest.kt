package io.ib67.mods.storyteller.commands

import io.ib67.mods.storyteller.dialog.DialogRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScriptCommandsTest {

    @Test
    fun `dialog reference uses the default script`() {
        assertEquals(DialogRef("script", "dialog"), parseDialogRef("dialog", "script"))
    }

    @Test
    fun `dialog reference uses the explicit script`() {
        assertEquals(DialogRef("other", "dialog"), parseDialogRef("other:dialog", "script"))
    }

    @Test
    fun `empty script part uses the default script`() {
        assertEquals(DialogRef("script", "dialog"), parseDialogRef(":dialog", "script"))
    }

    @Test
    fun `dialog reference without a script needs a default`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDialogRef("dialog", null)
        }
    }

    @Test
    fun `dialog reference with an empty dialog id is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseDialogRef("script:", "other")
        }
    }
}
