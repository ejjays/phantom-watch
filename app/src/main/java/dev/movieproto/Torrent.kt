package dev.movieproto

import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.Sha1Hash
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.swig.torrent_flags_t
import java.io.File
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject

data class TEntry(val name: String, val size: String, val seeds: Int, val infoHash: String)

private const val TUA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36"

private val TRACKERS = listOf(
    "udp://tracker.opentrackr.org:1337/announce",
    "udp://open.stealth.si:80/announce",
    "udp://tracker.torrent.eu.org:451/announce",
    "udp://explodie.org:6969/announce",
)

private val SIZE_RE = Regex("([0-9.]+)\\s*(GB|MB)")
private val VIDEO_EXTS = setOf("mp4", "mkv", "avi", "webm", "mov", "m4v")

object Torrents {
    fun magnet(e: TEntry): String {
        val tr = TRACKERS.joinToString("&") { "tr=${URLEncoder.encode(it, "UTF-8")}" }
        return "magnet:?xt=urn:btih:${e.infoHash}&dn=${URLEncoder.encode(e.name.take(80), "UTF-8")}&$tr"
    }

    private fun seedsOf(title: String): Int {
        val i = title.indexOf("👤")
        if (i < 0) return 0
        return Regex("[0-9]+").find(title.substring(i))?.value?.toIntOrNull() ?: 0
    }

    suspend fun query(imdb: String, kind: String, season: String, episode: String): List<TEntry> =
        withContext(Dispatchers.IO) {
            val id = if (kind == "movie") "movie/$imdb" else "series/$imdb:$season:$episode"
            val req = Request.Builder()
                .url("https://torrentio.strem.fun/stream/$id.json")
                .header("User-Agent", TUA)
                .build()
            val call = VidLove.client().newCall(req)
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
            val out = mutableListOf<TEntry>()
            for (i in 0 until arr.length()) {
                val s = arr.optJSONObject(i) ?: continue
                val hash = s.optString("infoHash")
                if (hash.length < 30) continue
                val title = (s.optString("title").ifEmpty { s.optString("name") })
                    .replace("\n", " ").trim().ifEmpty { "Unknown" }
                val size = SIZE_RE.find(title)?.let { "${it.groupValues[1]} ${it.groupValues[2]}" } ?: "?"
                out.add(TEntry(title.take(90), size, seedsOf(title), hash))
            }
            AppLog.d("torrentio $id streams=${out.size}")
            out.distinctBy { it.infoHash }.sortedWith(
                compareByDescending<TEntry> { e ->
                    val t = e.name.lowercase()
                    if (t.contains("aac") || t.contains("ac3") || t.contains("eac3") || t.contains("mp3") || t.contains("opus")) 1 else 0
                }.thenByDescending { it.seeds },
            ).take(12)
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

    fun download(e: TEntry, dir: File): TorrentHandle {
        val s = ensure()
        if (!dir.exists()) dir.mkdirs()
        // sequential_download bit, see torrent_flags.hpp: pieces arrive in
        // order so the header is parseable within megabytes, not gigabytes
        s.download(Torrents.magnet(e), dir, torrent_flags_t.from_int(0x200))
        val hash = Sha1Hash(e.infoHash)
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 20000) {
            try {
                s.find(hash)?.let { return it }
            } catch (_: Exception) {
            }
            Thread.sleep(500)
        }
        throw RuntimeException("torrent handle timeout")
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
