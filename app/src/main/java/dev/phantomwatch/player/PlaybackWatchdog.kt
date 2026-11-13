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
// a start that never renders shows in seconds; only a mid-play hang can be a slow edge
private const val START_STUCK_MS = 12_000L
// a hang is one bad edge, not a dead provider, and `forgive` never lifts a hang ban: every rung of
// a leg shares one CDN host, so a long ban hides the whole provider long after it recovered
private const val HANG_BAN_MIN = 5L
// the host answered, so this may be a slow edge or a wedged decoder: a short sit-out is enough
private const val HANG_SOFT_MIN = 2L

// a stream that hangs never errors, so the fallback path never runs: ExoPlayer just buffers
// drop the resume, ban the host, then let the caller walk to the next format
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
        // a saved spot past the tail cannot play, but a slow segment looks identical for the first
        // seconds: rewinding on that dumped the viewer at 0:00 and started the load over, so only
        // a resume point at the end of the file is treated as unplayable
        val dur = exo.duration
        if (!started && dur > 0 && exo.currentPosition > dur - TAIL_MS) {
            delay(RESUME_MS)
            if (!stalledNow) return@LaunchedEffect
            AppLog.d("watchdog: resume ${exo.currentPosition / 1000}s past the tail, back to 0")
            exo.seekTo(0)
        }
        delay(if (started) STUCK_MS else START_STUCK_MS)
        if (!stalledNow) return@LaunchedEffect
        val host = DeadHosts.hostOf(live.url)
        val alive = withContext(Dispatchers.IO) { Providers.alive(live.url, live.headers, live.referer) }
        val ban = if (alive) HANG_SOFT_MIN else HANG_BAN_MIN
        AppLog.d("watchdog: $host stuck at ${exo.currentPosition / 1000}s, ban ${ban}m, next source")
        // ban the host, not the rung: the sibling heights hang the same way and each costs a full
        // timeout, which is how a dead 1080p source ends up walking down to 360p
        DeadHosts.markHang(live.url, ban)
        DeadReleases.urls.add(live.url)
        stuckNow()
    }
}
