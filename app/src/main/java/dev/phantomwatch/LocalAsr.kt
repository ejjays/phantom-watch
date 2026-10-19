package dev.phantomwatch

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.media3.common.Format
import androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist.Rendition
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File
import java.nio.ByteOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

object LocalAsr {
    private const val CHUNK_SEC = 30
    private const val RATE = 16000

    private var recognizer: OfflineRecognizer? = null

    fun reset() {
        try {
            recognizer?.release()
        } catch (_: Exception) {
        }
        recognizer = null
    }

    private fun rec(modelDir: File): OfflineRecognizer {
        recognizer?.let { return it }
        val cfg = OfflineRecognizerConfig(
            featConfig = FeatureConfig(RATE, 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(),
                senseVoice = OfflineSenseVoiceModelConfig(
                    model = File(modelDir, "model.int8.onnx").absolutePath,
                    language = "auto",
                    useInverseTextNormalization = true,
                ),
                tokens = File(modelDir, "tokens.txt").absolutePath,
                numThreads = 2,
                provider = "cpu",
            ),
        )
        val r = OfflineRecognizer(null, cfg)
        recognizer = r
        return r
    }

    private fun resolveUrl(base: String, rel: String): String {
        if (rel.startsWith("http")) return rel
        return try {
            java.net.URI(base).resolve(rel).toString()
        } catch (_: Exception) {
            base.substringBeforeLast('/') + "/" + rel.removePrefix("/")
        }
    }

    private suspend fun resolveAudio(
        source: String,
        headers: Map<String, String>,
        cacheDir: File,
        paused: () -> Boolean,
    ): File? {
        if (source.startsWith("file")) {
            val p = try {
                android.net.Uri.parse(source).path
            } catch (_: Exception) {
                null
            }
            val f = if (p != null) File(p) else null
            if (f != null && f.exists()) {
                AppLog.d("asr local file ${f.length() / 1048576}MB")
                return f
            }
            AppLog.d("asr file missing")
            return null
        }
        val body: String
        try {
            val req = okhttp3.Request.Builder().url(source).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            val call = modelHttp.newCall(req)
            call.timeout().deadline(20, java.util.concurrent.TimeUnit.SECONDS)
            body = call.execute().use { res ->
                if (res.code != 200) throw RuntimeException("http ${res.code}")
                res.body?.string() ?: throw RuntimeException("empty")
            }
        } catch (e: Exception) {
            AppLog.d("asr probe err ${e.message?.take(140)}")
            return null
        }
        if (!body.contains("#EXTM3U")) {
            AppLog.d("asr probe not m3u8 (${body.take(60)})")
            return null
        }
        val pl = try {
            HlsPlaylistParser().parse(android.net.Uri.parse(source), body.byteInputStream())
        } catch (e: Exception) {
            AppLog.d("asr playlist parse fail ${e.message?.take(120)}")
            return null
        }
        return when (pl) {
            is HlsMultivariantPlaylist -> {
                val pick = pickKoreanAudio(pl)
                val pickUrl = pick?.url?.toString()
                if (pickUrl.isNullOrEmpty()) {
                    AppLog.d("asr no audio rendition")
                    return null
                }
                AppLog.d("asr rendition url=" + pickUrl.take(80))
                fetchMediaPlaylist(
                    resolveUrl(source, pickUrl), headers, cacheDir, paused,
                )
            }
            is HlsMediaPlaylist -> {
                AppLog.d("asr direct media playlist")
                fetchMediaPlaylist(source, headers, cacheDir, paused)
            }
            else -> {
                AppLog.d("asr unknown playlist")
                null
            }
        }
    }

    private fun pickKoreanAudio(pl: HlsMultivariantPlaylist): Rendition? {
        for (r in pl.audios) {
            val hay = ((r.url?.toString() ?: "") + " " + (r.name ?: "")).lowercase()
            AppLog.d("asr group url=" + (r.url?.toString() ?: "?").take(60))
            if (hay.contains("/ko") || hay.contains("kor") || hay.contains("korean") ||
                hay.contains("lang=ko") || hay.endsWith("/ko")
            ) {
                AppLog.d("asr ko match")
                return r
            }
        }
        return pl.audios.firstOrNull()
    }

    private suspend fun fetchMediaPlaylist(
        mediaUrl: String,
        headers: Map<String, String>,
        cacheDir: File,
        paused: () -> Boolean,
    ): File? {
        val dir = File(cacheDir, "asr").apply { mkdirs() }
        val out = File(dir, "ko-audio.ts")
        out.delete()
        val queued = mutableSetOf<String>()
        var got = 0
        var lastLog = 0
        try {
            while (true) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val body = try {
                    val req = okhttp3.Request.Builder().url(mediaUrl).apply {
                        headers.forEach { (k, v) -> header(k, v) }
                    }.build()
                    val call = modelHttp.newCall(req)
                    call.timeout().deadline(20, java.util.concurrent.TimeUnit.SECONDS)
                    call.execute().use { res ->
                        if (res.code != 200) throw RuntimeException("http ${res.code}")
                        res.body?.string() ?: throw RuntimeException("empty")
                    }
                } catch (e: Exception) {
                    AppLog.d("asr playlist fetch err ${e.message?.take(120)} got=$got")
                    return if (got > 0) out else null
                }
                val pl = try {
                    HlsPlaylistParser().parse(
                        android.net.Uri.parse(mediaUrl), body.byteInputStream(),
                    ) as? HlsMediaPlaylist
                } catch (e: Exception) {
                    AppLog.d("asr media parse fail got=$got")
                    return if (got > 0) out else null
                }
                if (pl == null) return if (got > 0) out else null
                var encKey: ByteArray? = null
                var encKeyUri: String? = null
                for ((si, seg) in pl.segments.withIndex()) {
                    val u = resolveUrl(mediaUrl, seg.url)
                    if (!queued.add(u)) continue
                    while (paused()) {
                        kotlinx.coroutines.delay(500)
                        kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    }
                    val init = try {
                        seg.initializationSegment?.url
                    } catch (_: Exception) {
                        null
                    }
                    if (init != null && init.isNotEmpty() && queued.add("init:$init")) {
                        if (!fetchBytes(resolveUrl(mediaUrl, init), headers, out)) {
                            AppLog.d("asr init seg fail")
                            return if (got > 0) out else null
                        }
                    }
                    val keyUri = seg.fullSegmentEncryptionKeyUri
                    if (keyUri != null) {
                        val abs = resolveUrl(mediaUrl, keyUri)
                        if (encKeyUri != abs) {
                            encKey = fetchRaw(abs, headers)
                            encKeyUri = abs
                            AppLog.d("asr enc key=" + (encKey?.size ?: -1) + "B")
                        }
                        val key = encKey
                        val plain = fetchRaw(u, headers)
                        if (plain == null || key == null) {
                            AppLog.d("asr enc seg fail got=$got")
                            return if (got > 0) out else null
                        }
                        val dec = decryptAes(key, ivFor(seg.encryptionIV, pl.mediaSequence + si), plain)
                        if (dec == null) return if (got > 0) out else null
                        java.io.FileOutputStream(out, true).use { it.write(dec) }
                        if (got == 0) AppLog.d("asr enc=cbc iv=" + (seg.encryptionIV ?: "seq$si"))
                    } else {
                        if (got == 0) AppLog.d("asr unencrypted first=" + u.takeLast(44))
                        if (!fetchBytes(u, headers, out)) {
                            AppLog.d("asr seg fail got=$got")
                            return if (got > 0) out else null
                        }
                    }
                    got += 1
                    if (got - lastLog >= 25) {
                        lastLog = got
                        AppLog.d("asr segs " + got + "/" + pl.segments.size)
                    }
                }
                if (pl.hasEndTag) break
                if (out.length() > 600L * 1048576) {
                    AppLog.d("asr live cap hit")
                    break
                }
                kotlinx.coroutines.delay((pl.targetDurationUs / 1000).coerceAtLeast(2000))
            }
            AppLog.d("asr segs done got=" + got + " size=" + (out.length() / 1048576) + "MB")
            return if (got > 0) out else null
        } catch (e: Exception) {
            AppLog.d("asr segs fail ${e.message?.take(120)}")
            return if (got > 0) out else null
        }
    }

    private fun fetchBytes(url: String, headers: Map<String, String>, out: File): Boolean {
        return try {
            val req = okhttp3.Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            modelHttp.newCall(req).execute().use { res ->
                if (res.code != 200) return false
                val body = res.body ?: return false
                java.io.FileOutputStream(out, true).use { o -> body.byteStream().copyTo(o) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fetchRaw(url: String, headers: Map<String, String>): ByteArray? {
        return try {
            val req = okhttp3.Request.Builder().url(url).apply {
                headers.forEach { (k, v) -> header(k, v) }
            }.build()
            modelHttp.newCall(req).execute().use { res ->
                if (res.code != 200) null else res.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun ivFor(raw: String?, seq: Long): ByteArray {
        val hx = raw?.removePrefix("0x")?.removePrefix("0X")
        if (hx != null && hx.isNotEmpty() && hx.length % 2 == 0 &&
            hx.all { it in "0123456789abcdefABCDEF" }
        ) {
            val padded = hx.padStart(32, '0').takeLast(32)
            val b = ByteArray(16)
            for (i in 0 until 16) b[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            return b
        }
        val b = ByteArray(16)
        var v = seq
        for (i in 15 downTo 0) {
            b[i] = (v and 0xFF).toByte()
            v = v ushr 8
        }
        return b
    }

    private fun decryptAes(key: ByteArray, iv: ByteArray, data: ByteArray): ByteArray? {
        for (mode in listOf("AES/CBC/PKCS5Padding", "AES/CBC/NoPadding")) {
            try {
                val c = javax.crypto.Cipher.getInstance(mode)
                c.init(
                    javax.crypto.Cipher.DECRYPT_MODE,
                    javax.crypto.spec.SecretKeySpec(key, "AES"),
                    javax.crypto.spec.IvParameterSpec(iv),
                )
                return c.doFinal(data)
            } catch (_: Exception) {
            }
        }
        AppLog.d("asr aes fail")
        return null
    }

    suspend fun transcribe(
        context: android.content.Context,
        source: String,
        headers: Map<String, String>,
        modelDir: File,
        cacheDir: File,
        paused: () -> Boolean,
        progress: (Float) -> Unit,
        onLines: suspend (List<VidLove.Cue>, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val ex = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            val local = resolveAudio(source, headers, cacheDir, paused)
            if (local == null) {
                AppLog.d("asr no audio source")
                return@withContext
            }
            val headB = ByteArray(16)
            val headN = java.io.FileInputStream(local).use { it.read(headB) }
            AppLog.d(
                "asr file head=" + headB.take(headN).joinToString("") { "%02x".format(it) } +
                    " size=" + (local.length() / 1048576) + "MB",
            )
            ex.setDataSource(local.absolutePath)
            var audioIdx = -1
            var pick: MediaFormat? = null
            for (i in 0 until ex.trackCount) {
                val f = ex.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                if (audioIdx < 0) {
                    audioIdx = i
                    pick = f
                }
                if ((f.getString("language") ?: "").startsWith("ko")) {
                    audioIdx = i
                    pick = f
                    break
                }
            }
            val fmt = pick
            if (audioIdx < 0 || fmt == null) {
                AppLog.d("asr no audio track")
                return@withContext
            }
            ex.selectTrack(audioIdx)
            val mime = fmt.getString(MediaFormat.KEY_MIME)!!
            val durationUs = if (fmt.containsKey(MediaFormat.KEY_DURATION)) {
                fmt.getLong(MediaFormat.KEY_DURATION)
            } else {
                0L
            }
            AppLog.d("asr track $mime dur=${durationUs / 1_000_000}s lang=${fmt.getString("language") ?: "?"}")
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(fmt, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var inRate = 0
            var inCh = 0
            val pieces = mutableListOf<FloatArray>()
            var rawN = 0
            var chunkIdx = 0
            var sawEos = false
            var pcmAbs = 0.0
            var pcmN = 0

            suspend fun flushChunk() {
                if (rawN == 0) return
                val raw = FloatArray(rawN)
                var at = 0
                for (p in pieces) {
                    p.copyInto(raw, at)
                    at += p.size
                }
                pieces.clear()
                rawN = 0
                val samples = resample16k(raw, inRate)
                val res = try {
                    val r = rec(modelDir)
                    val stream = r.createStream()
                    stream.acceptWaveform(samples, samples.size)
                    r.decode(stream)
                    r.getResult(stream)
                } catch (e: Exception) {
                    AppLog.d("asr decode fail ${e.message?.take(120)}")
                    return
                }
                val (cues, lang) = toCues(res, (chunkIdx * CHUNK_SEC).toDouble())
                val ev = res.event.replace("<|", "").replace("|>", "")
                val rms = if (pcmN > 0) (pcmAbs / pcmN * 1000).toInt() else 0
                AppLog.d(
                    "asr chunk=$chunkIdx lang=$lang ev=$ev rms=$rms lines=${cues.size} '" +
                        res.text.take(40) + "'",
                )
                pcmAbs = 0.0
                pcmN = 0
                val junk = ev == "BGM" || ev == "Applause" || ev == "Laughter" ||
                    ev == "Cry" || ev == "Sneeze" || ev == "Breath" || ev == "Cough"
                if (junk && res.text.length < 40) {
                    AppLog.d("asr skip $ev")
                } else if (cues.isNotEmpty()) {
                    onLines(cues, lang)
                }
                chunkIdx += 1
            }

            while (!sawEos) {
                while (paused()) {
                    delay(500)
                    ensureActive()
                }
                val inIdx = codec.dequeueInputBuffer(10_000)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, ex.sampleTime, 0)
                        ex.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                if (outIdx >= 0) {
                    if (info.size > 0) {
                        val of = codec.outputFormat
                        val sr = of.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        val ch = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        if (inRate == 0) {
                            inRate = sr
                            inCh = ch
                            AppLog.d("asr pcm ${sr}Hz ${ch}ch")
                        }
                        val ob = codec.getOutputBuffer(outIdx)!!
                        ob.position(info.offset)
                        ob.limit(info.offset + info.size)
                        val shorts = ShortArray(info.size / 2)
                        ob.order(ByteOrder.nativeOrder()).asShortBuffer().get(shorts)
                        val mono = FloatArray(shorts.size / inCh)
                        for (i in mono.indices) {
                            var s = 0f
                            for (c in 0 until inCh) s += shorts[i * inCh + c].toInt().toFloat()
                            mono[i] = s / inCh / 32768f
                        }
                        for (f in mono) pcmAbs += if (f < 0) -f else f
                        pcmN += mono.size
                        pieces += mono
                        rawN += mono.size
                        if (rawN >= inRate * CHUNK_SEC) flushChunk()
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) sawEos = true
                }
                if (durationUs > 0 && ex.sampleTime > 0) {
                    progress((ex.sampleTime.toFloat() / durationUs).coerceIn(0f, 1f))
                }
            }
            flushChunk()
            AppLog.d("asr done chunks=${chunkIdx + 1}")
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (_: Exception) {
            }
            ex.release()
        }
    }

    private fun resample16k(src: FloatArray, srcRate: Int): FloatArray {
        if (srcRate == RATE) return src
        val n = src.size
        val outN = (n.toLong() * RATE / srcRate).toInt().coerceAtLeast(1)
        val out = FloatArray(outN)
        val step = srcRate.toFloat() / RATE
        for (i in 0 until outN) {
            val pos = i * step
            val i0 = pos.toInt().coerceIn(0, n - 1)
            val i1 = (i0 + 1).coerceIn(0, n - 1)
            val f = pos - i0
            out[i] = src[i0] + (src[i1] - src[i0]) * f
        }
        return out
    }

    private fun toCues(res: OfflineRecognizerResult, baseSec: Double): Pair<List<VidLove.Cue>, String> {
        val lang = res.lang.replace("<|", "").replace("|>", "").ifEmpty { "auto" }
        val toks = res.tokens
        val ts = res.timestamps
        if (toks.isEmpty()) return emptyList<VidLove.Cue>() to lang
        val cues = mutableListOf<VidLove.Cue>()
        val sb = StringBuilder()
        var start = -1f
        var last = -1f
        fun close(endT: Float) {
            val body = sb.toString().trim()
            if (body.isNotEmpty() && start >= 0f) {
                val s = ((baseSec + start) * 1000).toLong()
                val e = ((baseSec + endT + 0.4) * 1000).toLong()
                if (e > s) cues.add(VidLove.Cue(s, e, body))
            }
            sb.setLength(0)
            start = -1f
        }
        for (i in toks.indices) {
            val t = toks[i]
            if (t.startsWith("<|")) continue
            val sec = if (i < ts.size) ts[i] else -1f
            if (sec < 0f) {
                sb.append(t)
                continue
            }
            if (start >= 0f && (sec - last > 1.0f || sb.length >= 40 || sec - start > 8f)) close(last)
            if (start < 0f) start = sec
            sb.append(t)
            last = sec
        }
        if (start >= 0f) close(last)
        if (cues.isEmpty() && sb.isNotEmpty()) {
            val s = (baseSec * 1000).toLong()
            cues.add(VidLove.Cue(s, s + CHUNK_SEC * 1000, sb.toString().trim()))
        }
        return cues to lang
    }
}