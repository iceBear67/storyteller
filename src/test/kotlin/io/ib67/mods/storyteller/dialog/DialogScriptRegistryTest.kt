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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class DialogScriptRegistryTest {

    @Test
    fun `load reads nested txt files and derives ids from relative paths`() {
        val root = Files.createTempDirectory("dialog-registry")
        try {
            write(root, "data/test/dialog/root.txt", "B: Root")
            write(root, "data/test/dialog/a/b/c/somedialog.txt", "A: Hello\n--- sub\nC: Child dialog")
            write(root, "data/test/dialog/ignored.json", "{}")

            withManager(root) { manager ->
                val scripts = DialogScriptRegistry.load(manager)
                assertEquals(setOf("root", "a/b/c/somedialog"), scripts.keys)
                assertEquals("a/b/c/somedialog", scripts.getValue("a/b/c/somedialog").id)
                assertEquals(setOf("sub"), scripts.getValue("a/b/c/somedialog").subDialogs.keys)
                assertEquals(
                    listOf(DialogueAction(null, "B", listOf(TextSegment.Literal("Root")), null)),
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
            write(root, "data/first/dialog/same.txt", "A: One")
            write(root, "data/second/dialog/same.txt", "B: Two")

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

    @Test
    fun `register changes the scripts checksum`() {
        val before = DialogScriptRegistry.scriptsChecksum
        DialogScriptRegistry.register(script("checksum-register"))
        assertNotEquals(before, DialogScriptRegistry.scriptsChecksum)
    }

    @Test
    fun `click command actions get tokens`() {
        val click = CommandAction(commands = listOf("say token-get"), selector = "@a")
        DialogScriptRegistry.register(scriptWithClick("token-get", click))
        assertEquals(click, DialogScriptRegistry.commandFor(DialogScriptRegistry.tokenFor(click)))
    }

    @Test
    fun `identical click commands share a token`() {
        val first = CommandAction(commands = listOf("say token-shared"), selector = "@a")
        val second = CommandAction(commands = listOf("say token-shared"), selector = "@a")
        DialogScriptRegistry.register(scriptWithClick("token-shared-a", first))
        DialogScriptRegistry.register(scriptWithClick("token-shared-b", second))
        assertEquals(DialogScriptRegistry.tokenFor(first), DialogScriptRegistry.tokenFor(second))
    }

    @Test
    fun `different click commands get different tokens`() {
        val first = CommandAction(commands = listOf("say token-different-a"), selector = "@a")
        val second = CommandAction(commands = listOf("say token-different-b"), selector = "@a")
        DialogScriptRegistry.register(scriptWithClick("token-different-a", first))
        DialogScriptRegistry.register(scriptWithClick("token-different-b", second))
        assertNotEquals(DialogScriptRegistry.tokenFor(first), DialogScriptRegistry.tokenFor(second))
    }

    @Test
    fun `standalone command actions are not tokenized`() {
        val command = CommandAction(commands = listOf("say token-standalone"), selector = "@s")
        DialogScriptRegistry.register(
            DialogScript(
                id = "token-standalone",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(actions = listOf(command), id = "token-standalone"),
            ),
        )
        assertThrows(IllegalStateException::class.java) { DialogScriptRegistry.tokenFor(command) }
    }

    private fun script(id: String): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )

    private fun scriptWithClick(id: String, click: CommandAction): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(
                actions = listOf(
                    DialogueAction(
                        condition = null,
                        speaker = "A",
                        text = listOf(TextSegment.Literal("click me")),
                        clickCommand = click,
                    ),
                ),
                id = id,
            ),
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
