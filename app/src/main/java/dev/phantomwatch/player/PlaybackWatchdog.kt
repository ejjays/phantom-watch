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
// how close to the end a saved spot has to be before it counts as unplayable rather than slow
private const val TAIL_MS = 20_000L
private const val STUCK_MS = 30_000L
// a cold 1080p start fetches a manifest, a key and the segment at the resume point before it can
// render: 12s killed sources that came up in 20s, and each ban walked the viewer down a rung
private const val START_STUCK_MS = 25_000L
// a hang is one bad edge, not a dead provider, and a ban outlives the walk to the next source:
// every rung of a leg shares one CDN host, so a long ban hides the provider long after it recovered
private const val HANG_BAN_MIN = 5L

// a saved spot past the tail cannot play, but a slow segment looks identical for the first
// seconds: rewinding on that dumped the viewer at 0:00 and started the load over, so only
// a resume point at the end of the file is treated as unplayable
internal fun resumeIsPastTail(started: Boolean, positionMs: Long, durationMs: Long): Boolean =
    !started && durationMs > 0 && positionMs > durationMs - TAIL_MS

// a stream that hangs never errors, so the fallback path never runs: ExoPlayer just buffers
// drop the resume, drop the url, then let the caller walk to the next format
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
        // a wedged decoder or a slow first segment stalls exactly like a dead CDN, so only blame the
        // host when it stops answering; the url stays out of the session list either way
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
