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
            """
            (function () {
              return function (__storyteller_context) {
                // 未声明的标识符会抛出 ReferenceError，导致还没写入的状态变量让整个条件失败。
                // 代理对象把所有字符串键都报告为存在，查不到时回落到 globalThis，
                // 这样未设置的玩家状态变量以 undefined 参与求值，Math 等全局对象仍然可用。
                var scope = new Proxy(__storyteller_context, {
                  has: function (target, key) { return typeof key === 'string'; },
                  get: function (target, key) { return key in target ? target[key] : globalThis[key]; }
                });
                with (scope) { return ($expression); }
              };
            })()
            """.trimIndent(),
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
