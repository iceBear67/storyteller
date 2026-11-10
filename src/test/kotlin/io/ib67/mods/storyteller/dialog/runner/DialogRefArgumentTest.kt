package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialogRefArgumentTest {

    private val argument = DialogRefArgument(
        DialogDirector({ error("Player lookup is not used") }) { 20L },
    )

    @Test
    fun `script qualified reference is parsed`() {
        assertEquals("chapter:ending", argument.parse(StringReader("chapter:ending")))
    }

    @Test
    fun `reference with slashes is parsed`() {
        assertEquals("chapter/one", argument.parse(StringReader("chapter/one")))
    }

    @Test
    fun `missing reference is rejected`() {
        assertThrows(CommandSyntaxException::class.java) {
            argument.parse(StringReader(""))
        }
    }
}
