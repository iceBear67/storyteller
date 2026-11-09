package io.ib67.mods.storyteller

import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TypedKeyMapTest {

    private val nameKey = TypedKey.of<String>("name")
    private val countKey = TypedKey.of<Int>("count")

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

    @Test
    fun `equal names resolve to the same value`() {
        val map = TypedKeyMap()
        map[TypedKey.of<Int>("count")] = 3
        assertEquals(3, map[TypedKey.of<Int>("count")])
    }

    @Test
    fun `named values expose stored values`() {
        val map = TypedKeyMap()
        map[countKey] = 3
        assertEquals(mapOf("count" to 3), map.namedValues())
    }

    @Test
    fun `dirty flag follows modifications`() {
        val map = TypedKeyMap()
        assertFalse(map.dirty)
        map[countKey] = 3
        assertTrue(map.dirty)
        map.markClean()
        assertFalse(map.dirty)
        map.remove(countKey)
        assertTrue(map.dirty)
    }

    @Test
    fun `clear removes named values`() {
        val map = TypedKeyMap()
        map[countKey] = 3
        map.clear()
        assertTrue(map.namedValues().isEmpty())
    }
}
