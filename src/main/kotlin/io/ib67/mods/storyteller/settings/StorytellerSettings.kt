package io.ib67.mods.storyteller.settings

import com.google.gson.JsonParser
import io.ib67.mods.storyteller.dialog.ParsedSelector
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

data class StorytellerSettings(
    val defaultAudienceSelector: ParsedSelector,
) {

    companion object {
        val DEFAULT: StorytellerSettings by lazy {
            StorytellerSettings(ParsedSelector.parse("@s"))
        }
    }
}

class StorytellerSettingsRegistry : SimplePreparableReloadListener<StorytellerSettings>() {

    @Volatile
    var current: StorytellerSettings = StorytellerSettings.DEFAULT
        private set

    override fun prepare(manager: ResourceManager, profiler: ProfilerFiller): StorytellerSettings =
        load(manager)

    internal fun load(manager: ResourceManager): StorytellerSettings {
        val resource = manager.getResource(SETTINGS_FILE).orElse(null)
            ?: return StorytellerSettings.DEFAULT
        val json = JsonParser.parseString(resource.readAllAsString()).asJsonObject
        val selector = json.get("defaultAudienceSelector")
            ?: throw IllegalArgumentException("settings.json is missing defaultAudienceSelector")
        return StorytellerSettings(ParsedSelector.parse(selector.asString))
    }

    override fun apply(
        preparations: StorytellerSettings,
        manager: ResourceManager,
        profiler: ProfilerFiller,
    ) {
        current = preparations
    }

    companion object {

        val ID: Identifier = Identifier.fromNamespaceAndPath("storyteller", "settings")

        private val SETTINGS_FILE: Identifier = Identifier.fromNamespaceAndPath("storyteller", "settings.json")
    }
}
