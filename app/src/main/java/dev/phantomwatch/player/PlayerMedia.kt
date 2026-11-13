package dev.phantomwatch.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.ScrubbingModeParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import dev.phantomwatch.core.AppLog
import dev.phantomwatch.data.Stream
import dev.phantomwatch.data.Sub
import dev.phantomwatch.lan.LanShare
import dev.phantomwatch.subs.SubtitleCue
import dev.phantomwatch.subs.Subtitles
import dev.phantomwatch.provider.DeadHosts

internal fun trackName(f: Format): String {
    val lab = f.label ?: ""
    return if (lab.isNotEmpty()) "${f.language ?: "und"}:$lab" else (f.language ?: "und")
}

internal fun pickEmbedOverride(exo: ExoPlayer, group: TrackGroup?, idx: Int, why: String, names: List<String>): Boolean {
    if (group == null || idx !in 0 until group.length) return false
    val sel = exo.trackSelector as? DefaultTrackSelector ?: return false
    return try {
        sel.parameters = sel.buildUponParameters()
            .setOverrideForType(TrackSelectionOverride(group, listOf(idx))).build()
        AppLog.d("embed $why $idx ${names.getOrNull(idx)}")
        true
    } catch (e: Exception) {
        AppLog.d("embed pick fail ${e.message}")
        false
    }
}

internal fun configsFor(subs: List<Sub>, firstUri: Uri? = null): List<MediaItem.SubtitleConfiguration> {
    return Subtitles.englishFirst(subs).mapIndexed { i, sub ->
        val uri = if (i == 0 && firstUri != null) firstUri else Uri.parse(sub.url)
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(Subtitles.mimeOf(uri.toString()))
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
internal fun itemFor(
    url: String,
    hls: Boolean,
    cfgs: List<MediaItem.SubtitleConfiguration>,
    meta: MediaMetadata? = null,
): MediaItem {
    val low = url.lowercase()
    val b = MediaItem.Builder()
        .setUri(url)
        .setSubtitleConfigurations(cfgs)
    when {
        hls || ".m3u8" in low -> b.setMimeType(MimeTypes.APPLICATION_M3U8)
        else -> Unit
    }
    if (meta != null) b.setMediaMetadata(meta)
    return b.build()
}

internal fun looksEnglish(name: String): Boolean {
    val l = name.lowercase()
    return l.substringBefore(":").startsWith("en") || "english" in l
}

internal fun hasEnglishSubs(subs: List<Sub>): Boolean = subs.any { looksEnglish(it.label) }

// love subs drift on some encodes; only non-love subs are trusted for OS skip
internal fun hasReliableEnSubs(subs: List<Sub>): Boolean =
    subs.any { looksEnglish(it.label) && it.src != "love" }

internal fun <B : TrackSelectionParameters.Builder> B.textTrack(disabled: Boolean): B =
    apply { setTrackTypeDisabled(C.TRACK_TYPE_TEXT, disabled) }

internal class PlayStats {
    var attachAt = 0L
    var startup = 0L
    var bufAt = 0L
    var lastSeekAt = 0L
    var stalls = 0
    var stallMs = 0L
    var err = ""
}

internal fun rankEmbed(names: List<String>): Int {
    var best = -1
    var bestScore = Int.MIN_VALUE
    names.forEachIndexed { i, n ->
        if (!looksEnglish(n)) return@forEachIndexed
        val l = n.lowercase()
        var s = 0
        if ("dialog" in l || "full" in l || "complete" in l) s += 10
        if ("sign" in l || "song" in l || "s&s" in l) s -= 10
        if ("forced" in l) s -= 20
        if (s > bestScore) {
            bestScore = s
            best = i
        }
    }
    return best
}

internal fun detectLang(cues: List<SubtitleCue>): String? {
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

internal fun mediaHeaders(stream: Stream): Map<String, String> =
    mediaHeaders(stream.url, stream.headers, stream.referer, stream.auth)

internal fun mediaHeaders(
    url: String,
    headers: Map<String, String>,
    referer: String,
    auth: String? = null,
): Map<String, String> {
    val ref = when {
        url.startsWith("file") -> emptyMap()
        headers.isNotEmpty() -> headers
        referer.isNotEmpty() -> mapOf(
            "Referer" to referer,
            "Origin" to "https://${referer.substringAfter("://").substringBefore('/')}",
        )
        else -> emptyMap()
    }
    return mapOf("User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Safari/537.36") +
        ref + (auth?.let { mapOf("Authorization" to it) } ?: emptyMap())
}

// 429 is a rate limit and 403 an expired token or geo block: retrying either only holds the
// player on a spinner before the same response, so surface it and let the player switch legs
@UnstableApi
private class FailFastLoadPolicy : DefaultLoadErrorHandlingPolicy(6) {
    override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
        val code = (loadErrorInfo.exception as? HttpDataSource.InvalidResponseCodeException)?.responseCode
        if (code == 403 || code == 429) return C.TIME_UNSET
        return super.getRetryDelayMsFor(loadErrorInfo)
    }
}

// a 403 or a 429 is about this url's token or rate window, not the host: banning the host there is
// how one expired segment hid a healthy CDN. Only expired DNS is worth a ban.
internal fun noteDeadHost(chain: String, code: Int?, url: String) {
    when {
        chain.contains("UnknownHostException") -> {
            DeadHosts.cooldown(url, 10)
            AppLog.d("player dead host ${DeadHosts.hostOf(url)}")
        }
        code == 429 -> AppLog.d("player rate limited ${DeadHosts.hostOf(url)}")
        code == 403 -> AppLog.d("player refused ${DeadHosts.hostOf(url)}")
    }
}

@OptIn(UnstableApi::class)
internal fun buildMediaFactory(
    context: android.content.Context,
    stream: Stream,
    headers: Map<String, String>,
): DefaultMediaSourceFactory {
    val retry = FailFastLoadPolicy()
    if (stream.url.startsWith("file")) {
        return DefaultMediaSourceFactory(
            DataSource.Factory {
                val inner = FileDataSource()
                object : DataSource {
                    override fun open(dataSpec: DataSpec): Long {
                        inner.open(dataSpec)
                        return C.LENGTH_UNSET.toLong()
                    }

                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                        inner.read(buffer, offset, length)

                    override fun getUri(): android.net.Uri? = inner.uri

                    override fun getResponseHeaders(): Map<String, List<String>> = inner.responseHeaders

                    override fun close() = inner.close()

                    override fun addTransferListener(transferListener: TransferListener) =
                        inner.addTransferListener(transferListener)
                }
            },
        ).setLoadErrorHandlingPolicy(retry)
    }
    val http = DefaultHttpDataSource.Factory()
        .setUserAgent(headers.getValue("User-Agent"))
        .setDefaultRequestProperties(headers)
        .setConnectTimeoutMs(15_000)
        // a stalled segment socket is retried at the read timeout; 30s matched the watchdog, so
        // every hang cost a full half minute before the loader even tried again
        .setReadTimeoutMs(15_000)
    // cached provider subs arrive as file:// while the stream is https: an http-only factory
    // loads the track but never the cues
    return DefaultMediaSourceFactory(androidx.media3.datasource.DefaultDataSource.Factory(context, http))
        .setLoadErrorHandlingPolicy(retry)
}

@OptIn(UnstableApi::class)
internal fun sourceFor(
    context: android.content.Context,
    stream: Stream,
    subs: List<Sub>,
    firstUri: Uri?,
    meta: MediaMetadata?,
): MediaSource {
    LanShare.onStream(stream, subs)
    return buildMediaFactory(context, stream, mediaHeaders(stream))
        .createMediaSource(itemFor(stream.url, stream.isHls, configsFor(subs, firstUri), meta))
}

@OptIn(UnstableApi::class)
internal fun buildPreviewPlayer(context: android.content.Context, stream: Stream): ExoPlayer {
    val headers = mediaHeaders(stream)
    val selector = DefaultTrackSelector(context).apply {
        parameters = buildUponParameters()
            .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }
    val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(2000, 5000, 1000, 1000)
        .build()
    return ExoPlayer.Builder(context)
        .setTrackSelector(selector)
        .setMediaSourceFactory(buildMediaFactory(context, stream, headers))
        .setLoadControl(loadControl)
        .build()
        .apply {
            volume = 0f
            setScrubbingModeParameters(ScrubbingModeParameters.Builder().setUseDecodeOnlyFlag(false).build())
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    AppLog.d("preview err ${error.errorCodeName}")
                }
            })
            setMediaItem(itemFor(stream.url, stream.isHls, emptyList()))
            prepare()
        }
}
