package dev.phantomwatch

import android.content.Context

// remembers where each title/episode was left off
object Resume {
    private const val PREFS = "phantom_resume"
    private const val MIN_MS = 10_000L
    private const val END_SLACK_MS = 30_000L

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun key(kind: String, id: String, season: String, episode: String) =
        "$kind|$id|$season|$episode"

    // a position at the very end would reopen the episode at STATE_ENDED
    internal fun shouldClear(positionMs: Long, durationMs: Long): Boolean =
        positionMs < MIN_MS || (durationMs > 0 && positionMs >= durationMs - END_SLACK_MS)

    internal fun startAt(positionMs: Long, durationMs: Long): Long =
        if (shouldClear(positionMs, durationMs)) 0L else positionMs

    fun position(ctx: Context, kind: String, id: String, season: String, episode: String): Long =
        prefs(ctx).getLong(key(kind, id, season, episode), 0L)

    fun save(
        ctx: Context,
        kind: String,
        id: String,
        season: String,
        episode: String,
        positionMs: Long,
        durationMs: Long = 0L,
    ) {
        val k = key(kind, id, season, episode)
        if (shouldClear(positionMs, durationMs)) {
            prefs(ctx).edit().remove(k).apply()
            return
        }
        prefs(ctx).edit().putLong(k, positionMs).apply()
    }
}
