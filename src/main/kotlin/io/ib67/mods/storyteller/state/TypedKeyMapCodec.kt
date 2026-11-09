package io.ib67.mods.storyteller.state

import io.ib67.mods.storyteller.util.TypedKey
import io.ib67.mods.storyteller.util.TypedKeyMap
import net.minecraft.nbt.ByteTag
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.DoubleTag
import net.minecraft.nbt.FloatTag
import net.minecraft.nbt.IntTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.LongTag
import net.minecraft.nbt.ShortTag
import net.minecraft.nbt.StringTag
import net.minecraft.nbt.Tag

private const val TYPE_FIELD = "type"
private const val VALUE_FIELD = "value"

private const val TYPE_BOOLEAN = "boolean"
private const val TYPE_BYTE = "byte"
private const val TYPE_SHORT = "short"
private const val TYPE_INT = "int"
private const val TYPE_LONG = "long"
private const val TYPE_FLOAT = "float"
private const val TYPE_DOUBLE = "double"
private const val TYPE_STRING = "string"
private const val TYPE_LIST = "list"
private const val TYPE_MAP = "map"

object TypedKeyMapCodec {

    fun encode(state: TypedKeyMap): CompoundTag {
        val tag = CompoundTag()
        for (entry in state.entries()) {
            tag.put(entry.key.name, encodeValue(entry.value))
        }
        return tag
    }

    fun <T : TypedKeyMap> decodeInto(tag: CompoundTag, target: T): T {
        for ((name, valueTag) in tag.entrySet()) {
            target.put(TypedKey<Any>(name), decodeValue(valueTag))
        }
        return target
    }

    private fun encodeValue(value: Any): CompoundTag {
        val wrapper = CompoundTag()
        when (value) {
            is Boolean -> {
                wrapper.putString(TYPE_FIELD, TYPE_BOOLEAN)
                wrapper.put(VALUE_FIELD, ByteTag.valueOf(value))
            }
            is Byte -> {
                wrapper.putString(TYPE_FIELD, TYPE_BYTE)
                wrapper.put(VALUE_FIELD, ByteTag.valueOf(value))
            }
            is Short -> {
                wrapper.putString(TYPE_FIELD, TYPE_SHORT)
                wrapper.put(VALUE_FIELD, ShortTag.valueOf(value))
            }
            is Int -> {
                wrapper.putString(TYPE_FIELD, TYPE_INT)
                wrapper.put(VALUE_FIELD, IntTag.valueOf(value))
            }
            is Long -> {
                wrapper.putString(TYPE_FIELD, TYPE_LONG)
                wrapper.put(VALUE_FIELD, LongTag.valueOf(value))
            }
            is Float -> {
                wrapper.putString(TYPE_FIELD, TYPE_FLOAT)
                wrapper.put(VALUE_FIELD, FloatTag.valueOf(value))
            }
            is Double -> {
                wrapper.putString(TYPE_FIELD, TYPE_DOUBLE)
                wrapper.put(VALUE_FIELD, DoubleTag.valueOf(value))
            }
            is String -> {
                wrapper.putString(TYPE_FIELD, TYPE_STRING)
                wrapper.put(VALUE_FIELD, StringTag.valueOf(value))
            }
            is List<*> -> {
                wrapper.putString(TYPE_FIELD, TYPE_LIST)
                val list = ListTag()
                for (element in value) {
                    list.add(encodeValue(requireNotNull(element) { "State list cannot contain null" }))
                }
                wrapper.put(VALUE_FIELD, list)
            }
            is Map<*, *> -> {
                wrapper.putString(TYPE_FIELD, TYPE_MAP)
                val map = CompoundTag()
                for ((key, element) in value) {
                    require(key is String) { "State map keys must be strings: $key" }
                    map.put(key, encodeValue(requireNotNull(element) { "State map cannot contain null" }))
                }
                wrapper.put(VALUE_FIELD, map)
            }
            else -> throw IllegalArgumentException(
                "Unsupported state value type: ${value::class.qualifiedName}",
            )
        }
        return wrapper
    }

    private fun decodeValue(tag: Tag): Any {
        val wrapper = tag.asCompound().orElseThrow {
            IllegalArgumentException("State value is not a compound tag: $tag")
        }
        val type = wrapper.getString(TYPE_FIELD).orElseThrow {
            IllegalArgumentException("State value has no type field: $wrapper")
        }
        val valueTag = wrapper.get(VALUE_FIELD) ?: error("State value has no value field: $wrapper")
        return when (type) {
            TYPE_BOOLEAN -> valueTag.asBoolean().orElseThrow {
                IllegalArgumentException("State value is not a boolean: $valueTag")
            }
            TYPE_BYTE -> valueTag.asByte().orElseThrow {
                IllegalArgumentException("State value is not a byte: $valueTag")
            }
            TYPE_SHORT -> valueTag.asShort().orElseThrow {
                IllegalArgumentException("State value is not a short: $valueTag")
            }
            TYPE_INT -> valueTag.asInt().orElseThrow {
                IllegalArgumentException("State value is not an int: $valueTag")
            }
            TYPE_LONG -> valueTag.asLong().orElseThrow {
                IllegalArgumentException("State value is not a long: $valueTag")
            }
            TYPE_FLOAT -> valueTag.asFloat().orElseThrow {
                IllegalArgumentException("State value is not a float: $valueTag")
            }
            TYPE_DOUBLE -> valueTag.asDouble().orElseThrow {
                IllegalArgumentException("State value is not a double: $valueTag")
            }
            TYPE_STRING -> valueTag.asString().orElseThrow {
                IllegalArgumentException("State value is not a string: $valueTag")
            }
            TYPE_LIST -> valueTag.asList().orElseThrow {
                IllegalArgumentException("State value is not a list: $valueTag")
            }.map(::decodeValue)
            TYPE_MAP -> valueTag.asCompound().orElseThrow {
                IllegalArgumentException("State value is not a map: $valueTag")
            }.entrySet().associate { (key, value) -> key to decodeValue(value) }
            else -> error("Unknown state value type: $type")
        }
    }
}
