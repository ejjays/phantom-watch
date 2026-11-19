package dev.phantomwatch.core

import android.content.Context
import android.widget.Toast
import dev.phantomwatch.BuildConfig
import dev.phantomwatch.R

object UpdateNotice {
    private const val PREFS = "phantom_update"
    private const val KEY = "applied"

    fun show(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = prefs.getInt(KEY, 0)
        val now = BuildConfig.VERSION_CODE
        if (seen != 0 && seen != now) {
            val label = "${BuildConfig.VERSION_NAME} ($now)"
            Toast.makeText(context, context.getString(R.string.update_applied, label), Toast.LENGTH_LONG).show()
        }
        if (seen != now) prefs.edit().putInt(KEY, now).apply()
    }
}
