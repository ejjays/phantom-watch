package dev.phantomwatch

import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.frostwire.jlibtorrent.TorrentHandle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    data class Player(val title: String, val stream: Stream, val ctl: DlCtl?) : Screen
    data class Torrents(val title: Title, val season: String, val episode: String) : Screen
    data class Fetch(val title: Title, val season: String, val episode: String, val entry: TEntry) : Screen
}

class DlCtl(val handle: TorrentHandle) {
    @Volatile var active = true
    var tries = 0
    var fileIndex = -1
    var fileSize = 0L
    fun stop() {
        active = false
        try {
            handle.pause()
        } catch (_: Exception) {
        }
    }
}

@Composable
private fun App() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SubsApi.init(context)
        withContext(Dispatchers.IO) {
            try {
                TorrentEngine.ensure()
            } catch (_: Exception) {
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        when (val s = screen) {
            is Screen.Home -> HomeScreen(
                onPick = { t, seas, ep ->
                    screen = Screen.Torrents(t, seas, ep)
                },
            )
            is Screen.Player -> PlayerScreen(
                title = s.title,
                stream = s.stream,
                ctl = s.ctl,
                onBack = {
                    s.ctl?.stop()
                    screen = Screen.Home
                },
            )
            is Screen.Torrents -> TorrentsScreen(
                title = s.title,
                season = s.season,
                episode = s.episode,
                onFetch = { e -> screen = Screen.Fetch(s.title, s.season, s.episode, e) },
                onBack = { screen = Screen.Home },
            )
            is Screen.Fetch -> {
                val dir = remember(s.entry.infoHash) {
                    java.io.File(context.cacheDir, "torrents/${s.entry.infoHash}")
                }
                FetchScreen(
                    title = s.title,
                    season = s.season,
                    episode = s.episode,
                    entry = s.entry,
                    dir = dir,
                    onPlay = { stream, ctl ->
                        screen = Screen.Player(s.title.title, stream, ctl)
                    },
                    onBack = { screen = Screen.Torrents(s.title, s.season, s.episode) },
                )
            }
        }
        LogFab()
    }
}

@Composable
private fun LogFab() {
    var open by remember { mutableStateOf(false) }
    var ox by remember { mutableStateOf(0f) }
    var oy by remember { mutableStateOf(0f) }
    Box(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),
    ) {
        Button(
            onClick = { open = true },
            modifier = Modifier.align(Alignment.TopEnd)
                .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        ox += drag.x
                        oy += drag.y
                    }
                },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("LOG", style = MaterialTheme.typography.labelSmall) }
    }
    if (open) {
        val clip = LocalClipboardManager.current
        val scroll = rememberScrollState()
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Logs (${AppLog.lines.size})") },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(max = 400.dp).verticalScroll(scroll)) {
                    Text(
                        AppLog.lines.joinToString("\n"),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { AppLog.lines.clear() }) { Text("Clear") }
                    TextButton(onClick = {
                        clip.setText(AnnotatedString(AppLog.lines.joinToString("\n")))
                    }) { Text("Copy all") }
                    TextButton(onClick = { open = false }) { Text("Close") }
                }
            },
        )
    }
}

private fun isWifi(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
}

private fun cacheBytes(dir: java.io.File): Long {
    return try {
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    } catch (_: Exception) {
        0L
    }
}

@Composable
private fun HomeScreen(onPick: (Title, String, String) -> Unit) {
    val scope = rememberCoroutineScope()
    var titles by remember { mutableStateOf<List<Title>?>(null) }
    var pending by remember { mutableStateOf<Title?>(null) }
    var season by remember { mutableStateOf("1") }
    var episode by remember { mutableStateOf("1") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Title>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var cacheMb by remember { mutableStateOf(0L) }
    LaunchedEffect(titles) {
        cacheMb = withContext(Dispatchers.IO) {
            cacheBytes(java.io.File(context.cacheDir, "torrents")) / 1048576
        }
    }

    fun runSearch() {
        val q = query.trim()
        if (q.length < 2) {
            results = null
            return
        }
        searching = true
        scope.launch {
            results = withContext(Dispatchers.IO) { VidLove.search(q) }
            searching = false
        }
    }

    LaunchedEffect(titles, error) {
        if (titles != null || error != null) return@LaunchedEffect
        loading = true
        val key = BuildConfig.TMDB_API_KEY
        try {
            val list = withContext(Dispatchers.IO) { VidLove.trending(key) }
            if (list.isEmpty()) error = "Trending empty"
            else titles = list
        } catch (e: Exception) {
            val diag = withContext(Dispatchers.IO) {
                listOf(
                    "https://www.google.com/generate_204",
                    "https://image.tmdb.org/t/p/w342/abc.jpg",
                ).joinToString("\n") { probe(it) }
            }
            error = "Trending failed: ${e.message}\n$diag"
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (results != null) "Results" else "Trending",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (error != null) TextButton(onClick = { error = null; titles = null }) {
                Text("Retry")
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search movies / shows") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { runSearch() }) { Text("Go") }
        }
        if (cacheMb > 0) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Cache ${cacheMb}MB", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            java.io.File(context.cacheDir, "torrents").deleteRecursively()
                            TorrentEngine.reset()
                        }
                        cacheMb = 0
                    }
                }) { Text("Clear") }
            }
        }
        val shown = results ?: titles
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && titles == null -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { Text(error!!) }
            searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            shown != null -> LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { "${it.kind}-${it.id}" }) { t ->
                    Column(
                        Modifier.padding(4.dp).clickable {
                            if (t.kind == "movie") onPick(t, "1", "1")
                            else pending = t
                        },
                    ) {
                        if (t.poster != null) AsyncImage(
                            model = t.poster,
                            contentDescription = t.title,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
                            contentScale = ContentScale.Crop,
                        )
                        Text(t.title, maxLines = 2, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    val tv = pending
    if (tv != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(tv.title) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = season,
                        onValueChange = { season = it.filter(Char::isDigit).take(2) },
                        label = { Text("Season") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = episode,
                        onValueChange = { episode = it.filter(Char::isDigit).take(3) },
                        label = { Text("Episode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    pending = null
                    onPick(tv, season.ifEmpty { "1" }, episode.ifEmpty { "1" })
                }) { Text("Play") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
        )
    }

    if (error != null && titles != null) {
        AlertDialog(
            onDismissRequest = { error = null },
            title = { Text("Playback failed") },
            text = { Text(error!!) },
            confirmButton = { TextButton(onClick = { error = null }) { Text("OK") } },
        )
    }
}

private suspend fun readSubText(url: String): String? = withContext(Dispatchers.IO) {
    if (!url.startsWith("file")) return@withContext null
    try {
        val path = android.net.Uri.parse(url).path ?: return@withContext null
        java.io.File(path).readText()
    } catch (_: Exception) {
        null
    }
}

@OptIn(UnstableApi::class)
private fun configsFor(subs: List<Sub>, firstUri: Uri? = null): List<MediaItem.SubtitleConfiguration> {
    return VidLove.englishFirst(subs).mapIndexed { i, sub ->
        val uri = if (i == 0 && firstUri != null) firstUri else Uri.parse(sub.url)
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(VidLove.mimeOf(uri.toString()))
            .setLanguage("en")
            .setLabel(sub.label)
            .apply {
                if (sub.label.contains("english", true)) {
                    setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                }
            }
            .build()
    }
}

@OptIn(UnstableApi::class)
private fun itemFor(url: String, hls: Boolean, cfgs: List<MediaItem.SubtitleConfiguration>): MediaItem =
    MediaItem.Builder()
        .setUri(url)
        .setMimeType(if (hls) MimeTypes.APPLICATION_M3U8 else MimeTypes.VIDEO_MP4)
        .setSubtitleConfigurations(cfgs)
        .build()

@Composable
private fun TorrentsScreen(title: Title, season: String, episode: String, onFetch: (TEntry) -> Unit, onBack: () -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var entries by remember { mutableStateOf<List<TEntry>?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(title.id, season, episode) {
        try {
            val key = BuildConfig.TMDB_API_KEY
            val imdb = withContext(Dispatchers.IO) { VidLove.imdbOf(title.id, title.kind, key) }
            if (imdb == null) {
                err = "no imdb id"
                return@LaunchedEffect
            }
            val found = withContext(Dispatchers.IO) {
                Torrents.query(imdb, title.kind, season, episode)
            }
            if (found.isEmpty()) err = "no torrents" else entries = found
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            err = e.message
        } finally {
            loading = false
        }
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
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            err != null && entries == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(err!!)
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
private fun FetchScreen(
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
                val need = minOf(25000000L, maxOf(8000000L, p.size * 4 / 100))
                if (System.currentTimeMillis() - lastArm > 15000) {
                    lastArm = System.currentTimeMillis()
                    withContext(Dispatchers.IO) { TorrentEngine.armStream(h, p.index) }
                }
                if (done >= need || (done >= p.size && p.size > 0)) {
                    subsJob.join()
                    val subs = vlSubs
                    val trusted = subs.any { it.url.startsWith("file") && !it.url.contains("/subs/") && it.label.contains("english", true) }
                    val full = done >= p.size && p.size > 0
                    AppLog.d("fetch play ${p.file.name} ${done / 1048576}MB subs=${subs.size} ${subs.take(4).joinToString { it.label }} trusted=$trusted full=$full")
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

private fun detectLang(cues: List<VidLove.Cue>): String? {
    if (cues.isEmpty()) return null
    val step = maxOf(1, cues.size / 60)
    var ja = 0
    var ko = 0
    var zh = 0
    var en = 0
    var th = 0
    var ar = 0
    var ru = 0
    var n = 0
    var i = 0
    while (i < cues.size && n < 60) {
        val t = cues[i].text.replace(Regex("\\{[^}]*\\}"), "")
        if (t.isNotBlank()) {
            n++
            when {
                t.contains(Regex("[\u3040-\u30ff]")) -> ja++
                Regex("[\uac00-\ud7af]").containsMatchIn(t) -> ko++
                t.contains(Regex("[\u0e00-\u0e7f]")) -> th++
                t.contains(Regex("[\u0600-\u06ff]")) -> ar++
                t.contains(Regex("[\u0400-\u04ff]")) -> ru++
                t.contains(Regex("[\u4e00-\u9fff]")) -> zh++
                t.contains(Regex("[a-zA-Z]")) -> en++
            }
        }
        i += step
    }
    if (n == 0) return null
    val best = mapOf("ja" to ja, "ko" to ko, "th" to th, "ar" to ar, "ru" to ru, "zh" to zh, "en" to en)
        .maxByOrNull { it.value }
    return best?.takeIf { it.value > 0 }?.key
}

private fun buildMediaFactory(
    context: android.content.Context,
    stream: Stream,
    headers: Map<String, String>,
    ctl: DlCtl?,
): androidx.media3.exoplayer.source.DefaultMediaSourceFactory {
    val retry = androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(3)
    if (stream.url.startsWith("file")) {
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
            DefaultDataSource.Factory(context),
        ).setLoadErrorHandlingPolicy(retry)
    }
    return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(
        DefaultHttpDataSource.Factory()
            .setUserAgent(headers.getValue("User-Agent"))
            .setDefaultRequestProperties(headers),
    ).setLoadErrorHandlingPolicy(retry)
}

@OptIn(UnstableApi::class)
private fun buildPlayer(context: android.content.Context, stream: Stream, ctl: DlCtl?): ExoPlayer {
    val origin = "https://${Uri.parse(stream.referer).host}"
    val headers = mapOf(
        "Referer" to stream.referer,
        "Origin" to origin,
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36",
    )
    val selector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setPreferredAudioLanguage("en")
            .setPreferredTextLanguage("en")
            .build()
    }
    val mediaFactory = buildMediaFactory(context, stream, headers, ctl)
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .setMediaSourceFactory(mediaFactory)
        .build()
        .apply {
            setMediaItem(itemFor(stream.url, stream.isHls, configsFor(stream.subs)))
            prepare()
            play()
        }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreen(title: String, stream: Stream, ctl: DlCtl?, onBack: () -> Unit) {
    val context = LocalContext.current
    val resumeScope = rememberCoroutineScope()
    var buildError by remember { mutableStateOf<String?>(null) }
    var emberDone by remember { mutableStateOf(false) }
    var embedNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var embedIdx by remember { mutableStateOf(-1) }
    var aiSubUrl by remember { mutableStateOf<String?>(null) }
    var embedGroup by remember { mutableStateOf<androidx.media3.common.TrackGroup?>(null) }
    LaunchedEffect(title) { AppLog.d("screen $title") }
    val player: ExoPlayer? = remember {
        try {
            buildPlayer(context, stream, ctl).also { AppLog.d("built") }
        } catch (e: Exception) {
            buildError = "${e.javaClass.simpleName}: ${e.message}"
            AppLog.d("build crash ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }
    if (player == null) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Player failed: $buildError")
            Button(onClick = onBack) { Text("Back") }
        }
        return
    }
    val exo = player
    DisposableEffect(exo) {        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                val audios = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .map { g ->
                        val f = g.getTrackFormat(0)
                        "${f.language}:${f.sampleMimeType?.substringAfterLast('/')} ${f.channelCount}ch${if (g.isSelected) "*" else ""}"
                    }
                if (audios.isNotEmpty()) {
                    AppLog.d("audio [${audios.joinToString()}]")
                }
                val texts = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .map { g ->
                        val f = g.getTrackFormat(0)
                        "${f.language}:${f.label}${if (g.isSelected) "*" else ""}"
                    }
                if (texts.isNotEmpty()) {
                    AppLog.d("embed subs [${texts.joinToString()}]")
                }
                val videos = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO }
                    .map { g ->
                        val f = g.getTrackFormat(0)
                        "${f.width}x${f.height}:${f.sampleMimeType?.substringAfterLast('/')}${if (g.isSelected) "*" else ""}"
                    }
                if (videos.isNotEmpty()) {
                    AppLog.d("video [${videos.joinToString()}]")
                }
                run {
                    val g = tracks.groups.firstOrNull { it.type == C.TRACK_TYPE_TEXT }
                    if (g != null) {
                        embedGroup = g.mediaTrackGroup
                        val names = (0 until g.mediaTrackGroup.length).map { i ->
                            val f = g.mediaTrackGroup.getFormat(i)
                            val lang = f.language ?: "und"
                            val lab = f.label ?: ""
                            if (lab.isNotEmpty()) "$lang:$lab" else lang
                        }
                        if (names != embedNames) embedNames = names
                    }
                }
                if (!emberDone && stream.subs.isEmpty()) {
                    emberDone = true
                    try {
                        val group = embedGroup
                        if (group != null) {
                            var idx = -1
                            for (i in 0 until group.length) {
                                val f = group.getFormat(i)
                                if ((f.language ?: "").startsWith("en", true) ||
                                    (f.label ?: "").contains("english", true)
                                ) {
                                    idx = i
                                    break
                                }
                            }
                            if (idx >= 0) {
                                val sel = player.trackSelector as? DefaultTrackSelector
                                if (sel != null) {
                                    val ov = androidx.media3.common.TrackSelectionOverride(
                                        group,
                                        listOf(idx),
                                    )
                                    sel.parameters = sel.buildUponParameters()
                                        .setOverrideForType(ov)
                                        .build()
                                    AppLog.d("embed subs forced $idx")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLog.d("embed pick fail ${e.message}")
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                val name = when (state) {
                    Player.STATE_IDLE -> "idle"
                    Player.STATE_BUFFERING -> "buffering"
                    Player.STATE_READY -> "ready"
                    Player.STATE_ENDED -> "ended"
                    else -> "$state"
                }
                AppLog.d("player $name")
            }

            override fun onPlayerError(error: PlaybackException) {
                val cause = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                val chain = generateSequence<Throwable>(error) { it.cause }.take(3).joinToString(" <- ") {
                    "${it.javaClass.simpleName}: ${it.message}"
                }
                AppLog.d("player error ${error.errorCodeName} http=${cause?.responseCode} pos=${exo.currentPosition / 1000}s buf=${exo.bufferedPosition / 1000}s dur=${exo.duration / 1000}s")
                AppLog.d("player cause $chain")
                if (chain.contains("FileNotFound") || chain.contains("ENOENT")) {
                    try {
                        val f = android.net.Uri.parse(stream.url).path?.let { java.io.File(it) }
                        AppLog.d("file check exists=${f?.exists()} len=${f?.length()}")
                    } catch (_: Exception) {
                    }
                    val c0 = ctl
                    if (c0 != null) {
                        c0.stop()
                        AppLog.d("retries stopped, file gone")
                        try {
                            android.widget.Toast.makeText(context, "file gone, reopen the title", android.widget.Toast.LENGTH_LONG).show()
                        } catch (_: Exception) {
                        }
                    }
                }
                val c = ctl
                if (c != null && c.active && c.tries < 12) {
                    c.tries += 1
                    AppLog.d("player resume ${c.tries} awaiting +5MB")
                    resumeScope.launch {
                        val before = try {
                            c.handle.status().totalDone()
                        } catch (_: Exception) {
                            0L
                        }
                        val pos = exo.currentPosition
                        val dur = exo.duration.takeIf { it > 0 } ?: 0L
                        val t0 = System.currentTimeMillis()
                        while (System.currentTimeMillis() - t0 < 120000) {
                            if (!c.active) return@launch
                            val now = try {
                                c.handle.status().totalDone()
                            } catch (_: Exception) {
                                before
                            }
                            if (now > before + 5000000) break
                            if (dur > 0 && c.fileSize > 0 && c.fileIndex >= 0) {
                                try {
                                    val ti = c.handle.torrentFile()
                                    var start = 0L
                                    for (i in 0 until c.fileIndex) start += ti.files().fileSize(i)
                                    val plen = ti.pieceLength().toLong()
                                    val at = start + (pos.toDouble() / dur * c.fileSize).toLong()
                                    val bits = c.handle.status(TorrentHandle.QUERY_PIECES).pieces()
                                    val piece = (at / plen).toInt()
                                    if (piece >= 0 && piece < bits.size() && bits.getBit(piece)) break
                                } catch (_: Exception) {
                                }
                            }
                            delay(2000)
                        }
                        if (!c.active) return@launch
                        try {
                            val dl = try {
                                c.handle.status().totalDone() / 1048576
                            } catch (_: Exception) {
                                -1
                            }
                            AppLog.d("player resume at ${pos / 1000}s dl=${dl}MB")
                            val subs = stream.subs + aiSubUrl?.let { listOf(Sub("AI English", it)) }.orEmpty()
                            exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(subs)), pos)
                            exo.prepare()
                            exo.play()
                        } catch (_: Exception) {
                        }
                    }
                } else if (c != null && c.active) {
                    AppLog.d("player gave up after ${c.tries} retries")
                }
            }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }
    var subsOff by remember { mutableStateOf(false) }
    var subDelayMs by remember { mutableStateOf(0L) }
    var subRaw by remember { mutableStateOf<String?>(null) }
    val nudgeScope = rememberCoroutineScope()
    fun applyDelay() {
        if (stream.subs.isEmpty()) return
        nudgeScope.launch {
            var raw = subRaw
            if (raw == null) {
                raw = readSubText(VidLove.englishFirst(stream.subs).first().url)
                if (raw == null) {
                    AppLog.d("subs download failed")
                    return@launch
                }
                subRaw = raw
            }
            val f = withContext(Dispatchers.IO) {
                VidLove.shiftSubFile(raw, subDelayMs, context.cacheDir)
            }
            if (f == null) {
                AppLog.d("subs shift failed")
                return@launch
            }
            val pos = exo.currentPosition
            val wasPlaying = exo.isPlaying
            exo.setMediaItem(
                itemFor(stream.url, stream.isHls, configsFor(stream.subs, Uri.fromFile(f))),
                pos,
            )
            exo.prepare()
            if (wasPlaying) exo.play()
        }
    }
    fun nudge(delta: Long) {
        if (stream.subs.isEmpty()) return
        subDelayMs += delta
        AppLog.d("subs delay ${subDelayMs}ms")
        applyDelay()
    }
    var aiStatus by remember { mutableStateOf<String?>(null) }
    var aiRunning by remember { mutableStateOf(false) }
    var aiCues by remember { mutableStateOf<List<VidLove.Cue>>(emptyList()) }
    var lastNeed by remember { mutableStateOf(0) }
    suspend fun showAi() {
        val all = aiCues.sortedBy { it.start }
        val f = withContext(Dispatchers.IO) { VidLove.cuesToFile(all, context.cacheDir) } ?: return
        aiSubUrl = Uri.fromFile(f).toString()
        aiStatus = "AI ✓ ${all.size}"
        AppLog.d("ai cues=${all.size}")
        val merged = (stream.subs + Sub("AI English", Uri.fromFile(f).toString())).distinctBy { it.url }
        val pos = exo.currentPosition
        val wasPlaying = exo.isPlaying
        exo.setMediaItem(itemFor(stream.url, stream.isHls, configsFor(merged)), pos)
        exo.prepare()
        if (wasPlaying) exo.play()
        if (stream.subKey.isNotEmpty() && all.size >= 5) {
            withContext(Dispatchers.IO) {
                SubsApi.store(stream.subKey, "AI English", VidLove.renderCues(all), "ai")
            }
        }
    }
    fun runQwenSubs() {
        if (aiRunning) return
        aiRunning = true
        nudgeScope.launch {
            try {
                aiStatus = "Qwen…"
                val keys = withContext(Dispatchers.IO) { AiSubs.keys() }
                if (keys.isEmpty()) {
                    aiStatus = null
                    AppLog.d("ai qwen no groq keys, set GROQ_API_KEY")
                    return@launch
                }
                val path = try {
                    android.net.Uri.parse(stream.url).path?.takeIf { it.startsWith("/") }
                } catch (_: Exception) {
                    null
                } ?: return@launch
                val tracks = withContext(Dispatchers.IO) { MkvSubs.extract(path) }
                lastNeed = maxOf(lastNeed, tracks.maxOfOrNull { it.cues.size } ?: 0)
                var acted = false
                var current = false
                for (t in tracks) {
                    if (t.cues.size < 5) continue
                    val from = detectLang(t.cues)
                    AppLog.d("ai embed ${t.codec} cues=${t.cues.size} lang=${from ?: "?"}")
                    if (from == null || from == "en") continue
                    val fresh = t.cues.filter { n -> aiCues.none { o -> o.start == n.start } }
                    if (fresh.isEmpty()) {
                        current = true
                        continue
                    }
                    var added = 0
                    for (chunk in fresh.chunked(40)) {
                        val tr = withContext(Dispatchers.IO) { AiSubs.translateLines(chunk.map { it.text }, keys, from) }
                        if (tr == null || tr.size != chunk.size) break
                        val en = chunk.mapIndexed { j, c -> c.copy(text = tr[j]) }
                        aiCues = (aiCues + en).sortedBy { it.start }
                        added += en.size
                        showAi()
                    }
                    if (added > 0) {
                        aiStatus = "Qwen ✓"
                        AppLog.d("ai qwen +$added from=$from total=${aiCues.size}")
                        acted = true
                        break
                    }
                }
                if (!acted) {
                    AppLog.d(if (current) "ai subs up to date" else "ai qwen no usable embed subs")
                    if (!current) aiStatus = null
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                aiRunning = false
                throw e
            } catch (e: Exception) {
                aiStatus = null
                AppLog.d("ai qwen crash ${e.message}")
            } finally {
                aiRunning = false
            }
        }
    }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(stream.url) {
        try {
            if (stream.inSync) {
                syncStatus = "matched"
                AppLog.d("sync skipped, same-release subs")
                return@LaunchedEffect
            }
            runQwenSubs()
            val c = ctl
            if (c != null && !stream.inSync) {
                var waited = 0
                var lastAiDl = 0L
                while (waited < 600000) {
                    delay(15000)
                    if (!c.active) return@LaunchedEffect
                    val done = try {
                        c.handle.status().totalDone()
                    } catch (_: Exception) {
                        -1L
                    }
                    val total = try {
                        c.handle.status().totalWanted()
                    } catch (_: Exception) {
                        -1L
                    }
                    if (total > 0 && done >= total) {
                        var tries = 0
                        while (tries < 6 && aiCues.size < lastNeed) {
                            AppLog.d("ai retry on complete file")
                            runQwenSubs()
                            var w = 0
                            while (aiRunning && w < 12) {
                                delay(15000)
                                w++
                            }
                            tries++
                        }
                        return@LaunchedEffect
                    }
                    if (done > lastAiDl + 15000000) {
                        lastAiDl = done
                        AppLog.d("ai retry at ${done / 1048576}MB")
                        runQwenSubs()
                    }
                    waited += 15000
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.d("sync crash ${e.javaClass.simpleName}: ${e.message}")
            syncStatus = null
        }
    }
    fun applySubs() {
        val sel = exo.trackSelector as? DefaultTrackSelector ?: return
        sel.parameters = sel.buildUponParameters()
            .setRendererDisabled(C.TRACK_TYPE_TEXT, subsOff)
            .build()
        AppLog.d(if (subsOff) "subs off" else "subs on (${stream.subs.size})")
    }
    fun pickEmbed(idx: Int, why: String) {
        val group = embedGroup ?: return
        if (idx !in 0 until group.length) return
        try {
            val sel = exo.trackSelector as? DefaultTrackSelector ?: return
            sel.parameters = sel.buildUponParameters().setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group, listOf(idx)),
            ).build()
            embedIdx = idx
            AppLog.d("embed $why $idx ${embedNames.getOrNull(idx)}")
        } catch (e: Exception) {
            AppLog.d("embed pick fail ${e.message}")
        }
    }
    fun cycleEmbed() {
        val n = embedNames.size
        if (n == 0) return
        val next = if (embedIdx < 0) (if (n > 1) 1 else 0) else (embedIdx + 1) % n
        pickEmbed(next, "cycle")
    }
    var langDone by remember { mutableStateOf(false) }
    LaunchedEffect(embedNames) {
        if (langDone || stream.subs.isNotEmpty() || embedNames.isEmpty()) return@LaunchedEffect
        langDone = true
        val path = try {
            android.net.Uri.parse(stream.url).path
        } catch (_: Exception) {
            null
        }
        if (path == null || !path.startsWith("/")) return@LaunchedEffect
        val tracks = withContext(Dispatchers.IO) { MkvSubs.extract(path) }
        if (tracks.isEmpty()) return@LaunchedEffect
        val langs = tracks.map { t -> detectLang(t.cues) ?: "und" }
        AppLog.d("embed lang $langs")
        embedNames = embedNames.mapIndexed { i, n -> "$n:${langs.getOrNull(i) ?: "und"}" }
        val en = langs.indexOfFirst { it == "en" }
        if (en >= 0) pickEmbed(en, "auto")
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text(
                title,
                Modifier.weight(1f).padding(end = 16.dp),
                maxLines = 1,
                style = MaterialTheme.typography.titleMedium,
            )
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = exo
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stream.subs.isNotEmpty()) {
                Button(onClick = {
                    subsOff = !subsOff
                    applySubs()
                }) { Text(if (subsOff) "CC off" else "CC on · ${stream.subs.size}") }
                Text(
                    VidLove.englishFirst(stream.subs).first().label,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { nudge(-500) }) { Text("-0.5s") }
                Text(
                    "${subDelayMs / 1000.0}s",
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { nudge(500) }) { Text("+0.5s") }
                TextButton(onClick = { runQwenSubs() }) { Text("AI") }
                if (aiStatus != null) {
                    Text(aiStatus!!, style = MaterialTheme.typography.bodySmall)
                }
                if (syncStatus != null) {
                    Text(syncStatus!!, style = MaterialTheme.typography.bodySmall)
                }
            } else if (embedNames.isNotEmpty()) {
                val cur = if (embedIdx < 0) "CC: auto" else "CC: ${embedNames.getOrNull(embedIdx) ?: "?"}"
                Button(onClick = { cycleEmbed() }) { Text(cur) }
            } else {
                Text("no subs", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
