package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogRef
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ScriptCommandsTest {

    @Test
    fun `boolean literals are parsed`() {
        assertEquals(true, coerceValue("true"))
        assertEquals(false, coerceValue("false"))
    }

    @Test
    fun `integer literals are parsed`() {
        assertEquals(1, coerceValue("1"))
        assertEquals(-2, coerceValue("-2"))
        assertEquals(3_000_000_000L, coerceValue("3000000000"))
    }

    @Test
    fun `decimal literals are parsed`() {
        assertEquals(1.5, coerceValue("1.5"))
        assertEquals(0.5, coerceValue(".5"))
        assertEquals(1000.0, coerceValue("1e3"))
    }

    @Test
    fun `out of range integers are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            coerceValue("99999999999999999999999999")
        }
    }

    @Test
    fun `quoted values lose paired quotes`() {
        assertEquals(1, coerceValue("\"1\""))
        assertEquals("hello world", coerceValue("\"hello world\""))
        assertEquals("", coerceValue("\"\""))
    }

    @Test
    fun `unquoted text is a string`() {
        assertEquals("hello world", coerceValue("hello world"))
        assertEquals("1a", coerceValue("1a"))
    }

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
