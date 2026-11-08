package io.ib67.mods.storyteller.dialog.runner

import io.ib67.mods.storyteller.dialog.DialogueAction
import io.ib67.mods.storyteller.dialog.TextSegment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionRunnerTimingTest {

    @Test
    fun `dialogue duration grows with text length`() {
        val short = action("hi")
        val long = action("a".repeat(100))
        assertTrue(dialogueDuration(long) > dialogueDuration(short))
    }

    @Test
    fun `dialogue duration is capped`() {
        assertEquals(dialogueDuration(action("a".repeat(1000))), dialogueDuration(action("a".repeat(2000))))
    }

    private fun action(text: String): DialogueAction =
        DialogueAction(
            condition = null,
            speaker = null,
            text = listOf(TextSegment.Literal(text)),
            clickCommand = null,
        )
}
