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

    @Volatile
    private var scripts: Map<String, DialogScript> = emptyMap()

    @Volatile
    private var commandTokens: Map<String, CommandAction> = emptyMap()

    @Volatile
    var reloadId: Long = System.nanoTime()
        private set

    @Synchronized
    fun register(script: DialogScript) {
        check(script.id !in scripts) { "重复的 DialogScript ID：${script.id}" }
        scripts = LinkedHashMap(scripts).apply { put(script.id, script) }
        rebuildCommandTokens()
        reloadId += 1
    }

    fun get(id: String): DialogScript? = scripts[id]

    fun tokenFor(action: CommandAction): String =
        checkNotNull(commandTokens.entries.firstOrNull { it.value == action }?.key) {
            "动作不在 token 表中：$action"
        }

    fun commandFor(token: String): CommandAction? = commandTokens[token]

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Map<String, DialogScript> =
        load(manager)

    @Synchronized
    override fun apply(preparations: Map<String, DialogScript>, manager: ResourceManager, profiler: ProfilerFiller) {
        scripts = preparations
        rebuildCommandTokens()
        reloadId += 1
    }

    internal fun load(manager: ResourceManager): Map<String, DialogScript> {
        val loaded = linkedMapOf<String, DialogScript>()
        val resources = manager.listResources(DIALOG_DIRECTORY) { id -> id.path.endsWith(DIALOG_EXTENSION) }
        for ((identifier, resource) in resources) {
            val id = scriptId(identifier)
            val script = parser.parseDialogs(resource.readAllAsString(), id)
            check(loaded.put(id, script) == null) { "重复的 DialogScript ID：$id（$identifier）" }
        }
        return loaded
    }

    private fun rebuildCommandTokens() {
        val tokens = LinkedHashMap<String, CommandAction>()
        for (script in scripts.values) {
            registerClickCommands(script.defaultDialog, tokens)
            for (dialog in script.subDialogs.values) {
                registerClickCommands(dialog, tokens)
            }
        }
        commandTokens = tokens
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
        throw IllegalStateException("命令 token 冲突：$first 与 $second")
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
