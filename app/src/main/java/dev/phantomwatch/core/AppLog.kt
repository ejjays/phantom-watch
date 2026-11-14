package dev.phantomwatch.core

import android.content.Context
import dev.phantomwatch.BuildConfig
import java.io.File
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object AppLog {
    private val buf = ArrayDeque<String>()
    private val fmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
    private var sink: File? = null

    // plain buffer not state, every thread logs, state off main thread
    // conflicts with composition that logs too, trailer logs inside remember
    fun snapshot(): List<String> = synchronized(buf) { buf.toList() }

    // debug only, file lets adb read buffer without opening log sheet
    fun init(ctx: Context) {
        if (!BuildConfig.DEBUG) return
        sink = File(ctx.filesDir, "applog.txt").also { runCatching { it.writeText("") } }
    }

    fun d(msg: String) {
        synchronized(buf) {
            val line = "${fmt.format(LocalTime.now())} $msg"
            buf.addLast(line)
            while (buf.size > 300) buf.removeFirst()
            sink?.let { f -> runCatching { f.appendText("$line\n") } }
        }
    }

    fun clear() {
        synchronized(buf) {
            buf.clear()
            sink?.let { f -> runCatching { f.writeText("") } }
        }
    }
}
