package io.ib67.mods.storyteller.dialog.runner

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.CommandSyntaxException
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.session.DialogDirector
import java.util.concurrent.CompletableFuture
import net.minecraft.commands.CommandSourceStack

class DialogRefArgument(
    private val director: DialogDirector,
) : ArgumentType<String> {

    override fun parse(reader: StringReader): String {
        val start = reader.cursor
        while (reader.canRead() && !reader.peek().isWhitespace()) {
            reader.skip()
        }
        if (reader.cursor == start) {
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument().createWithContext(reader)
        }
        return reader.string.substring(start, reader.cursor)
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val source = context.source as? CommandSourceStack ?: return builder.buildFuture()
        val prefix = builder.remaining
        val currentScriptId = source.player?.let { director.currentFrame(it)?.scriptId }
        val refs = mutableSetOf<String>()
        for ((scriptId, script) in DialogScriptRegistry.scriptsSnapshot()) {
            if (scriptId == currentScriptId) {
                refs += script.defaultDialog.id
                refs += script.subDialogs.keys
            }
            refs += "$scriptId:${script.defaultDialog.id}"
            for (dialogId in script.subDialogs.keys) {
                refs += "$scriptId:$dialogId"
            }
        }
        for (ref in refs) {
            if (ref.startsWith(prefix)) {
                builder.suggest(ref)
            }
        }
        return builder.buildFuture()
    }

    override fun toString(): String = "dialog_ref"
}
