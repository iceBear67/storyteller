package io.ib67.mods.storyteller

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DebugToggleEventTest {

    @Test
    fun `toggle notifies listeners with the new state`() {
        val event = DebugToggleEvent()
        val states = mutableListOf<Boolean>()
        event.register { states += it }
        assertFalse(event.enabled)
        event.toggle()
        assertTrue(event.enabled)
        event.toggle()
        assertFalse(event.enabled)
        assertEquals(listOf(true, false), states)
    }
}
