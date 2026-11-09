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
) : Action

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
