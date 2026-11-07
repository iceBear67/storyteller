package io.ib67.mods.storyteller.dialog

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import net.minecraft.network.chat.Component
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialogScriptRegistryTest {

    @Test
    fun `load reads nested txt files and derives ids from relative paths`() {
        val root = Files.createTempDirectory("dialog-registry")
        try {
            write(root, "data/test/dialog/root.txt", "B: 根目录")
            write(root, "data/test/dialog/a/b/c/somedialog.txt", "A: 你好\n--- sub\nC: 子对白")
            write(root, "data/test/dialog/ignored.json", "{}")

            withManager(root) { manager ->
                val scripts = DialogScriptRegistry.load(manager)
                assertEquals(setOf("root", "a/b/c/somedialog"), scripts.keys)
                assertEquals("a/b/c/somedialog", scripts.getValue("a/b/c/somedialog").id)
                assertEquals(setOf("sub"), scripts.getValue("a/b/c/somedialog").subDialogs.keys)
                assertEquals(
                    listOf(DialogueAction(null, "B", listOf(TextSegment.Literal("根目录")), null)),
                    scripts.getValue("root").defaultDialog.actions,
                )
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `load rejects duplicate ids from different namespaces`() {
        val root = Files.createTempDirectory("dialog-registry")
        try {
            write(root, "data/first/dialog/same.txt", "A: 一")
            write(root, "data/second/dialog/same.txt", "B: 二")

            withManager(root) { manager ->
                assertThrows(IllegalStateException::class.java) { DialogScriptRegistry.load(manager) }
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `registry returns registered script by id`() {
        val script = script("registry-get")
        DialogScriptRegistry.register(script)
        assertEquals(DialogScriptRegistry.get("registry-get"), script)
        assertNull(DialogScriptRegistry.get("registry-missing"))
    }

    @Test
    fun `registry rejects duplicate id`() {
        val script = script("registry-duplicate")
        DialogScriptRegistry.register(script)
        assertThrows(IllegalStateException::class.java) { DialogScriptRegistry.register(script) }
    }

    private fun script(id: String): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )

    private fun withManager(root: Path, action: (MultiPackResourceManager) -> Unit) {
        val location = PackLocationInfo("test", Component.literal("test"), PackSource.BUILT_IN, Optional.empty())
        MultiPackResourceManager(PackType.SERVER_DATA, listOf(PathPackResources(location, root))).use(action)
    }

    private fun write(root: Path, relativePath: String, content: String) {
        val file = root.resolve(relativePath)
        Files.createDirectories(file.parent)
        Files.writeString(file, content, StandardCharsets.UTF_8)
    }
}
