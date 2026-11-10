package io.ib67.mods.storyteller.dialog

import io.ib67.mods.storyteller.dialog.pass.CommandTokenPass
import io.ib67.mods.storyteller.dialog.pass.DialogScriptPass
import io.ib67.mods.storyteller.dialog.pass.WaitActionPass
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

object DialogScriptRegistry : SimplePreparableReloadListener<Map<String, DialogScript>>() {

    private const val DIALOG_DIRECTORY = "dialog"
    private const val DIALOG_EXTENSION = ".txt"

    private val parser = DialogParser()

    private data class Snapshot(
        val rawScripts: Map<String, DialogScript>,
        val scripts: Map<String, DialogScript>,
        val checksum: String,
    )

    @Volatile
    private var snapshot = Snapshot(emptyMap(), emptyMap(), DialogScriptChecksum.compute(emptyMap()))

    internal val reloadEvent = DialogScriptReloadEvent()

    internal val scriptsChecksum: String
        get() = snapshot.checksum

    @Synchronized
    fun register(script: DialogScript) {
        check(script.id !in snapshot.rawScripts) { "Duplicate DialogScript ID: ${script.id}" }
        install(LinkedHashMap(snapshot.rawScripts).apply { put(script.id, script) })
    }

    fun get(id: String): DialogScript? = snapshot.scripts[id]

    internal fun scriptsSnapshot(): Map<String, DialogScript> = snapshot.scripts

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): Map<String, DialogScript> =
        load(manager)

    @Synchronized
    override fun apply(preparations: Map<String, DialogScript>, manager: ResourceManager, profiler: ProfilerFiller) {
        install(preparations)
    }

    private fun install(rawScripts: Map<String, DialogScript>) {
        val checksum = DialogScriptChecksum.compute(rawScripts)
        val builder = CommandTokenRegistry.builder()
        val scripts = applyPasses(rawScripts, builder)
        val previous = snapshot.checksum
        reloadEvent.fire(previous, checksum)
        CommandTokenRegistry.install(builder)
        snapshot = Snapshot(rawScripts, scripts, checksum)
    }

    private fun applyPasses(
        rawScripts: Map<String, DialogScript>,
        builder: CommandTokenRegistry.Builder,
    ): Map<String, DialogScript> {
        val passes: List<DialogScriptPass> = listOf(WaitActionPass(), CommandTokenPass(builder))
            .sortedByDescending { it.priority }
        return rawScripts.mapValues { (_, script) ->
            passes.fold(script) { current, pass -> pass.apply(current) }
        }
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

    private fun scriptId(identifier: Identifier): String =
        identifier.path
            .removePrefix("$DIALOG_DIRECTORY/")
            .removeSuffix(DIALOG_EXTENSION)
}
