package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypedKeyMapTest {

    private val nameKey = TypedKey.of<String>()
    private val countKey = TypedKey.of<Int>()

    @Test
    fun `values are stored by key`() {
        val map = TypedKeyMap()
        map[nameKey] = "Alice"
        map[countKey] = 3
        assertEquals("Alice", map[nameKey])
        assertEquals(3, map[countKey])
    }

    @Test
    fun `missing key returns null`() {
        assertNull(TypedKeyMap()[nameKey])
    }

    @Test
    fun `remove returns previous value`() {
        val map = TypedKeyMap()
        map[nameKey] = "Alice"
        assertEquals("Alice", map.remove(nameKey))
        assertFalse(map.contains(nameKey))
        assertNull(map[nameKey])
    }

    @Test
    fun `contains and size reflect stored keys`() {
        val map = TypedKeyMap()
        assertTrue(map.isEmpty())
        map[nameKey] = "Alice"
        assertTrue(map.contains(nameKey))
        assertEquals(1, map.size)
        map.clear()
        assertTrue(map.isEmpty())
    }
}
