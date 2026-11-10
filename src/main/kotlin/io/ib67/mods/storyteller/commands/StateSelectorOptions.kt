package io.ib67.mods.storyteller.commands

import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import io.ib67.mods.storyteller.KnownKeys
import io.ib67.mods.storyteller.dialog.Condition
import io.ib67.mods.storyteller.dialog.runner.ConditionEvaluator
import io.ib67.mods.storyteller.state.StateManager
import io.ib67.mods.storyteller.util.TypedKey
import java.util.Collections
import java.util.WeakHashMap
import net.fabricmc.fabric.api.command.v2.EntitySelectorOptionRegistry
import net.minecraft.commands.arguments.selector.EntitySelectorParser
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer

class StateSelectorOptions(
    private val stateManager: StateManager,
    private val conditionEvaluator: ConditionEvaluator,
) {

    private val pendingKeys: MutableMap<EntitySelectorParser, String> =
        Collections.synchronizedMap(WeakHashMap<EntitySelectorParser, String>())

    init {
        registerKey()
        registerValue()
        registerPredicate()
    }

    private fun registerKey() {
        EntitySelectorOptionRegistry.registerNonRepeatable(
            Identifier.fromNamespaceAndPath("st", "key"),
            Component.literal("Player state key"),
        ) { parser ->
            val keyName = parser.reader.readString()
            if (keyName.isEmpty()) {
                throw EMPTY_KEY_ERROR.createWithContext(parser.reader)
            }
            if (keyName in ScriptCommands.RESERVED_KEY_NAMES) {
                throw ScriptCommands.RESERVED_KEY_ERROR.createWithContext(parser.reader)
            }
            val key = TypedKey.of<Any>(keyName)
            pendingKeys[parser] = keyName
            parser.addPredicate { entity ->
                entity is ServerPlayer && stateManager.playerState(entity).contains(key)
            }
        }
    }

    private fun registerValue() {
        EntitySelectorOptionRegistry.registerNonRepeatable(
            Identifier.fromNamespaceAndPath("st", "value"),
            Component.literal("Player state value"),
        ) { parser ->
            val expected = parser.reader.readString()
            val keyName = pendingKeys.remove(parser)
                ?: throw VALUE_WITHOUT_KEY_ERROR.createWithContext(parser.reader)
            val key = TypedKey.of<Any>(keyName)
            parser.addPredicate { entity ->
                entity is ServerPlayer &&
                    stateManager.playerState(entity)[key]?.toString() == expected
            }
        }
    }

    private fun registerPredicate() {
        EntitySelectorOptionRegistry.registerNonRepeatable(
            Identifier.fromNamespaceAndPath("st", "predicate"),
            Component.literal("Player state condition"),
        ) { parser ->
            val expression = parser.reader.readString()
            if (expression.isBlank()) {
                throw EMPTY_PREDICATE_ERROR.createWithContext(parser.reader)
            }
            parser.addPredicate { entity ->
                if (entity !is ServerPlayer) {
                    false
                } else {
                    val variables = HashMap<String, Any>(stateManager.playerState(entity).namedValues())
                    variables[KnownKeys.GLOBAL_SCOPE] = stateManager.globalState.namedValues()
                    conditionEvaluator.evaluate(variables, Condition(expression))
                }
            }
        }
    }

    private companion object {

        val EMPTY_KEY_ERROR = SimpleCommandExceptionType(Component.literal("State key cannot be empty"))

        val VALUE_WITHOUT_KEY_ERROR = SimpleCommandExceptionType(Component.literal("st_value requires st_key before it"))

        val EMPTY_PREDICATE_ERROR = SimpleCommandExceptionType(Component.literal("Predicate expression cannot be empty"))
    }
}
