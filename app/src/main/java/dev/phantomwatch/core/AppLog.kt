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

    // a plain buffer, not a state: every thread logs, and a state written off the main thread
    // conflicts with any composition that logs too (the trailer logs inside remember)
    fun snapshot(): List<String> = synchronized(buf) { buf.toList() }

    // debug only: a file lets adb read the buffer without opening the log sheet
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
