package dev.phantomwatch.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

// taps decoded PCM as 16 kHz mono; playback is untouched
internal object AiAudio : BaseAudioProcessor() {

    @Volatile
    var sink: ((ShortArray) -> Unit)? = null

    private const val OUT_RATE = 16000
    private const val FRAME = OUT_RATE / 10

    private var encoding = C.ENCODING_PCM_16BIT
    private var channels = 2
    private var rate = 48000
    private var step = 1.0
    private var pos = 0.0
    private var prev = 0f
    private var frame = ShortArray(FRAME)
    private var framePos = 0
    private var scratch = ByteArray(0)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val e = inputAudioFormat.encoding
        if (e != C.ENCODING_PCM_16BIT && e != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        encoding = e
        channels = inputAudioFormat.channelCount.coerceAtLeast(1)
        rate = inputAudioFormat.sampleRate.coerceAtLeast(8000)
        step = rate.toDouble() / OUT_RATE
        pos = 0.0
        prev = 0f
        framePos = 0
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size > 0) {
            if (scratch.size < size) scratch = ByteArray(size)
            inputBuffer.duplicate().get(scratch, 0, size)
            inputBuffer.position(inputBuffer.position() + size)
            if (sink != null) runCatching { convert(scratch, size) }
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
            .setAudioProcessors(arrayOf(this@AiAudio))
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
    }

    private fun convert(bytes: ByteArray, size: Int) {
        val buf = ByteBuffer.wrap(bytes, 0, size).order(ByteOrder.nativeOrder())
        val frames = size / (channels * 2)
        if (frames <= 0) return
        val mono = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            for (c in 0 until channels) {
                sum += if (encoding == C.ENCODING_PCM_FLOAT) buf.float else buf.short / 32768f
            }
            mono[i] = sum / channels
        }
        var at = pos
        while (at < frames - 1) {
            val i = floor(at).toInt()
            val frac = (at - i).toFloat()
            val a = if (i < 0) prev else mono[i]
            val b = mono[i + 1]
            push(a + (b - a) * frac)
            at += step
        }
        pos = at - frames
        prev = mono[frames - 1]
    }

    private fun push(v: Float) {
        val s = (v * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        frame[framePos++] = s
        if (framePos == FRAME) {
            sink?.invoke(frame)
            frame = ShortArray(FRAME)
            framePos = 0
        }
    }
}
