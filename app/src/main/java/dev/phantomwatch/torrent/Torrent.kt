package dev.phantomwatch.torrent

import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.core.Http
import dev.phantomwatch.data.Sub
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class TorrentRelease(val name: String, val size: String, val seeds: Int, val infoHash: String)

private const val TUA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"

private val TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://explodie.org:6969/announce",
)

private val SIZE_RE = Regex("([0-9.]+)\\s*(GB|MB)")
internal val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v")
internal const val TORRENTS_ENABLED = false

object Torrents {
    fun magnet(e: TorrentRelease): String {
        val tr = TRACKERS.joinToString("&") { "tr=${URLEncoder.encode(it, "UTF-8")}" }
        return "magnet:?xt=urn:btih:${e.infoHash}&dn=${URLEncoder.encode(e.name.take(80), "UTF-8")}&$tr"
    }

    private val audioDecoders: Map<String, Boolean> by lazy {
        try {
            val infos = android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos
            mapOf(
                "eac3" to infos.any { !it.isEncoder && it.supportedTypes.contains("audio/eac3") },
                "dts" to infos.any { !it.isEncoder && it.supportedTypes.any { t -> t.startsWith("audio/vnd.dts") || t == "audio/true-hd" } },
            )
        } catch (_: Exception) {
            mapOf("eac3" to true, "dts" to true)
        }
    }

    private fun seedsOf(title: String): Int {
        val i = title.indexOf("👤")
        if (i < 0) return 0
        return Regex("[0-9]+").find(title.substring(i))?.value?.toIntOrNull() ?: 0
    }

    private fun sizeBytes(size: String): Long {
        val m = Regex("([0-9.]+)\\s*(GB|MB)").find(size) ?: return 0L
        val n = m.groupValues[1].toDoubleOrNull() ?: 0.0
        return (n * if (m.groupValues[2] == "GB") 1_000_000_000L else 1_000_000L).toLong()
    }

    suspend fun query(imdb: String, kind: String, season: String, episode: String): List<TorrentRelease> =
        withContext(Dispatchers.IO) {
            val id = if (kind == "movie") "movie/$imdb" else "series/$imdb:$season:$episode"
            val req = Request.Builder()
                .url("https://torrentio.strem.fun/stream/$id.json")
                .header("User-Agent", TUA)
                .build()
            val call = Http.client.newCall(req)
            call.timeout().deadline(20, java.util.concurrent.TimeUnit.SECONDS)
            val body = call.execute().use { res ->
                if (res.code != 200) return@withContext emptyList()
                res.body?.string() ?: return@withContext emptyList()
            }
            val arr = try {
                JSONObject(body).optJSONArray("streams")
            } catch (_: Exception) {
                null
            } ?: return@withContext emptyList()
            val out = mutableListOf<TorrentRelease>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val hash = s.optString("infoHash")
                if (hash.length < 30) continue
                val title = (s.optString("title").ifEmpty { s.optString("name") })
                    .replace("\n", " ").trim().ifEmpty { "Unknown" }
                val size = SIZE_RE.find(title)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: "?"
                out.add(TorrentRelease(title.take(90), size, seedsOf(title), hash))
            }
            AppLog.d("torrentio $id streams=${out.size}")
            AppLog.d("audio dec eac3=${audioDecoders["eac3"]} dts=${audioDecoders["dts"]}")
            out.distinctBy { it.infoHash }.sortedWith(
                compareByDescending<TorrentRelease> { e ->
                    val t = e.name.lowercase()
                    var s = 0
                    if ("1080p" in t) s += 30
                    else if ("720p" in t) s += 20
                    else if ("2160p" in t || "4k" in t) s += 10
                    if ("cam" in t || "hdts" in t || "telesync" in t) s -= 50
                    if ("subfrench" in t) s -= 50
                    if ("engsub" in t || "english" in t) s += 5
                    if (audioDecoders["eac3"] == false && ("eac3" in t || "ddp" in t || "dd+" in t || "dolby" in t)) s -= 40
                    if (audioDecoders["dts"] == false && ("dts" in t || "truehd" in t || "true-hd" in t || "atmos" in t)) s -= 40
                    s += minOf(25, e.seeds / 10)
                    if (kind != "movie") {
                        if (Regex("s0*$season" + "e0*$episode\\b").containsMatchIn(t)) s += 60
                        if ("batch" in t) s -= 40
                        if (!Regex("s\\d+e\\d+|e\\d{2,}|ep?\\.?\\d+|\\d+x\\d+").containsMatchIn(t)) s -= 25
                        if (sizeBytes(e.size) > 2_000_000_000L) s -= 30
                    } else if (sizeBytes(e.size) > 12_000_000_000L) {
                        s -= 15
                    }
                    s
                }.thenByDescending { e ->
                    val t = e.name.lowercase()
                    if ("aac" in t || "ac3" in t || "eac3" in t || "mp3" in t || "opus" in t) 1 else 0
                }.thenByDescending { it.seeds },
            ).take(12)
        }

    fun best(entries: List<TorrentRelease>): TorrentRelease? =
        entries.firstOrNull { it.infoHash !in DeadReleases.hashes && it.seeds >= 3 }
            ?: entries.firstOrNull { it.infoHash !in DeadReleases.hashes }

    object DeadReleases {
        val hashes = mutableSetOf<String>()
        val urls = mutableSetOf<String>()
    }

    object DeadHosts {
        // host -> ban expiry, so a short cooldown can expire inside the same process
        private val mem = java.util.concurrent.ConcurrentHashMap<String, Long>()
        private val hang = mutableMapOf<String, Long>()
        private var prefs: android.content.SharedPreferences? = null

        fun init(ctx: android.content.Context) {
            if (prefs == null) {
                prefs = ctx.applicationContext.getSharedPreferences("phantom_net", android.content.Context.MODE_PRIVATE)
            }
        }

        fun hostOf(url: String): String =
            try {
                java.net.URI(url).host.orEmpty()
            } catch (_: Exception) {
                ""
            }

        fun mark(url: String, hours: Long = 24) {
            ban(url, System.currentTimeMillis() + hours * 3_600_000)
        }

        // a 429 is a transient rate limit, not a dead host; keep it out of the 24h list
        fun cooldown(url: String, minutes: Long = 10) {
            ban(url, System.currentTimeMillis() + minutes * 60_000)
        }

        private fun ban(url: String, until: Long) {
            val h = hostOf(url)
            if (h.isEmpty()) return
            mem[h] = until
            try {
                prefs?.edit()?.putLong(h, until)?.apply()
            } catch (_: Exception) {
            }
        }

        fun blocked(url: String): Boolean {
            val h = hostOf(url)
            if (h.isEmpty()) return false
            val now = System.currentTimeMillis()
            val cached = mem[h]
            if (cached != null) {
                if (cached > now) return true
                mem.remove(h)
            }
            val until = try {
                prefs?.getLong(h, 0) ?: 0
            } catch (_: Exception) {
                0
            }
            if (until > now) {
                mem[h] = until
                return true
            }
            return false
        }

        // a hang is not one probe away from fine: the host served the manifest and still stalled,
        // so only its own expiry clears it, never the re-admit probe
        fun markHang(url: String, hours: Long = 2) {
            mark(url, hours)
            val h = hostOf(url)
            if (h.isEmpty()) return
            val until = System.currentTimeMillis() + hours * 3_600_000
            hang[h] = until
            try {
                prefs?.edit()?.putLong("hang|$h", until)?.apply()
            } catch (_: Exception) {
            }
        }

        fun hangBanned(url: String): Boolean {
            val h = hostOf(url)
            if (h.isEmpty()) return false
            val until = hang[h] ?: try {
                prefs?.getLong("hang|$h", 0) ?: 0
            } catch (_: Exception) {
                0
            }
            if (until <= System.currentTimeMillis()) return false
            hang[h] = until
            return true
        }

        fun forgive(url: String) {
            val h = hostOf(url)
            if (h.isEmpty() || hangBanned(url)) return
            mem.remove(h)
            try {
                prefs?.edit()?.remove(h)?.apply()
            } catch (_: Exception) {
            }
        }
    }

    data class VidPick(val file: File, val index: Int, val size: Long)

    fun pickIndexed(h: TorrentHandle, dir: File): VidPick? {
        return try {
            val ti = h.torrentFile()
            val fs = ti.files()
            var best: VidPick? = null
            for (i in 0 until fs.numFiles()) {
                val rel = fs.filePath(i)
                val ext = rel.substringAfterLast('.', "").lowercase()
                if (ext !in VIDEO_EXTS) continue
                val size = fs.fileSize(i)
                val f = File(dir, rel)
                val score = size + (if (ext in setOf("mp4", "m4v", "mov")) 1_000_000_000L else 0L)
                val cur = best?.let { it.size + (if (it.file.extension.lowercase() in setOf("mp4", "m4v", "mov")) 1_000_000_000L else 0L) } ?: -1L
                if (score > cur) best = VidPick(f, i, size)
            }
            best
        } catch (_: Exception) {
            null
        }
    }

    fun bundledSubs(dir: File): List<Sub> =
        dir.walkTopDown().filter { f ->
            f.isFile && f.extension.lowercase() in setOf("srt", "vtt")
        }.take(6).map { f ->
            val name = f.nameWithoutExtension
            val label = if (name.contains("eng", true)) "English" else name
            Sub(label, android.net.Uri.fromFile(f).toString())
        }.toList()

    fun subAlive(fileUri: String): Boolean {
        return try {
            val path = android.net.Uri.parse(fileUri).path ?: return false
            java.io.File(path).readText().contains("-->")
        } catch (_: Exception) {
            false
        }
    }
}

object TorrentEngine {
    private var session: SessionManager? = null

    @Synchronized
    fun ensure(): SessionManager {
        session?.let { return it }
        val s = SessionManager()
        s.start()
        try {
            s.startDht()
        } catch (_: Exception) {
        }
        session = s
        AppLog.d("torrent session up")
        return s
    }

    fun reset() {
        try {
            session?.stop()
        } catch (_: Exception) {
        }
        session = null
        AppLog.d("torrent session reset")
    }

    suspend fun download(e: TorrentRelease, dir: File): TorrentHandle {
        val s = ensure()
        if (!dir.exists()) dir.mkdirs()
        s.download(Torrents.magnet(e), dir, torrent_flags_t.from_int(0x200))
        val hash = Sha1Hash(e.infoHash)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 20000) {
            try {
                s.find(hash)?.let { return it }
            } catch (_: Exception) {
            }
            delay(500)
        }
        throw RuntimeException("torrent handle timeout")
    }

    fun densePrefix(h: TorrentHandle, fileIndex: Int, fileSize: Long): Long {
        return try {
            val ti = h.torrentFile()
            var start = 0L
            for (i in 0 until fileIndex) start += ti.files().fileSize(i)
            val plen = ti.pieceLength().toLong()
            if (plen <= 0) return 0L
            val bits = h.status(TorrentHandle.QUERY_PIECES).pieces()
            var p = (start / plen).toInt()
            val last = ((start + fileSize - 1) / plen).toInt()
            var bytes = 0L
            while (p <= last && p < bits.size() && bits.getBit(p)) {
                bytes += plen
                p++
            }
            minOf(bytes, fileSize)
        } catch (_: Exception) {
            0L
        }
    }

    fun armStream(h: TorrentHandle, fileIndex: Int) {
        try {
            val ti = h.torrentFile()
            val plen = ti.pieceLength().toLong()
            val n = ti.files().numFiles()
            try {
                h.prioritizeFiles(Array(n) { i ->
                    when {
                        i == fileIndex -> Priority.SEVEN
                        ti.files().filePath(i).substringAfterLast('.', "").lowercase() in setOf("srt", "vtt") -> Priority.NORMAL
                        else -> Priority.IGNORE
                    }
                })
            } catch (_: Exception) {
            }
            var start = 0L
            for (i in 0 until fileIndex) start += ti.files().fileSize(i)
            val size = ti.files().fileSize(fileIndex)
            val first = (start / plen).toInt()
            val last = ((start + size - 1) / plen).toInt()
            var p = first
            val frontEnd = minOf(last, first + (60000000L / plen).toInt())
            while (p <= frontEnd) {
                try {
                    h.setPieceDeadline(p, 7000)
                } catch (_: Exception) {
                }
                p += 1
            }
            var q = maxOf(first, last - (8000000L / plen).toInt() - 1)
            val tailStart = q
            while (q <= last) {
                try {
                    h.setPieceDeadline(q, 15000)
                } catch (_: Exception) {
                }
                q += 1
            }
            AppLog.d("torrent armed $first..$frontEnd + $tailStart..$last")
            try {
                val bits = h.status(TorrentHandle.QUERY_PIECES).pieces()
                var miss = 0
                while (miss < bits.size() && bits.getBit(miss)) miss += 1
                AppLog.d("torrent miss=$miss/${bits.size()}")
            } catch (_: Exception) {
            }
        } catch (e: Exception) {
            AppLog.d("torrent arm fail ${e.message}")
        }
    }
}
