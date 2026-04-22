package dev.phantomwatch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object Resolve {
    data class Bundle(val formats: List<VFormat>, val subs: List<Sub>, val imdb: String?)

    private const val TTL = 5 * 60_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val done = HashMap<String, Pair<Long, Bundle>>()
    private val running = HashMap<String, Deferred<Bundle>>()

    private fun key(media: Title, season: String, episode: String) =
        "${media.kind}|${media.id}|$season|$episode"

    fun prefetch(media: Title, season: String, episode: String) {
        scope.launch {
            try {
                val b = streams(media, season, episode)
                AppLog.d("prefetch ${media.title}: ${b.formats.size} formats")
            } catch (_: Exception) {
                AppLog.d("prefetch ${media.title} failed")
            }
        }
    }

    suspend fun streams(media: Title, season: String, episode: String, fresh: Boolean = false): Bundle =
        load(key(media, season, episode), fresh) { solve(media, season, episode) }

    internal suspend fun load(k: String, fresh: Boolean = false, block: suspend () -> Bundle): Bundle {
        if (!fresh) {
            mutex.withLock {
                done[k]?.let { if (System.currentTimeMillis() - it.first < TTL) return it.second }
            }
        }
        val d = mutex.withLock {
            running[k] ?: scope.async { block() }.also { running[k] = it }
        }
        val b = try {
            d.await()
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            mutex.withLock { if (running[k] === d) running.remove(k) }
            throw e
        }
        mutex.withLock {
            if (running[k] === d) {
                running.remove(k)
                if (b.formats.isNotEmpty()) {
                    done[k] = System.currentTimeMillis() to b
                    if (done.size > 32) done.clear()
                }
            }
        }
        return b
    }

    private suspend fun solve(media: Title, season: String, episode: String): Bundle {
        val (formats, subs, imdb) = Providers.directAll(media.id, media.kind, season, episode, media.title)
        return Bundle(formats, subs, imdb?.removePrefix("tt"))
    }
}
