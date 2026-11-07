package io.ib67.mods.storyteller.dialog

import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

object DialogScriptRegistry : SimplePreparableReloadListener<Map<String, DialogScript>>() {

    private const val DIALOG_DIRECTORY = "dialog"
    private const val DIALOG_EXTENSION = ".txt"

    private val parser = DialogParser()

    @Volatile
    private var scripts: Map<String, DialogScript> = emptyMap()

    @Synchronized
    fun register(script: DialogScript) {
        check(script.id !in scripts) { "重复的 DialogScript ID：${script.id}" }
        scripts = LinkedHashMap(scripts).apply { put(script.id, script) }
    }

    fun get(id: String): DialogScript? = scripts[id]

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Map<String, DialogScript> =
        load(manager)

    @Synchronized
    override fun apply(preparations: Map<String, DialogScript>, manager: ResourceManager, profiler: ProfilerFiller) {
        scripts = preparations
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

    private fun scriptId(identifier: Identifier): String =
        identifier.path
            .removePrefix("$DIALOG_DIRECTORY/")
            .removeSuffix(DIALOG_EXTENSION)
}
