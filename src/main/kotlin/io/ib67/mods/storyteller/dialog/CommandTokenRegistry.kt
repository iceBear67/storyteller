package io.ib67.mods.storyteller.dialog

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object CommandTokenRegistry {

    private data class Snapshot(val byToken: Map<String, CommandCall>)

    @Volatile
    private var snapshot = Snapshot(emptyMap())

    init {
        DialogScriptRegistry.reloadEvent.register { _, _ ->
            snapshot = Snapshot(emptyMap())
        }
    }

    fun commandFor(token: String): CommandCall? = snapshot.byToken[token]

    internal fun builder(): Builder = Builder()

    internal fun install(builder: Builder) {
        snapshot = Snapshot(builder.build())
    }

    class Builder internal constructor() {

        private val byToken = LinkedHashMap<String, CommandCall>()
        private val byCall = LinkedHashMap<CommandCall, String>()

        fun register(call: CommandCall): String {
            byCall[call]?.let { return it }
            val token = computeToken(call)
            val existing = byToken[token]
            if (existing != null && existing != call) {
                throw IllegalStateException("Command token collision: $token")
            }
            byToken[token] = call
            byCall[call] = token
            return token
        }

        internal fun commandFor(token: String): CommandCall? = byToken[token]

        internal fun build(): Map<String, CommandCall> = byToken.toMap()

        private fun computeToken(call: CommandCall): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(call.selector.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            for (command in call.commands) {
                digest.update(command.toByteArray(StandardCharsets.UTF_8))
                digest.update(0.toByte())
            }
            return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(32)
        }
    }
}
