package dev.phantomwatch.data

// one list for the store and the settings UI: the two drifted apart once and every toggle
// silently wrote a key nothing read
object SettingsKeys {
    const val SUBS_ON = "subs_on"
    const val SUBS_SIZE = "subs_size"
    const val SUBS_BG = "subs_bg"
    const val SAVE_HISTORY = "save_history"
    const val CONTINUE_WATCHING = "continue_watching"
    const val AUTOPLAY = "autoplay"
    const val SKIP_INTRO = "skip_intro"
    const val SHARE_USAGE = "share_usage"
    const val AUTO_REFRESH = "auto_refresh"

    val ALL = setOf(
        SUBS_ON, SUBS_SIZE, SUBS_BG, SAVE_HISTORY, CONTINUE_WATCHING,
        AUTOPLAY, SKIP_INTRO, SHARE_USAGE, AUTO_REFRESH,
    )
}
