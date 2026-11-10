package dev.phantomwatch.ui.settings

import dev.phantomwatch.data.SettingsKeys
import dev.phantomwatch.res.Res
import dev.phantomwatch.res.*
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

sealed interface SettingItem {
    val title: StringResource
    val note: StringResource?

    data class Toggle(
        val key: String,
        override val title: StringResource,
        override val note: StringResource? = null,
        val default: Boolean = true,
    ) : SettingItem

    data class Choice(
        val key: String,
        override val title: StringResource,
        override val note: StringResource? = null,
        val options: List<StringResource>,
        val default: Int = 0,
    ) : SettingItem

    data class Value(
        override val title: StringResource,
        override val note: StringResource? = null,
        val value: String,
    ) : SettingItem

    data class Action(
        val action: SettingsAction,
        override val title: StringResource,
        override val note: StringResource? = null,
        val destructive: Boolean = false,
    ) : SettingItem
}

enum class SettingsAction { ClearHistory, Updates, Licences, Privacy, Feedback }

data class SettingCategory(
    val id: String,
    val icon: DrawableResource,
    val title: StringResource,
    val blurb: StringResource,
    val items: List<SettingItem>,
)

fun settingsCatalog(providerCount: Int, version: String): List<SettingCategory> = listOf(
    SettingCategory(
        id = "playback",
        icon = Res.drawable.ic_cat_playback,
        title = Res.string.you_section_playback,
        blurb = Res.string.you_section_playback_blurb,
        items = listOf(
            SettingItem.Toggle(
                SettingsKeys.AUTOPLAY,
                Res.string.you_playback_autoplay,
                Res.string.you_playback_autoplay_note,
            ),
            SettingItem.Toggle(
                SettingsKeys.SKIP_INTRO,
                Res.string.you_playback_skip,
                Res.string.you_playback_skip_note,
            ),
        ),
    ),
    SettingCategory(
        id = "subtitles",
        icon = Res.drawable.ic_cat_subtitles,
        title = Res.string.you_section_subtitles,
        blurb = Res.string.you_section_subtitles_blurb,
        items = listOf(
            SettingItem.Toggle(
                SettingsKeys.SUBS_ON,
                Res.string.you_subs_on,
                Res.string.you_subs_on_note,
            ),
            SettingItem.Choice(
                SettingsKeys.SUBS_SIZE,
                Res.string.you_subs_size,
                options = listOf(
                    Res.string.you_subs_size_small,
                    Res.string.you_subs_size_medium,
                    Res.string.you_subs_size_large,
                ),
                default = 1,
            ),
            SettingItem.Choice(
                SettingsKeys.SUBS_BG,
                Res.string.you_subs_background,
                Res.string.you_subs_bg_note,
                options = listOf(
                    Res.string.you_subs_bg_off,
                    Res.string.you_subs_bg_dim,
                    Res.string.you_subs_bg_solid,
                ),
                default = 1,
            ),
        ),
    ),
    SettingCategory(
        id = "library",
        icon = Res.drawable.ic_cat_library,
        title = Res.string.you_section_library,
        blurb = Res.string.you_section_library_blurb,
        items = listOf(
            SettingItem.Toggle(
                SettingsKeys.CONTINUE_WATCHING,
                Res.string.you_library_continue,
                Res.string.you_library_continue_note,
            ),
            SettingItem.Toggle(
                SettingsKeys.SAVE_HISTORY,
                Res.string.you_library_history,
                Res.string.you_library_history_note,
            ),
            SettingItem.Action(
                SettingsAction.ClearHistory,
                Res.string.you_library_clear,
                Res.string.you_library_clear_note,
                destructive = true,
            ),
        ),
    ),
    SettingCategory(
        id = "providers",
        icon = Res.drawable.ic_cat_providers,
        title = Res.string.you_section_providers,
        blurb = Res.string.you_section_providers_blurb,
        items = listOf(
            SettingItem.Value(
                Res.string.you_providers_configured,
                value = providerCount.toString(),
            ),
            SettingItem.Toggle(
                SettingsKeys.AUTO_REFRESH,
                Res.string.you_providers_refresh,
                Res.string.you_providers_refresh_note,
            ),
            SettingItem.Toggle(
                SettingsKeys.SHARE_USAGE,
                Res.string.you_providers_share,
                Res.string.you_providers_share_note,
            ),
        ),
    ),
    SettingCategory(
        id = "about",
        icon = Res.drawable.ic_cat_about,
        title = Res.string.you_section_about,
        blurb = Res.string.you_section_about_blurb,
        items = listOf(
            SettingItem.Value(Res.string.you_about_version, value = version),
            SettingItem.Action(
                SettingsAction.Updates,
                Res.string.you_about_updates,
                Res.string.you_about_updates_note,
            ),
            SettingItem.Action(
                SettingsAction.Licences,
                Res.string.you_about_licences,
                Res.string.you_about_licences_note,
            ),
            SettingItem.Action(
                SettingsAction.Privacy,
                Res.string.you_about_privacy,
                Res.string.you_about_privacy_note,
            ),
            SettingItem.Action(
                SettingsAction.Feedback,
                Res.string.you_about_feedback,
                Res.string.you_about_feedback_note,
            ),
        ),
    ),
)
