package io.ib67.mods.storyteller.util

import kotlin.reflect.KClass

class TypedKey<V : Any>(val type: KClass<V>) {

    override fun toString(): String = "TypedKey(${type.simpleName})"

    companion object {
        inline fun <reified V : Any> of(): TypedKey<V> = TypedKey(V::class)
    }
}

class TypedKeyMap {

    private val values = mutableMapOf<TypedKey<*>, Any>()

    val size: Int
        get() = values.size

    fun isEmpty(): Boolean = values.isEmpty()

    fun contains(key: TypedKey<*>): Boolean = key in values

    operator fun <V : Any> get(key: TypedKey<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return values[key] as V?
    }

    operator fun <V : Any> set(key: TypedKey<V>, value: V) {
        check(key.type.java.isInstance(value)) { "值的类型与键 ${key.type.simpleName} 不匹配" }
        values[key] = value
    }

    fun <V : Any> remove(key: TypedKey<V>): V? {
        @Suppress("UNCHECKED_CAST")
        return values.remove(key) as V?
    }

    fun clear() {
        values.clear()
    }
}
