package io.ib67.mods.storyteller.dialog

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.exceptions.CommandSyntaxException
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.commands.arguments.selector.EntitySelector

class ParsedSelector private constructor(
    val text: String,
    val selector: EntitySelector,
) {

    override fun equals(other: Any?): Boolean =
        other is ParsedSelector && other.text == text

    override fun hashCode(): Int = text.hashCode()

    override fun toString(): String = text

    companion object {

        fun parse(text: String): ParsedSelector =
            try {
                ParsedSelector(text, EntityArgument.entities().parse(StringReader(text)))
            } catch (exception: CommandSyntaxException) {
                throw IllegalArgumentException("Invalid selector: $text: ${exception.message}", exception)
            }
    }
}
