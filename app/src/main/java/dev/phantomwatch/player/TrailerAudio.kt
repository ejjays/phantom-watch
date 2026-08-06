package dev.phantomwatch.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import dev.phantomwatch.core.AppLog
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

// taps decoded PCM for the detail wall's spectrum; volume is a post-decode gain, so this works muted
internal class TrailerAudio : BaseAudioProcessor() {

    @Volatile
    var level = 0f
        private set

    @Volatile
    var bands: FloatArray = FloatArray(BANDS)
        private set

    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private var edges = IntArray(BANDS + 1)
    private var failed = false
    private var scratch = ByteArray(0)
    private val ring = FloatArray(FFT)
    private var ringPos = 0
    private var frames = 0
    private var gain = 0.01f
    private val re = FloatArray(FFT)
    private val im = FloatArray(FFT)
    private val window = FloatArray(FFT) { 0.5f - 0.5f * cos(2f * PI.toFloat() * it / (FFT - 1)) }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val e = inputAudioFormat.encoding
        // passthrough and exotic depths must not touch the chain, so stay inactive for them
        if (e != C.ENCODING_PCM_16BIT && e != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        encoding = e
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        val rate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        edges = IntArray(BANDS + 1) { i ->
            val f = LO_HZ * Math.pow(HI_HZ / LO_HZ, i.toDouble() / BANDS)
            (f * FFT / rate).toInt().coerceIn(1, FFT / 2 - 1)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        // the pipeline can hand our own output buffer back as input, so stage the bytes before
        // replaceOutputBuffer() clears it: a self-copy throws IllegalArgumentException
        if (size > 0) {
            if (scratch.size < size) scratch = ByteArray(size)
            inputBuffer.duplicate().get(scratch, 0, size)
            if (!failed) {
                runCatching { measure(scratch, size) }.onFailure {
                    failed = true
                    AppLog.d("trailer tap fail ${it.stackTraceToString().take(900)}")
                }
            }
            inputBuffer.position(inputBuffer.position() + size)
        }
        val out = replaceOutputBuffer(size)
        out.put(scratch, 0, size)
        out.flip()
    }

    fun renderers(ctx: Context): RenderersFactory = object : DefaultRenderersFactory(ctx) {
        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ): AudioSink = DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf(this@TrailerAudio))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    private fun measure(bytes: ByteArray, size: Int) {
        val ch = channels
        val buf = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.nativeOrder())
        var sum = 0.0
        var n = 0
        if (encoding == C.ENCODING_PCM_FLOAT) {
            val f = buf.asFloatBuffer()
            while (f.remaining() >= ch) {
                var acc = 0f
                for (c in 0 until ch) acc += f.get()
                val v = acc / ch
                sum += (v * v).toDouble()
                n++
                push(v)
            }
        } else {
            val s = buf.asShortBuffer()
            while (s.remaining() >= ch) {
                var acc = 0f
                for (c in 0 until ch) acc += s.get() / 32768f
                val v = acc / ch
                sum += (v * v).toDouble()
                n++
                push(v)
            }
        }
        if (n > 0) {
            val db = 20 * log10(sqrt(sum / n).coerceAtLeast(1e-6))
            val target = ((db + 55.0) / 55.0).toFloat().coerceIn(0f, 1f)
            level = if (target > level) target else level * 0.82f + target * 0.18f
        }
        if (++frames >= 4) {
            frames = 0
            analyze()
        }
    }

    private fun push(v: Float) {
        ring[ringPos] = v
        ringPos = (ringPos + 1) and (FFT - 1)
    }

    private fun analyze() {
        for (i in 0 until FFT) {
            re[i] = ring[(ringPos + i) and (FFT - 1)] * window[i]
            im[i] = 0f
        }
        fft(re, im)
        val mags = FloatArray(BANDS)
        var peak = 0f
        for (b in 0 until BANDS) {
            val lo = edges[b].coerceIn(1, FFT / 2 - 1)
            val hi = edges[b + 1].coerceIn(lo + 1, FFT / 2)
            var m = 0f
            for (k in lo until hi) m += sqrt(re[k] * re[k] + im[k] * im[k])
            val v = m / (hi - lo)
            mags[b] = v
            if (v > peak) peak = v
        }
        gain = maxOf(peak, gain * 0.985f).coerceAtLeast(1e-6f)
        val prev = bands
        val out = FloatArray(BANDS) { i ->
            val v = (mags[i] / (gain * 1.15f)).coerceIn(0f, 1f)
            if (v > prev[i]) prev[i] + (v - prev[i]) * 0.35f else prev[i] * 0.80f + v * 0.20f
        }
        repeat(2) {
            val src = out.copyOf()
            for (i in 0 until BANDS) {
                val l = src[(i - 1).coerceAtLeast(0)]
                val r = src[(i + 1).coerceAtMost(BANDS - 1)]
                out[i] = l * 0.25f + src[i] * 0.5f + r * 0.25f
            }
        }
        bands = out
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j or bit
            if (i < j) {
                var t = real[i]
                real[i] = real[j]
                real[j] = t
                t = imag[i]
                imag[i] = imag[j]
                imag[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2.0 * PI / len
            val wr = cos(ang).toFloat()
            val wi = sin(ang).toFloat()
            var i = 0
            val half = len shr 1
            while (i < n) {
                var cr = 1f
                var ci = 0f
                for (k in 0 until half) {
                    val a = i + k
                    val b = a + half
                    val vr = real[b] * cr - imag[b] * ci
                    val vi = real[b] * ci + imag[b] * cr
                    real[b] = real[a] - vr
                    imag[b] = imag[a] - vi
                    real[a] += vr
                    imag[a] += vi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }

    private companion object {
        const val FFT = 1024
        const val BANDS = 30
        const val LO_HZ = 55.0
        const val HI_HZ = 9000.0
    }
}
