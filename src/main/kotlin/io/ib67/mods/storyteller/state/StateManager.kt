package io.ib67.mods.storyteller.state

import io.ib67.mods.storyteller.dialog.DialogScriptRegistry
import io.ib67.mods.storyteller.dialog.DialogScriptReloadEvent
import io.ib67.mods.storyteller.util.TypedKeyMap
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.BeforeSave
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStarted
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.nbt.NbtAccounter
import net.minecraft.nbt.NbtIo
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.storage.LevelResource

private const val STATE_DIRECTORY = "storyteller"
private const val PLAYER_DIRECTORY = "players"
private const val GLOBAL_FILE = "global.nbt"

class StateManager(
    serverStarted: Event<ServerStarted>,
    beforeSave: Event<BeforeSave>,
    serverStopping: Event<ServerStopping>,
    playerJoin: Event<ServerPlayConnectionEvents.Join>,
    playerDisconnect: Event<ServerPlayConnectionEvents.Disconnect>,
    dialogScriptReload: DialogScriptReloadEvent,
) {

    private val playerStates = mutableMapOf<UUID, PlayerState>()

    private lateinit var playerDirectory: Path

    private lateinit var globalFile: Path

    var globalState: TypedKeyMap = TypedKeyMap()
        private set

    var checkCommandScope: Boolean = true

    init {
        serverStarted.register(::onServerStarted)
        beforeSave.register { _, _, _ -> saveDirty() }
        serverStopping.register { saveDirty() }
        playerJoin.register { listener, _, _ -> onJoin(listener.player) }
        playerDisconnect.register { listener, _ -> onLeave(listener.player) }
        dialogScriptReload.register { previous, current -> onDialogScriptsReloaded(previous, current) }
    }

    fun playerState(player: ServerPlayer): PlayerState =
        playerStates.getOrPut(player.uuid) { load(playerFile(player.uuid), PlayerState()) }

    private fun onServerStarted(server: MinecraftServer) {
        val root = server.getWorldPath(LevelResource.DATA).resolve(STATE_DIRECTORY)
        playerDirectory = root.resolve(PLAYER_DIRECTORY)
        globalFile = root.resolve(GLOBAL_FILE)
        Files.createDirectories(playerDirectory)
        playerStates.clear()
        globalState = load(globalFile, TypedKeyMap())
    }

    private fun onJoin(player: ServerPlayer) {
        playerState(player).useScriptChecksum(DialogScriptRegistry.scriptsChecksum)
    }

    private fun onLeave(player: ServerPlayer) {
        val state = playerStates.remove(player.uuid) ?: return
        saveIfDirty(playerFile(player.uuid), state)
    }

    private fun onDialogScriptsReloaded(previousChecksum: String, currentChecksum: String) {
        if (previousChecksum == currentChecksum) {
            return
        }
        for (state in playerStates.values) {
            state.useScriptChecksum(currentChecksum)
        }
    }

    private fun saveDirty() {
        if (!::globalFile.isInitialized) {
            return
        }
        saveIfDirty(globalFile, globalState)
        for ((uuid, state) in playerStates) {
            saveIfDirty(playerFile(uuid), state)
        }
    }

    private fun playerFile(uuid: UUID): Path = playerDirectory.resolve("$uuid.nbt")

    private fun <T : TypedKeyMap> load(file: Path, target: T): T {
        if (Files.exists(file)) {
            val tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap())
            TypedKeyMapCodec.decodeInto(tag, target)
        }
        target.markClean()
        return target
    }

    private fun saveIfDirty(file: Path, state: TypedKeyMap) {
        if (!state.dirty) {
            return
        }
        val tag = TypedKeyMapCodec.encode(state)
        Files.createDirectories(file.parent)
        val temporary = file.resolveSibling("${file.fileName}.tmp")
        NbtIo.writeCompressed(tag, temporary)
        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        state.markClean()
    }
}
