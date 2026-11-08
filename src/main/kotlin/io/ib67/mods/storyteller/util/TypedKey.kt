package io.ib67.mods.storyteller.util

import kotlin.reflect.KClass

class TypedKey<V : Any>(val type: KClass<V>, val name: String) {

    init {
        require(name.isNotEmpty()) { "TypedKey name cannot be empty" }
    }

    override fun toString(): String = "TypedKey(${type.simpleName}, $name)"

    companion object {
        inline fun <reified V : Any> of(name: String): TypedKey<V> = TypedKey(V::class, name)
    }
}

class TypedKeyMap {

    private class Entry(val type: KClass<*>, val value: Any)

    private val keysByName = mutableMapOf<String, Entry>()

    val size: Int
        get() = keysByName.size

    fun isEmpty(): Boolean = keysByName.isEmpty()

    fun contains(key: TypedKey<*>): Boolean = keysByName[key.name]?.type == key.type

    operator fun <V : Any> get(key: TypedKey<V>): V? {
        val entry = entryFor(key) ?: return null
        @Suppress("UNCHECKED_CAST")
        return entry.value as V
    }

    operator fun <V : Any> set(key: TypedKey<V>, value: V) {
        check(key.type.java.isInstance(value)) { "Value type does not match key ${key.type.simpleName}" }
        val existing = keysByName[key.name]
        require(existing == null || existing.type == key.type) {
            "Context variable name is already used by a key of another type: ${key.name}"
        }
        keysByName[key.name] = Entry(key.type, value)
    }

    fun <V : Any> remove(key: TypedKey<V>): V? {
        val entry = entryFor(key) ?: return null
        keysByName.remove(key.name)
        @Suppress("UNCHECKED_CAST")
        return entry.value as V
    }

    fun clear() {
        keysByName.clear()
    }

    fun namedValues(): Map<String, Any> = keysByName.mapValues { (_, entry) -> entry.value }

    private fun entryFor(key: TypedKey<*>): Entry? {
        val entry = keysByName[key.name] ?: return null
        require(entry.type == key.type) {
            "Context variable ${key.name} is stored as ${entry.type.simpleName}, requested ${key.type.simpleName}"
        }
        return entry
    }
}
