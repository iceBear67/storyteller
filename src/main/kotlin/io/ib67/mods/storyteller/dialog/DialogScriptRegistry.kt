package io.ib67.mods.storyteller.dialog

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

private const val TOKEN_HEX_LENGTH = 32
private val TOKEN_SEPARATOR = byteArrayOf(0)

object DialogScriptRegistry : SimplePreparableReloadListener<Map<String, DialogScript>>() {

    private const val DIALOG_DIRECTORY = "dialog"
    private const val DIALOG_EXTENSION = ".txt"

    private val parser = DialogParser()

    private data class Snapshot(
        val scripts: Map<String, DialogScript>,
        val commandTokens: Map<String, CommandAction>,
        val checksum: String,
    )

    @Volatile
    private var snapshot = Snapshot(emptyMap(), emptyMap(), DialogScriptChecksum.compute(emptyMap()))

    internal val reloadEvent = DialogScriptReloadEvent()

    internal val scriptsChecksum: String
        get() = snapshot.checksum

    fun register(script: DialogScript) {
        val change = synchronized(this) {
            check(script.id !in snapshot.scripts) { "Duplicate DialogScript ID: ${script.id}" }
            replaceSnapshot(LinkedHashMap(snapshot.scripts).apply { put(script.id, script) })
        }
        reloadEvent.fire(change.first, change.second)
    }

    fun get(id: String): DialogScript? = snapshot.scripts[id]

    fun tokenFor(action: CommandAction): String =
        checkNotNull(snapshot.commandTokens.entries.firstOrNull { it.value == action }?.key) {
            "Action is not in the token table: $action"
        }

    fun commandFor(token: String): CommandAction? = snapshot.commandTokens[token]

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Map<String, DialogScript> =
        load(manager)

    override fun apply(preparations: Map<String, DialogScript>, manager: ResourceManager, profiler: ProfilerFiller) {
        val change = synchronized(this) { replaceSnapshot(preparations) }
        reloadEvent.fire(change.first, change.second)
    }

    private fun replaceSnapshot(scripts: Map<String, DialogScript>): Pair<String, String> {
        val previous = snapshot.checksum
        snapshot = Snapshot(scripts, buildCommandTokens(scripts), DialogScriptChecksum.compute(scripts))
        return previous to snapshot.checksum
    }

    internal fun load(manager: ResourceManager): Map<String, DialogScript> {
        val loaded = linkedMapOf<String, DialogScript>()
        val resources = manager.listResources(DIALOG_DIRECTORY) { id -> id.path.endsWith(DIALOG_EXTENSION) }
        for ((identifier, resource) in resources) {
            val id = scriptId(identifier)
            val script = parser.parseDialogs(resource.readAllAsString(), id)
            check(loaded.put(id, script) == null) { "Duplicate DialogScript ID: $id ($identifier)" }
        }
        return loaded
    }

    private fun buildCommandTokens(scripts: Map<String, DialogScript>): Map<String, CommandAction> {
        val tokens = LinkedHashMap<String, CommandAction>()
        for ((_, script) in scripts) {
            registerClickCommands(script.defaultDialog, tokens)
            for (dialog in script.subDialogs.values) {
                registerClickCommands(dialog, tokens)
            }
        }
        return tokens
    }

    private fun registerClickCommands(dialog: Dialog, tokens: MutableMap<String, CommandAction>) {
        for (action in dialog.actions) {
            if (action is DialogueAction) {
                val clickCommand = action.clickCommand ?: continue
                registerToken(clickCommand, tokens)
            }
        }
    }

    private fun registerToken(action: CommandAction, tokens: MutableMap<String, CommandAction>) {
        val first = computeToken("1", action)
        val existing = tokens[first]
        if (existing == null) {
            tokens[first] = action
            return
        }
        if (existing == action) {
            return
        }
        val second = computeToken("2", action)
        val secondExisting = tokens[second]
        if (secondExisting == null) {
            tokens[second] = action
            return
        }
        if (secondExisting == action) {
            return
        }
        throw IllegalStateException("Command token collision: $first and $second")
    }

    private fun computeToken(salt: String, action: CommandAction): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt.toByteArray(StandardCharsets.UTF_8))
        digest.update(TOKEN_SEPARATOR)
        digest.update(action.selector.toByteArray(StandardCharsets.UTF_8))
        digest.update(TOKEN_SEPARATOR)
        for (command in action.commands) {
            digest.update(command.toByteArray(StandardCharsets.UTF_8))
            digest.update(TOKEN_SEPARATOR)
        }
        action.condition?.let { condition ->
            digest.update(condition.expression.toByteArray(StandardCharsets.UTF_8))
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }.take(TOKEN_HEX_LENGTH)
    }

    private fun scriptId(identifier: Identifier): String =
        identifier.path
            .removePrefix("$DIALOG_DIRECTORY/")
            .removeSuffix(DIALOG_EXTENSION)
}
