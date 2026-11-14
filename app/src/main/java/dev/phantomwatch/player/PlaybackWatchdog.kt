package dev.phantomwatch.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.media3.exoplayer.ExoPlayer
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.provider.DeadHosts
import dev.phantomwatch.provider.DeadReleases
import dev.phantomwatch.provider.Providers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private const val RESUME_MS = 15_000L
// how close to end a saved spot counts unplayable not slow
private const val TAIL_MS = 20_000L
private const val STUCK_MS = 30_000L
// 25s, cold start fetches manifest and segment at resume point before render
private const val START_STUCK_MS = 25_000L
// every rung of a leg shares one cdn host so a long ban hides recovered provider
private const val HANG_BAN_MIN = 5L

// saved spot past tail cannot play but a slow segment looks the same for the first
// seconds, rewinding dumped viewer at 0:00 and started over so only
// resume point at end of file is unplayable
internal fun resumeIsPastTail(started: Boolean, positionMs: Long, durationMs: Long): Boolean =
    !started && durationMs > 0 && positionMs > durationMs - TAIL_MS

// a hang never errors so the fallback never runs, exoplayer just buffers
// drop resume and url, walk to next format
@Composable
internal fun PlaybackWatchdog(
    exo: ExoPlayer,
    stalled: Boolean,
    started: Boolean,
    live: Stream,
    onStuck: suspend () -> Unit,
) {
    val stalledNow by rememberUpdatedState(stalled)
    val stuckNow by rememberUpdatedState(onStuck)
    LaunchedEffect(live.url, stalled, started) {
        if (!stalled) return@LaunchedEffect
        val dur = exo.duration
        if (resumeIsPastTail(started, exo.currentPosition, dur)) {
            delay(RESUME_MS)
            if (!stalledNow) return@LaunchedEffect
            AppLog.d("watchdog: resume ${exo.currentPosition / 1000}s past the tail, back to 0")
            exo.seekTo(0)
        }
        delay(if (started) STUCK_MS else START_STUCK_MS)
        if (!stalledNow) return@LaunchedEffect
        val host = DeadHosts.hostOf(live.url)
        // wedged decoder stalls like dead cdn, ban only when probe fails, url is
        // dropped either way
        val alive = withContext(Dispatchers.IO) { Providers.alive(live.url, live.headers, live.referer) }
        val banned = if (alive) false else DeadHosts.markHang(live.url, HANG_BAN_MIN)
        val why = when {
            alive -> "host answers"
            banned -> "ban ${HANG_BAN_MIN}m"
            else -> "kept, no ban"
        }
        AppLog.d("watchdog: $host stuck at ${exo.currentPosition / 1000}s, $why, next source")
        DeadReleases.urls.add(live.url)
        stuckNow()
    }
}
