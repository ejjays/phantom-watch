package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.RemoteConfig
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import dev.phantomwatch.data.Tmdb
import dev.phantomwatch.provider.Providers
import dev.phantomwatch.provider.VideoFormat
import dev.phantomwatch.torrent.Seedbox
import dev.phantomwatch.torrent.TorrentRelease
import dev.phantomwatch.torrent.Torrents
import dev.phantomwatch.torrent.VIDEO_EXTS
import dev.phantomwatch.torrent.TORRENTS_ENABLED
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun SourcesScreen(
    title: Title,
    season: String,
    episode: String,
    autoplay: Boolean,
    onPickTorrent: (TorrentRelease) -> Unit,
    onStreamReady: (Stream, TorrentRelease?) -> Unit,
    onBack: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<TorrentRelease>?>(null) }
    var formats by remember { mutableStateOf<List<VideoFormat>>(emptyList()) }
    var formatSubs by remember { mutableStateOf<List<Sub>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var imdbState by remember { mutableStateOf<String?>(null) }
    var gone by remember { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LaunchedEffect(title.id, season, episode) {
        try {
            status = "Trying direct streams…"
            val directJob = async(Dispatchers.IO) {
                Providers.directAll(title.id, title.kind, season, episode, title.title)
            }
            val imdbJob = async(Dispatchers.IO) {
                Tmdb.imdbOf(title.id, title.kind, RemoteConfig.tmdbKey())?.removePrefix("tt")
            }
            val all = directJob.await()
            val imdbDigits = imdbJob.await()
            imdbState = imdbDigits
            AppLog.d("imdb ${imdbDigits ?: "none"}")
            val live = all.first.filter { it.url !in Torrents.DeadReleases.urls }
            AppLog.d("direct ${live.size} formats subs=${all.second.size}")
            if (live.isNotEmpty()) {
                formats = live
                formatSubs = all.second
                if (autoplay) {
                    val best = live.first()
                    AppLog.d(
                        "direct play provider=${Providers.providerName(best)} label=${best.label} " +
                            "height=${best.height} hls=${best.isHls} enSubs=${best.enSubs} " +
                            "enAudio=${best.enAudio} streamSubs=${Providers.subsFor(best, all.second).size}",
                    )
                    gone = true
                    onStreamReady(
                        Stream(
                            url = best.url, isHls = best.isHls,
                            subs = Providers.subsFor(best, all.second),
                            referer = best.referer, sampleMp4 = null,
                            imdb = imdbDigits,
                        ),
                        null,
                    )
                    return@LaunchedEffect
                }
            } else if (!TORRENTS_ENABLED) {
                err = "No direct streams found"
                return@LaunchedEffect
            }
            val key = RemoteConfig.tmdbKey()
            val imdb = withContext(Dispatchers.IO) { Tmdb.imdbOf(title.id, title.kind, key) }
            if (imdb == null) {
                err = "no imdb id"
                return@LaunchedEffect
            }
            val found = withContext(Dispatchers.IO) {
                Torrents.query(imdb, title.kind, season, episode)
            }
            if (found.isEmpty()) {
                err = "no torrents"
            } else {
                entries = found
                val pick = Torrents.best(found)
                val seed = Seedbox.cfg()
                if (autoplay && seed != null && pick != null) {
                    status = "Sending to seedbox…"
                    AppLog.d("seed pick ${pick.name.take(60)} ${pick.size} seeds=${pick.seeds}")
                    val sentAt = System.currentTimeMillis()
                    val sent = withContext(Dispatchers.IO) {
                        Seedbox.add(seed, Torrents.magnet(pick), pick.infoHash)
                    }
                    if (!sent) {
                        status = null
                        return@LaunchedEffect
                    }
                    AppLog.d("seed sent +${System.currentTimeMillis() - sentAt}ms")
                    var metaLogged = false
                    var lastProg = 0.0
                    var lastChange = System.currentTimeMillis()
                    val t0 = System.currentTimeMillis()
                    while (System.currentTimeMillis() - t0 < 25 * 60 * 1000 &&
                        System.currentTimeMillis() - lastChange < 3 * 60 * 1000
                    ) {
                        val fs = withContext(Dispatchers.IO) { Seedbox.files(seed, pick.infoHash) }
                        if (fs.isNotEmpty() && !metaLogged) {
                            metaLogged = true
                            AppLog.d("seed meta +${System.currentTimeMillis() - sentAt}ms files=${fs.size}")
                        }
                        val st = withContext(Dispatchers.IO) { Seedbox.stat(seed, pick.infoHash) }
                        val vid = fs.filter {
                            it.path.substringAfterLast('.', "").lowercase() in VIDEO_EXTS
                        }.maxByOrNull { it.size }
                        if (vid != null && vid.size > 0) {
                            if (vid.progress > lastProg) {
                                lastProg = vid.progress
                                lastChange = System.currentTimeMillis()
                            }
                            status = buildString {
                                append("Cloud fetching ${(100 * vid.progress).toInt()}%")
                                val spd = (st?.speed ?: 0L) / 1048576.0
                                if (spd > 0.05) append(" · ${"%.1f".format(spd)}MB/s")
                                val eta = st?.eta ?: -1L
                                if (eta > 0) append(if (eta >= 3600) " · eta ${eta / 3600}h${(eta % 3600) / 60}m" else " · eta ${eta / 60}m${eta % 60}s")
                                append("…")
                            }
                            if (vid.progress >= 0.999) {
                                AppLog.d("seed play ${vid.path.take(60)} ${(vid.size / 1048576)}MB at ${(100 * vid.progress).toInt()}%")
                                onStreamReady(
                                    Stream(
                                        url = Seedbox.fileUrl(seed, pick.infoHash, vid.path),
                                        isHls = false, subs = emptyList(), referer = seed.base,
                                        sampleMp4 = null, auth = Seedbox.authHeader(seed),
                                    ),
                                    pick,
                                )
                                return@LaunchedEffect
                            }
                        } else {
                            status = "Waiting for metadata…"
                        }
                        delay(2000)
                    }
                    status = null
                } else if (autoplay) {
                    pick?.let {
                        AppLog.d("autoplay ${it.name.take(60)} seeds=${it.seeds}")
                        onPickTorrent(it)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
    }
    if ((autoplay && loading || gone) && err == null) {
        val cfg = LocalConfiguration.current
        val stageH = (minOf(cfg.screenWidthDp, cfg.screenHeightDp).toFloat() * 9 / 16).dp
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.fillMaxWidth().statusBarsPadding().height(stageH)) {
                PlayerStage(
                    title = if (season.isNotEmpty() && episode.isNotEmpty()) "${title.title} · S$season E$episode" else title.title,
                    fullscreen = false, isPlaying = false, stalled = true,
                    positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                    flash = null, seekMark = null, seekInset = 0.dp, locked = false,
                    subsOn = false, canSubs = true,
                    onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                    onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                    onSwipe = { _, _ -> }, onSwipeEnd = {},
                    video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
                )
            }
            PlayerPanel(media = title, season = season, episode = episode)
        }
        return
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(title.title, Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.titleMedium)
        }
        when {
            loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                Text(status ?: "Finding best release…", style = MaterialTheme.typography.bodySmall)
            }
            err != null && entries == null && formats.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err ?: "No streams found")
            }
            !TORRENTS_ENABLED -> LazyColumn(Modifier.fillMaxSize()) {
                items(formats, key = { it.url }) { f ->
                    Column(
                        Modifier.fillMaxWidth().clickable {
                            onStreamReady(
                                Stream(
                                    url = f.url, isHls = f.isHls, subs = Providers.subsFor(f, formatSubs),
                                    referer = f.referer, sampleMp4 = null,
                                    imdb = imdbState,
                                ),
                                null,
                            )
                        }.padding(12.dp),
                    ) {
                        Text(f.label, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            (if (f.height > 0) "${f.height}p · " else "") + if (f.isHls) "HLS" else "MP4",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(entries ?: emptyList(), key = { it.infoHash }) { e ->
                    Column(
                        Modifier.fillMaxWidth().clickable { onPickTorrent(e) }.padding(12.dp),
                    ) {
                        Text(e.name, maxLines = 2, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${e.size} · 👤 ${e.seeds}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
