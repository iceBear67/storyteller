package io.ib67.mods.storyteller.dialog

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

internal object DialogScriptChecksum {

    fun compute(scripts: Map<String, DialogScript>): String {
        val buffer = ByteArrayOutputStream()
        buffer.writeString("storyteller:dialog-scripts:3")
        for ((_, script) in scripts.entries.sortedBy { it.key }) {
            buffer.writeScript(script)
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(buffer.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun ByteArrayOutputStream.writeScript(script: DialogScript) {
        writeString(script.id)
        writeDialog(script.defaultDialog)
        val subDialogs = script.subDialogs.entries.sortedBy { it.key }
        writeInt(subDialogs.size)
        for ((_, dialog) in subDialogs) {
            writeDialog(dialog)
        }
    }

    private fun ByteArrayOutputStream.writeDialog(dialog: Dialog) {
        writeString(dialog.id)
        writeInt(dialog.actions.size)
        for (action in dialog.actions) {
            writeAction(action)
        }
    }

    private fun ByteArrayOutputStream.writeAction(action: Action) {
        when (action) {
            is DividerAction -> {
                writeString("divider")
                writeString(action.name)
            }
            is DialogueAction -> {
                writeString("dialogue")
                writeCondition(action.condition)
                writeString(action.speaker)
                writeInt(action.text.size)
                for (segment in action.text) {
                    writeTextSegment(segment)
                }
                writeInvocation(action.clickCommand)
                writeString(action.audience)
            }
            is CommandAction -> {
                writeString("command")
                writeInvocation(action.invocation)
                writeCondition(action.condition)
            }
            is WaitAction -> {
                writeString("wait")
                writeCondition(action.condition)
                writeInt(action.timeoutTicks ?: -1)
                writeString(action.audience)
            }
        }
    }

    private fun ByteArrayOutputStream.writeInvocation(invocation: CommandInvocation?) {
        when (invocation) {
            null -> writeInt(0)
            is CommandInvocation.Raw -> {
                writeInt(1)
                writeString(invocation.call.selector)
                writeInt(invocation.call.commands.size)
                for (command in invocation.call.commands) {
                    writeString(command)
                }
            }
            is CommandInvocation.Tokenized -> {
                writeInt(2)
                writeString(invocation.token)
            }
        }
    }

    private fun ByteArrayOutputStream.writeTextSegment(segment: TextSegment) {
        when (segment) {
            is TextSegment.Literal -> {
                writeString("literal")
                writeString(segment.text)
            }
            is TextSegment.Color -> {
                writeString("color")
                writeInt(segment.code.code)
            }
            is TextSegment.InlineCommand -> {
                writeString("inline")
                writeInt(segment.commands.size)
                for (command in segment.commands) {
                    writeString(command)
                }
            }
        }
    }

    private fun ByteArrayOutputStream.writeCondition(condition: Condition?) {
        writeString(condition?.expression)
    }

    private fun ByteArrayOutputStream.writeString(value: String?) {
        if (value == null) {
            writeInt(-1)
            return
        }
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) {
        write(value ushr 24)
        write(value ushr 16)
        write(value ushr 8)
        write(value)
    }
}
