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

private const val RESUME_MIN_MS = 60_000L
private const val RESUME_MS = 15_000L
private const val STUCK_MS = 30_000L
// a start that never renders shows in seconds; only a mid-play hang can be a slow edge
private const val START_STUCK_MS = 12_000L
// a hang is usually one bad edge, not a dead provider: keep the ban short so the host can recover
private const val HANG_BAN_H = 2L

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
        if (!started && exo.currentPosition > RESUME_MIN_MS) {
            delay(RESUME_MS)
            if (!stalledNow) return@LaunchedEffect
            AppLog.d("watchdog: resume ${exo.currentPosition / 1000}s never played, back to 0")
            exo.seekTo(0)
        }
        delay(if (started) STUCK_MS else START_STUCK_MS)
        if (!stalledNow) return@LaunchedEffect
        val host = DeadHosts.hostOf(live.url)
        AppLog.d("watchdog: $host stuck at ${exo.currentPosition / 1000}s, next source")
        // a wedged decoder stalls exactly like a dead CDN, so only blame the host if it stops answering;
        // the url stays out of the session list either way, which is per-release and cannot poison a title
        if (!withContext(Dispatchers.IO) { Providers.alive(live.url, live.headers, live.referer) }) {
            DeadHosts.markHang(live.url, HANG_BAN_H)
        }
        DeadReleases.urls.add(live.url)
        stuckNow()
    }
}
