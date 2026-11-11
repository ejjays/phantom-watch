package dev.phantomwatch.player

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dev.phantomwatch.core.AppLog
import java.io.File
import kotlin.math.abs

// segments outlive the player, so a clip watched once starts from disk when it is swiped back to
object TrailerCache {
    private var cache: SimpleCache? = null

    // a fresh player estimates 1 mbps, so the playlist it opens is the one worth warming
    private const val START_BITRATE = 1_000_000
    private const val HEAD = 64 * 1024

    // the prefetch builds this on an IO thread while the player builds it on main
    @Synchronized
    private fun get(ctx: Context): SimpleCache = cache ?: SimpleCache(
        File(ctx.applicationContext.cacheDir, "trailer-cache"),
        LeastRecentlyUsedCacheEvictor(80L * 1024 * 1024),
        StandaloneDatabaseProvider(ctx.applicationContext),
    ).also { cache = it }

    fun dataSource(ctx: Context): DataSource.Factory = CacheDataSource.Factory()
        .setCache(get(ctx))
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    // blocking: the playlist reads are the ones a player repeats on every open, so warm just those
    fun prefetch(ctx: Context, url: String) {
        val t0 = System.currentTimeMillis()
        runCatching {
            val ds = dataSource(ctx).createDataSource()
            try {
                val head = String(pull(ds, url) ?: return, Charsets.UTF_8)
                if (!head.startsWith("#EXTM3U")) return
                val variant = variants(head).minByOrNull { abs(it.first - START_BITRATE) }?.second ?: return
                pull(ds, resolve(url, variant))
                AppLog.d("prefetch 2 lists ${System.currentTimeMillis() - t0}ms")
            } finally {
                runCatching { ds.close() }
            }
        }.onFailure { AppLog.d("prefetch failed ${it.message}") }
    }

    private fun pull(ds: DataSource, url: String): ByteArray? {
        ds.open(DataSpec(Uri.parse(url)))
        return try {
            val buf = ByteArray(HEAD)
            var n = 0
            while (n < HEAD) {
                val r = ds.read(buf, n, HEAD - n)
                if (r == -1) break
                n += r
            }
            buf.copyOf(n)
        } finally {
            ds.close()
        }
    }

    private fun variants(master: String): List<Pair<Int, String>> {
        val out = mutableListOf<Pair<Int, String>>()
        var bw = 0
        for (raw in master.lineSequence()) {
            val line = raw.trim()
            when {
                line.startsWith("#EXT-X-STREAM-INF:") ->
                    bw = Regex("BANDWIDTH=(\\d+)").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                line.isEmpty() || line.startsWith("#") -> Unit
                else -> out += bw to line
            }
        }
        return out
    }

    private fun resolve(base: String, ref: String): String = runCatching {
        java.net.URI(base).resolve(ref).toString()
    }.getOrDefault(ref)
}
