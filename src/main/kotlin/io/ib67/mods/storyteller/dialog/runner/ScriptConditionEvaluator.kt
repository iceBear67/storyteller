package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.Condition
import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Source
import org.graalvm.polyglot.Value
import org.graalvm.polyglot.proxy.ProxyArray
import org.graalvm.polyglot.proxy.ProxyObject

class ConditionEvaluationException(expression: String, cause: Throwable) :
    RuntimeException("Condition expression failed: $expression (${cause.message ?: cause::class.simpleName})", cause)

class ScriptConditionEvaluator : ConditionEvaluator {

    private val context: Context = Context.newBuilder("js")
        .allowExperimentalOptions(true)
        .option("engine.WarnInterpreterOnly", "false")
        .build()

    private val compiledExpressions = mutableMapOf<String, Value>()

    override fun evaluate(variables: Map<String, Any>, condition: Condition): Boolean =
        evaluate(condition.expression, variables)

    fun evaluate(expression: String, variables: Map<String, Any>): Boolean =
        try {
            val function = compiledExpressions.getOrPut(expression) { compile(expression) }
            val scope = ProxyObject.fromMap(variables.mapValues { (_, value) -> toScriptValue(value) })
            val result = function.execute(scope)
            if (!result.isBoolean) {
                throw IllegalArgumentException(
                    "Condition expression must evaluate to a boolean: $expression (result: $result)",
                )
            }
            result.asBoolean()
        } catch (exception: RuntimeException) {
            throw ConditionEvaluationException(expression, exception)
        }

    private fun compile(expression: String): Value {
        val source = Source.newBuilder(
            "js",
            "(function (__storyteller_context) { with (__storyteller_context) { return ($expression); } })",
            "condition:$expression",
        ).buildLiteral()
        return context.eval(source)
    }

    private fun toScriptValue(value: Any?): Any? = when (value) {
        null -> null
        is String, is Boolean, is Number -> value
        is Map<*, *> -> ProxyObject.fromMap(
            value.entries.associate { (key, entryValue) -> key.toString() to toScriptValue(entryValue) },
        )
        is Iterable<*> -> ProxyArray.fromList(value.map { toScriptValue(it) })
        is Array<*> -> ProxyArray.fromList(value.map { toScriptValue(it) })
        else -> throw IllegalArgumentException(
            "Unsupported condition variable type: ${value::class.qualifiedName}",
        )
    }
}
