package io.ib67.mods.storyteller.dialog.runner

import org.graalvm.polyglot.PolyglotException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScriptConditionEvaluatorTest {

    private val evaluator = ScriptConditionEvaluator()

    @Test
    fun `nested map variables are visible`() {
        val variables = mapOf("count" to 2, "flag" to mapOf("done" to true))
        assertEquals(true, evaluator.evaluate("count > 1 && flag.done", variables))
    }

    @Test
    fun `string variables are compared`() {
        assertEquals(true, evaluator.evaluate("name === 'Alice'", mapOf("name" to "Alice")))
    }

    @Test
    fun `iterable variables support array methods`() {
        val variables = mapOf("items" to listOf("apple", "pear"))
        assertEquals(true, evaluator.evaluate("items.includes('apple')", variables))
    }

    @Test
    fun `non boolean result is rejected`() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            evaluator.evaluate("count + 1", mapOf("count" to 1))
        }
        assertTrue(error.message!!.contains("count + 1"))
    }

    @Test
    fun `undefined variable is rejected`() {
        assertThrows(PolyglotException::class.java) {
            evaluator.evaluate("missing", emptyMap())
        }
    }

    @Test
    fun `unsupported variable type is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            evaluator.evaluate("value", mapOf("value" to Any()))
        }
    }
}
