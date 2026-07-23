package dev.phantomwatch.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.isWifi
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.data.Title
import dev.phantomwatch.torrent.DownloadControl
import dev.phantomwatch.torrent.TorrentEngine
import dev.phantomwatch.torrent.TorrentRelease
import dev.phantomwatch.torrent.Torrents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TorrentFetchScreen(
    title: Title,
    season: String,
    episode: String,
    entry: TorrentRelease,
    dir: java.io.File,
    onPlay: (Stream, DownloadControl) -> Unit,
    onBack: () -> Unit,
) {
    var prog by remember { mutableStateOf(0f) }
    var stat by remember { mutableStateOf("starting…") }
    var err by remember { mutableStateOf<String?>(null) }
    var control by remember { mutableStateOf<DownloadControl?>(null) }
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
            control = DownloadControl(h)
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
                            control?.stop()
                            err = "no peers sharing this yet, pick another"
                            return@LaunchedEffect
                        }
                        stat = "waiting for metadata…"
                        kotlinx.coroutines.delay(1000)
                        continue
                    }
                    val p0 = pick
                    if (!p0.file.exists() || p0.file.length() == 0L) {
                        AppLog.d("fetch stale disk, re-adding")
                        withContext(Dispatchers.IO) { TorrentEngine.reset() }
                        h = withContext(Dispatchers.IO) { TorrentEngine.download(entry, dir) }
                        control?.stop()
                        control = DownloadControl(h)
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
                    control!!.fileIndex = p.index
                    control!!.fileSize = p.size
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
                        control!!,
                    )
                    return@LaunchedEffect
                }
                if (System.currentTimeMillis() - lastChange > 180000 && done > 0) {
                    err = "stalled, try another"
                    return@LaunchedEffect
                }
                kotlinx.coroutines.delay(800)
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
        LinearProgressIndicator(progress = { prog }, modifier = Modifier.fillMaxWidth())
        androidx.compose.foundation.layout.Spacer(Modifier.padding(4.dp))
        Text(stat, style = MaterialTheme.typography.bodySmall)
        if (err != null) Text(err!!, style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = {
                control?.stop()
                onBack()
            }) { Text(if (err != null) "Back" else "Cancel") }
        }
        Text("stays in cache — keep app open while downloading", style = MaterialTheme.typography.bodySmall)
    }
}
