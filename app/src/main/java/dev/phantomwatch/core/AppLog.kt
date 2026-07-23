package dev.phantomwatch.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppLog {
    private val buf = ArrayDeque<String>()
    private val fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
    var lines by mutableStateOf<List<String>>(emptyList())
        private set

    fun d(msg: String) {
        synchronized(buf) {
            buf.addLast("${fmt.format(java.time.LocalTime.now())} $msg")
            while (buf.size > 300) buf.removeFirst()
            lines = buf.toList()
        }
    }

    fun clear() {
        synchronized(buf) {
            buf.clear()
            lines = emptyList()
        }
    }
}
