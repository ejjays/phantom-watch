package dev.phantomwatch.player

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

// segments outlive the player, so a clip watched once starts from disk when it is swiped back to
object TrailerCache {
    private var cache: SimpleCache? = null

    private fun get(ctx: Context): SimpleCache = cache ?: SimpleCache(
        File(ctx.applicationContext.cacheDir, "trailer-cache"),
        LeastRecentlyUsedCacheEvictor(80L * 1024 * 1024),
        StandaloneDatabaseProvider(ctx.applicationContext),
    ).also { cache = it }

    fun dataSource(ctx: Context): DataSource.Factory = CacheDataSource.Factory()
        .setCache(get(ctx))
        .setUpstreamDataSourceFactory(DefaultDataSource.Factory(ctx))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
