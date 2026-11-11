package io.ib67.mods.storyteller.settings

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional
import net.minecraft.SharedConstants
import net.minecraft.network.chat.Component
import net.minecraft.server.Bootstrap
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.MultiPackResourceManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class StorytellerSettingsTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }

    @Test
    fun `settings file provides the default audience selector`() {
        val root = Files.createTempDirectory("storyteller-settings")
        try {
            write(root, "data/storyteller/settings.json", """{"defaultAudienceSelector": "@a"}""")
            withManager(root) { manager ->
                val settings = StorytellerSettingsRegistry().load(manager)
                assertEquals("@a", settings.defaultAudienceSelector.text)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `missing settings file uses the built-in default`() {
        val root = Files.createTempDirectory("storyteller-settings-missing")
        try {
            withManager(root) { manager ->
                val settings = StorytellerSettingsRegistry().load(manager)
                assertEquals(StorytellerSettings.DEFAULT.defaultAudienceSelector, settings.defaultAudienceSelector)
            }
        } finally {
            root.toFile().deleteRecursively()
        }
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
