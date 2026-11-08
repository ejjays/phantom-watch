package dev.phantomwatch.data

import android.content.Context

object Settings {
    private const val PREFS = "phantom_settings"

    const val SUBS_ON = "subs_on"
    const val SUBS_SIZE = "subs_size"
    const val SUBS_BG = "subs_bg"
    const val SUBS_AI = "subs_ai"
    const val SAVE_HISTORY = "save_history"
    const val CONTINUE_WATCHING = "continue_watching"
    const val AUTOPLAY = "autoplay"
    const val SKIP_INTRO = "skip_intro"
    const val SHARE_USAGE = "share_usage"

    const val SIZE_SMALL = 0
    const val SIZE_MEDIUM = 1
    const val SIZE_LARGE = 2

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
