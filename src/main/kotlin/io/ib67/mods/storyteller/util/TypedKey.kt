package io.ib67.mods.storyteller.util

class TypedKey<V : Any>(val name: String) {

    init {
        require(name.isNotEmpty()) { "TypedKey name cannot be empty" }
    }

    override fun toString(): String = "TypedKey($name)"

    companion object {
        fun <V : Any> of(name: String): TypedKey<V> = TypedKey(name)
    }
}

open class TypedKeyMap {

    internal class Entry(val key: TypedKey<*>, val value: Any)

    private val entriesByName = mutableMapOf<String, Entry>()

    var dirty: Boolean = false
        private set

    val size: Int
        get() = entriesByName.size

    fun isEmpty(): Boolean = entriesByName.isEmpty()

    fun contains(key: TypedKey<*>): Boolean = entriesByName.containsKey(key.name)

    operator fun <V : Any> get(key: TypedKey<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return entriesByName[key.name]?.value as V?
    }

    operator fun <V : Any> set(key: TypedKey<V>, value: V) = put(key, value)

    fun <V : Any> remove(key: TypedKey<V>): V? {
        @Suppress("UNCHECKED_CAST")
        val previous = entriesByName.remove(key.name)?.value as V?
        if (previous != null) {
            dirty = true
        }
        return previous
    }

    fun clear() {
        if (entriesByName.isEmpty()) {
            return
        }
        entriesByName.clear()
        dirty = true
    }

    fun markClean() {
        dirty = false
    }

    open fun namedValues(): Map<String, Any> = entriesByName.mapValues { (_, entry) -> entry.value }

    internal fun entries(): Collection<Entry> = entriesByName.values

    internal fun put(key: TypedKey<*>, value: Any) {
        entriesByName[key.name] = Entry(key, value)
        dirty = true
    }
}
