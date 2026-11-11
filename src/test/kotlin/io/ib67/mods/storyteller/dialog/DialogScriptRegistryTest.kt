package io.ib67.mods.storyteller.dialog

import io.ib67.mods.storyteller.settings.StorytellerSettings
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import net.minecraft.server.packs.resources.PreparableReloadListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class DialogScriptRegistryTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

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
        val call = CommandCall(commands = listOf("say token-get"), selector = "@a")
        DialogScriptRegistry.register(scriptWithClick("token-get", call))
        assertEquals(call, CommandTokenRegistry.commandFor(clickToken("token-get")))
    }

    @Test
    fun `identical click commands share a token`() {
        val call = CommandCall(commands = listOf("say token-shared"), selector = "@a")
        DialogScriptRegistry.register(scriptWithClick("token-shared-a", call))
        DialogScriptRegistry.register(scriptWithClick("token-shared-b", call))
        assertEquals(clickToken("token-shared-a"), clickToken("token-shared-b"))
    }

    @Test
    fun `different click commands get different tokens`() {
        DialogScriptRegistry.register(
            scriptWithClick(
                "token-different-a",
                CommandCall(commands = listOf("say token-different-a"), selector = "@a"),
            ),
        )
        DialogScriptRegistry.register(
            scriptWithClick(
                "token-different-b",
                CommandCall(commands = listOf("say token-different-b"), selector = "@a"),
            ),
        )
        assertNotEquals(clickToken("token-different-a"), clickToken("token-different-b"))
    }

    @Test
    fun `register parses action selectors`() {
        DialogScriptRegistry.register(
            DialogScript(
                id = "parsed-selectors",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(
                    actions = listOf(
                        CommandAction(
                            CommandInvocation.Raw(CommandCall(commands = listOf("say hi"), selector = "@a")),
                        ),
                    ),
                    id = "parsed-selectors",
                ),
            ),
        )
        val action = DialogScriptRegistry.get("parsed-selectors")!!.defaultDialog.actions.single() as CommandAction
        assertEquals("@a", action.parsedSelector.text)
    }

    @Test
    fun `actions without audience use the settings default`() {
        DialogScriptRegistry.useSettings { StorytellerSettings(ParsedSelector.parse("@a")) }
        val root = Files.createTempDirectory("dialog-settings-default")
        try {
            write(root, "data/test/dialog/settings-default.txt", "A: hi")
            withManager(root) { manager ->
                val script = DialogScriptRegistry.load(manager).getValue("settings-default")
                val action = script.defaultDialog.actions.single() as DialogueAction
                assertEquals("@a", action.audienceSelector.text)
            }
        } finally {
            DialogScriptRegistry.useSettings { StorytellerSettings.DEFAULT }
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `register rejects invalid selectors`() {
        assertThrows(IllegalArgumentException::class.java) {
            DialogScriptRegistry.register(
                DialogScript(
                    id = "invalid-selector",
                    subDialogs = emptyMap(),
                    defaultDialog = Dialog(
                        actions = listOf(
                            DialogueAction(
                                condition = null,
                                speaker = "A",
                                text = listOf(TextSegment.Literal("hi")),
                                clickCommand = null,
                                audience = "@a[",
                            ),
                        ),
                        id = "invalid-selector",
                    ),
                ),
            )
        }
    }

    @Test
    fun `register applies the pass pipeline`() {
        DialogScriptRegistry.register(
            DialogScript(
                id = "pass-pipeline",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(
                    actions = listOf(
                        CommandAction(CommandInvocation.Raw(CommandCall(commands = listOf("wait 20")))),
                    ),
                    id = "pass-pipeline",
                ),
            ),
        )
        assertEquals(
            listOf(
                WaitAction(
                    condition = null,
                    timeoutTicks = 20,
                    audience = "@s",
                    audienceSelector = ParsedSelector.parse("@s"),
                ),
            ),
            DialogScriptRegistry.get("pass-pipeline")!!.defaultDialog.actions,
        )
    }

    @Test
    fun `standalone command actions are tokenized`() {
        val call = CommandCall(commands = listOf("say token-standalone"), selector = "@s")
        DialogScriptRegistry.register(
            DialogScript(
                id = "token-standalone",
                subDialogs = emptyMap(),
                defaultDialog = Dialog(
                    actions = listOf(CommandAction(CommandInvocation.Raw(call))),
                    id = "token-standalone",
                ),
            ),
        )
        val action = DialogScriptRegistry.get("token-standalone")!!.defaultDialog.actions.single() as CommandAction
        val invocation = action.invocation as CommandInvocation.Tokenized
        assertEquals(call, CommandTokenRegistry.commandFor(invocation.token))
    }

    @Test
    fun `snapshot exposes registered scripts`() {
        val script = script("snapshot-script")
        DialogScriptRegistry.register(script)
        assertEquals(script, DialogScriptRegistry.scriptsSnapshot()["snapshot-script"])
    }

    @Test
    fun `prepare and apply install scripts and fire the reload event`() {
        val root = Files.createTempDirectory("dialog-registry-apply")
        try {
            write(root, "data/test/dialog/apply-test.txt", "A: Hello\n--- sub\nB: Child [say hi]")
            withManager(root) { manager ->
                var previous: String? = null
                var current: String? = null
                DialogScriptRegistry.reloadEvent.register { previousChecksum, currentChecksum ->
                    previous = previousChecksum
                    current = currentChecksum
                }
                val before = DialogScriptRegistry.scriptsChecksum
                val executor = Executor { it.run() }
                val barrier = object : PreparableReloadListener.PreparationBarrier {
                    override fun <T : Any> wait(value: T): CompletableFuture<T> = CompletableFuture.completedFuture(value)
                }
                DialogScriptRegistry.reload(
                    PreparableReloadListener.SharedState(manager),
                    executor,
                    barrier,
                    executor,
                ).join()
                assertEquals(setOf("apply-test"), DialogScriptRegistry.scriptsSnapshot().keys)
                assertEquals("apply-test", DialogScriptRegistry.get("apply-test")?.id)
                assertEquals(before, previous)
                assertNotEquals(previous, current)
                assertEquals(current, DialogScriptRegistry.scriptsChecksum)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun script(id: String): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(actions = emptyList(), id = id),
        )

    private fun scriptWithClick(id: String, call: CommandCall): DialogScript =
        DialogScript(
            id = id,
            subDialogs = emptyMap(),
            defaultDialog = Dialog(
                actions = listOf(
                    DialogueAction(
                        condition = null,
                        speaker = "A",
                        text = listOf(TextSegment.Literal("click me")),
                        clickCommand = CommandInvocation.Raw(call),
                    ),
                ),
                id = id,
            ),
        )

    private fun clickToken(scriptId: String): String {
        val action = DialogScriptRegistry.get(scriptId)!!.defaultDialog.actions.single() as DialogueAction
        return (action.clickCommand as CommandInvocation.Tokenized).token
    }

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
