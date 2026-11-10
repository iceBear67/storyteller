package io.ib67.mods.storyteller.dialog

sealed interface Action

data class DividerAction(val name: String) : Action

data class CommandCall(
    val commands: List<String>,
    val selector: String = DEFAULT_SELECTOR,
) {
    companion object {
        const val DEFAULT_SELECTOR: String = "@s"
    }
}

sealed interface CommandInvocation {
    data class Raw(val call: CommandCall) : CommandInvocation
    data class Tokenized(val token: String) : CommandInvocation
}

data class DialogueAction(
    val condition: Condition?,
    val speaker: String?,
    val text: List<TextSegment>,
    val clickCommand: CommandInvocation?,
) : Action {

    override fun toString(): String = buildString {
        condition?.let { append('(').append(it.expression).append(") ") }
        speaker?.let { append(it).append(": ") }
        for (segment in text) {
            when (segment) {
                is TextSegment.Literal -> append(segment.text)
                is TextSegment.Color -> append('&').append(segment.code)
                is TextSegment.InlineCommand -> append('$').append(segment.commands.joinToString("; ")).append('$')
            }
        }
        clickCommand?.let { invocation ->
            append(" [")
            append(
                when (invocation) {
                    is CommandInvocation.Raw -> invocation.call.commands.joinToString("; ") + " " + invocation.call.selector
                    is CommandInvocation.Tokenized -> invocation.token
                },
            )
            append(']')
        }
    }
}

data class CommandAction(
    val invocation: CommandInvocation,
    val condition: Condition? = null,
) : Action

data class WaitAction(
    val condition: Condition?,
    val timeoutTicks: Int?,
) : Action

@JvmInline
value class Condition(val expression: String)

sealed interface TextSegment {
    data class Literal(val text: String) : TextSegment
    data class Color(val code: Char) : TextSegment
    data class InlineCommand(val commands: List<String>) : TextSegment
}
