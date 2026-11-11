package io.ib67.mods.storyteller.dialog

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID

object CommandTokenRegistry {

    private data class Snapshot(
        val byToken: Map<String, CommandCall>,
        val repeatableTokens: Set<String>,
    )

    @Volatile
    private var snapshot = Snapshot(emptyMap(), emptySet())

    private val consumed = mutableSetOf<Pair<UUID, String>>()

    private val allowed = mutableMapOf<UUID, MutableSet<String>>()

    internal fun bind(reloadEvent: DialogScriptReloadEvent) {
        reloadEvent.register { previous, current -> onReload(previous != current) }
    }

    @Synchronized
    private fun onReload(clearAllowed: Boolean) {
        snapshot = Snapshot(emptyMap(), emptySet())
        consumed.clear()
        if (clearAllowed) {
            allowed.clear()
        }
    }

    fun commandFor(token: String): CommandCall? = snapshot.byToken[token]

    @Synchronized
    fun isAllowed(playerId: UUID, token: String): Boolean = token in allowed[playerId].orEmpty()

    @Synchronized
    fun replaceAllowed(playerId: UUID, tokens: Collection<String>) {
        if (tokens.isEmpty()) {
            allowed.remove(playerId)
        } else {
            allowed[playerId] = tokens.toMutableSet()
        }
    }

    @Synchronized
    fun allow(playerId: UUID, token: String) {
        allowed.getOrPut(playerId) { mutableSetOf() } += token
    }

    @Synchronized
    fun clearAllowed(playerId: UUID) {
        allowed.remove(playerId)
    }

    @Synchronized
    fun consume(playerId: UUID, token: String): Boolean {
        val current = snapshot
        if (token !in current.byToken) {
            return false
        }
        if (token in current.repeatableTokens) {
            return true
        }
        return consumed.add(playerId to token)
    }

    internal fun builder(): Builder = Builder()

    @Synchronized
    internal fun install(builder: Builder) {
        val byToken = builder.build()
        consumed.retainAll { (_, token) -> token in byToken }
        for (tokens in allowed.values) {
            tokens.retainAll(byToken.keys)
        }
        allowed.values.removeIf { it.isEmpty() }
        snapshot = Snapshot(byToken, builder.repeatableTokens())
    }

    class Builder internal constructor() {

        private val byToken = LinkedHashMap<String, CommandCall>()
        private val byCall = LinkedHashMap<Pair<CommandCall, Boolean>, String>()
        private val reusableTokens = mutableSetOf<String>()

        fun register(call: CommandCall, repeatable: Boolean = false): String {
            val key = call to repeatable
            byCall[key]?.let { return it }
            val token = computeToken(call, repeatable)
            val existing = byToken[token]
            if (existing != null && existing != call) {
                throw IllegalStateException("Command token collision: $token")
            }
            byToken[token] = call
            byCall[key] = token
            if (repeatable) {
                reusableTokens += token
            }
            return token
        }

        internal fun commandFor(token: String): CommandCall? = byToken[token]

        internal fun build(): Map<String, CommandCall> = byToken.toMap()

        internal fun repeatableTokens(): Set<String> = reusableTokens.toSet()

        private fun computeToken(call: CommandCall, repeatable: Boolean): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(if (repeatable) 1 else 0)
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
