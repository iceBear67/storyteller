package io.ib67.mods.storyteller.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.StringReader
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.context.StringRange
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.RootCommandNode
import io.ib67.mods.storyteller.dialog.Dialog
import io.ib67.mods.storyteller.dialog.DialogScript
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import net.minecraft.SharedConstants
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.server.Bootstrap
import net.minecraft.server.permissions.LevelBasedPermissionSet
import net.minecraft.server.permissions.PermissionLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DialogRefArgumentTest {

    private val argument = DialogRefArgument(
        DialogDirector({ error("Player lookup is not used") }) { 20L },
    )

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

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

    @Test
    fun `qualified references are suggested`() {
        val script = DialogScript(
            id = "suggest-script",
            subDialogs = mapOf("branch" to Dialog(actions = emptyList(), id = "branch")),
            defaultDialog = Dialog(actions = emptyList(), id = "suggest-script"),
        )
        DialogScriptRegistry.register(script)
        val source = Commands.createCompilationContext(LevelBasedPermissionSet.forLevel(PermissionLevel.ALL))
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(Commands.literal("goto").then(Commands.argument("ref", argument)))
        val parse = dispatcher.parse("goto suggest-", source)
        val texts = dispatcher.getCompletionSuggestions(parse).join().list.map { it.text }
        assertTrue("suggest-script:suggest-script" in texts)
        assertTrue("suggest-script:branch" in texts)
    }

    @Test
    fun `non command sources get qualified references`() {
        val script = DialogScript(
            id = "client-suggest-script",
            subDialogs = mapOf("branch" to Dialog(actions = emptyList(), id = "branch")),
            defaultDialog = Dialog(actions = emptyList(), id = "client-suggest-script"),
        )
        DialogScriptRegistry.register(script)
        val context = CommandContext<Any>(
            Any(),
            "goto ",
            emptyMap(),
            null,
            RootCommandNode<Any>(),
            emptyList(),
            StringRange.at(0),
            null,
            null,
            false,
        )
        val texts = argument.listSuggestions(context, SuggestionsBuilder("goto ", 5)).join().list.map { it.text }
        assertTrue("client-suggest-script:client-suggest-script" in texts)
        assertTrue("client-suggest-script:branch" in texts)
    }
}
