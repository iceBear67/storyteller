package io.ib67.mods.storyteller.dialog.runner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        val error = assertThrows(ConditionEvaluationException::class.java) {
            evaluator.evaluate("count + 1", mapOf("count" to 1))
        }
        assertTrue(error.message!!.contains("count + 1"))
    }

    @Test
    fun `undefined variable falls back to undefined`() {
        assertTrue(evaluator.evaluate("missing === undefined", emptyMap()))
        assertFalse(evaluator.evaluate("missing > 0", emptyMap()))
        assertTrue(evaluator.evaluate("Math.max(1, 2) === 2", emptyMap()))
    }

    @Test
    fun `unsupported variable type is rejected`() {
        assertThrows(ConditionEvaluationException::class.java) {
            evaluator.evaluate("value", mapOf("value" to Any()))
        }
    }

    @Test
    fun `cached expression sees fresh variables`() {
        assertTrue(evaluator.evaluate("count > 1", mapOf("count" to 2)))
        assertFalse(evaluator.evaluate("count > 1", mapOf("count" to 0)))
    }

    @Test
    fun `long and double variables are supported`() {
        assertTrue(evaluator.evaluate("value > 1", mapOf("value" to 3_000_000_000L)))
        assertTrue(evaluator.evaluate("value > 1", mapOf("value" to 1.5)))
    }
}
