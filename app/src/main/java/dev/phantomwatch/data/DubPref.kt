package dev.phantomwatch.data

import android.content.Context

// remembers the audio cut a viewer picked, per title. Only the label is stored: every resolve
// rebuilds the cuts, so the url always comes from the fresh list and can never go stale here
object DubPref {
    private const val PREFS = "phantom_dub"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun get(ctx: Context, kind: String, id: String): String? =
        prefs(ctx).getString("$kind|$id", null)?.ifBlank { null }

    fun set(ctx: Context, kind: String, id: String, label: String?) {
        val e = prefs(ctx).edit()
        if (label == null) e.remove("$kind|$id") else e.putString("$kind|$id", label)
        e.apply()
    }
}
