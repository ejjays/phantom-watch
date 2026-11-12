package dev.phantomwatch.data

import android.content.Context

object Settings {
    private const val PREFS = "phantom_settings"

    const val SUBS_ON = SettingsKeys.SUBS_ON
    const val SUBS_SIZE = SettingsKeys.SUBS_SIZE
    const val SUBS_BG = SettingsKeys.SUBS_BG
    const val SAVE_HISTORY = SettingsKeys.SAVE_HISTORY
    const val CONTINUE_WATCHING = SettingsKeys.CONTINUE_WATCHING
    const val AUTOPLAY = SettingsKeys.AUTOPLAY
    const val SKIP_INTRO = SettingsKeys.SKIP_INTRO
    const val SHARE_USAGE = SettingsKeys.SHARE_USAGE
    const val AUTO_REFRESH = SettingsKeys.AUTO_REFRESH
    const val SEEK_STEP = SettingsKeys.SEEK_STEP
    const val NEXT_COUNT = SettingsKeys.NEXT_COUNT

    const val SIZE_SMALL = 0
    const val SIZE_MEDIUM = 1
    const val SIZE_LARGE = 2

    val STEP_SECONDS = listOf(5, 10, 15, 30)
    val NEXT_SECONDS = listOf(5, 10, 15)

    // the picker stores the row it was given, not the number on it
    fun seekSeconds(index: Int): Int = STEP_SECONDS.getOrElse(index) { 10 }

    fun nextSeconds(index: Int): Int = NEXT_SECONDS.getOrElse(index) { 10 }

    const val BG_OFF = 0
    const val BG_DIM = 1
    const val BG_SOLID = 2

    fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun on(ctx: Context, key: String, fallback: Boolean): Boolean =
        prefs(ctx).getBoolean(key, fallback)

    fun setOn(ctx: Context, key: String, value: Boolean) {
        prefs(ctx).edit().putBoolean(key, value).apply()
    }

    fun choice(ctx: Context, key: String, fallback: Int): Int =
        prefs(ctx).getInt(key, fallback)

    fun setChoice(ctx: Context, key: String, value: Int) {
        prefs(ctx).edit().putInt(key, value).apply()
    }

    // subtitle size is a multiple of the picture height, which is how the player sizes its cues
    internal fun sizeFraction(choice: Int): Float = when (choice) {
        SIZE_SMALL -> 0.042f
        SIZE_LARGE -> 0.068f
        else -> 0.0533f
    }

    internal fun bgAlpha(choice: Int): Float = when (choice) {
        BG_OFF -> 0f
        BG_SOLID -> 0.95f
        else -> 0.80f
    }
}
