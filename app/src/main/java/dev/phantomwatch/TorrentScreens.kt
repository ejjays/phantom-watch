package dev.phantomwatch

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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.phantomwatch.ui.PlayerPanel
import dev.phantomwatch.ui.PlayerStage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun isWifi(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}

@Composable
internal fun TorrentsScreen(title: Title, season: String, episode: String, autoplay: Boolean, onFetch: (TEntry) -> Unit, onSeedReady: (Stream, TEntry?) -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<TEntry>?>(null) }
    var formats by remember { mutableStateOf<List<VFormat>>(emptyList()) }
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
                VidStream.directAll(title.id, title.kind, season, episode, title.title)
            }
            val imdbJob = async(Dispatchers.IO) {
                VidLove.imdbOf(title.id, title.kind, BuildConfig.TMDB_API_KEY)?.removePrefix("tt")
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
                    AppLog.d("direct play ${best.label} hls=${best.isHls}")
                    gone = true
                    onSeedReady(
                        Stream(
                            url = best.url, isHls = best.isHls,
                            subs = all.second,
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
            if (!TORRENTS_ENABLED) return@LaunchedEffect
            val key = BuildConfig.TMDB_API_KEY
            val imdb = withContext(Dispatchers.IO) { VidLove.imdbOf(title.id, title.kind, key) }
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
                                onSeedReady(
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
                        onFetch(it)
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
                    title = title.title, fullscreen = false, isPlaying = false, stalled = true,
                    positionMs = 0, durationMs = 0, bufferedMs = 0, controlsVisible = true,
                    flash = null, seekMark = null, seekInset = 0.dp, locked = false,
                    subsOn = false, canSubs = true,
                    onBack = onBack, onFullscreen = {}, onTogglePlay = {}, onToggleLock = {},
                    onSeek = {}, onInteract = {}, onZoneTap = {}, onToggleSubs = {},
                    onSwipe = { _, _ -> }, onSwipeEnd = {},
                    video = { mod -> Box(mod.fillMaxSize().background(Color.Black)) },
                )
            }
            PlayerPanel(title = title.title, year = title.year, rating = title.rating)
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
                            onSeedReady(
                                Stream(
                                    url = f.url, isHls = f.isHls, subs = formatSubs,
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
                        Modifier.fillMaxWidth().clickable { onFetch(e) }.padding(12.dp),
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

@Composable
internal fun FetchScreen(
    title: Title,
    season: String,
    episode: String,
    entry: TEntry,
    dir: java.io.File,
    onPlay: (Stream, DlCtl) -> Unit,
    onBack: () -> Unit,
) {
    var prog by remember { mutableStateOf(0f) }
    var stat by remember { mutableStateOf("starting…") }
    var err by remember { mutableStateOf<String?>(null) }
    var ctl by remember { mutableStateOf<DlCtl?>(null) }
    val context = LocalContext.current
    BackHandler(onBack = onBack)
    var wifiOk by remember { mutableStateOf(isWifi(context)) }
    if (!wifiOk) {
        AlertDialog(
            onDismissRequest = { onBack() },
            title = { Text("Not on WiFi") },
            text = { Text("Torrents will eat mobile data. Continue anyway?") },
            confirmButton = { TextButton(onClick = { wifiOk = true }) { Text("Download") } },
            dismissButton = { TextButton(onClick = { onBack() }) { Text("Back") } },
        )
        return
    }
    LaunchedEffect(entry.infoHash) {
        try {
            stat = "contacting peers…"
            var h = withContext(Dispatchers.IO) { TorrentEngine.download(entry, dir) }
            ctl = DlCtl(h)
            var vlSubs: List<Sub> = emptyList()
            val subsJob = launch {
                val bundled = withContext(Dispatchers.IO) { Torrents.bundledSubs(dir) }
                val live = mutableListOf<Sub>()
                for (s in bundled) {
                    if (live.size >= 8) break
                    if (withContext(Dispatchers.IO) { Torrents.subAlive(s.url) }) live.add(s)
                    else AppLog.d("subs drop ${s.label}")
                }
                vlSubs = live
                AppLog.d("subs live ${live.map { it.label }}")
            }
            var lastDone = 0L
            var lastChange = System.currentTimeMillis()
            var lastLog = 0L
            var lastArm = 0L
            val metaSince = System.currentTimeMillis()
            var pick: Torrents.VidPick? = null
            while (true) {
                ensureActive()
                if (pick == null) {
                    pick = withContext(Dispatchers.IO) { Torrents.pickIndexed(h, dir) }
                    if (pick == null) {
                        if (System.currentTimeMillis() - metaSince > 60000) {
                            ctl?.stop()
                            err = "no peers sharing this yet, pick another"
                            return@LaunchedEffect
                        }
                        stat = "waiting for metadata…"
                        delay(1000)
                        continue
                    }
                    val p0 = pick
                    if (!p0.file.exists() || p0.file.length() == 0L) {
                        AppLog.d("fetch stale disk, re-adding")
                        withContext(Dispatchers.IO) { TorrentEngine.reset() }
                        h = withContext(Dispatchers.IO) { TorrentEngine.download(entry, dir) }
                        ctl?.stop()
                        ctl = DlCtl(h)
                        pick = null
                        continue
                    }
                    AppLog.d("torrent video ${p0.file.name} ${p0.size / 1048576}MB")
                    withContext(Dispatchers.IO) { TorrentEngine.armStream(h, p0.index) }
                }
                val p = pick
                val st = h.status()
                val fp = try {
                    h.fileProgress()[p.index]
                } catch (_: Exception) {
                    -1L
                }
                val done = if (fp >= 0) fp else st.totalDone()
                prog = if (p.size > 0) (done.toFloat() / p.size).coerceIn(0f, 1f) else 0f
                stat = "${done / 1048576}MB / ${p.size / 1048576}MB · ${st.downloadRate() / 1024}KB/s · ${st.numPeers()} peers"
                if (done > lastDone) {
                    lastDone = done
                    lastChange = System.currentTimeMillis()
                }
                if (System.currentTimeMillis() - lastLog > 10000) {
                    lastLog = System.currentTimeMillis()
                    AppLog.d("fetch ${done / 1048576}MB/${p.size / 1048576}MB ${st.downloadRate() / 1024}KB/s ${st.numPeers()}p")
                }
                // start only once the head is dense, else Exo reads ahead into sparse ranges
                val need = minOf(60000000L, maxOf(25000000L, p.size * 8 / 100))
                val headNeed = minOf(need, 30000000L)
                if (System.currentTimeMillis() - lastArm > 15000) {
                    lastArm = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { TorrentEngine.armStream(h, p.index) }
                }
                val head = withContext(Dispatchers.IO) { TorrentEngine.densePrefix(h, p.index, p.size) }
                if ((done >= need && head >= headNeed) || (done >= p.size && p.size > 0)) {
                    subsJob.join()
                    val subs = vlSubs
                    val trusted = subs.any { it.url.startsWith("file") && !it.url.contains("/subs/") && it.label.contains("english", true) }
                    val full = done >= p.size && p.size > 0
                    AppLog.d("fetch play ${p.file.name} ${done / 1048576}MB head=${head / 1048576}MB subs=${subs.size} ${subs.take(4).joinToString { it.label }} trusted=$trusted full=$full")
                    ctl!!.fileIndex = p.index
                    ctl!!.fileSize = p.size
                    onPlay(
                        Stream(
                            android.net.Uri.fromFile(p.file).toString(),
                            false,
                            subs,
                            "",
                            p.file.absolutePath,
                            trusted,
                            full,
                            if (title.kind == "movie") "movie/${title.id}"
                            else "tv/${title.id}/$season/$episode",
                        ),
                        ctl!!,
                    )
                    return@LaunchedEffect
                }
                if (System.currentTimeMillis() - lastChange > 180000 && done > 0) {
                    err = "stalled, try another"
                    return@LaunchedEffect
                }
                delay(800)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = "${e.javaClass.simpleName}: ${e.message}"
            AppLog.d("fetch fail ${e.message}")
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(entry.name, maxLines = 2, style = MaterialTheme.typography.titleMedium)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        androidx.compose.material3.LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        Text(stat, style = MaterialTheme.typography.bodySmall)
        if (err != null) Text(err!!, style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                ctl?.stop()
                onBack()
            }) { Text(if (err != null) "Back" else "Cancel") }
        }
        Text("stays in cache — keep app open while downloading", style = MaterialTheme.typography.bodySmall)
    }
}
