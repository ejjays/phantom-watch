package dev.phantomwatch.core

import android.content.Context
import android.util.Log
import dev.phantomwatch.BuildConfig
import java.time.LocalDateTime

object CrashReport {
    private const val FILE = "last_crash.txt"

    fun install(ctx: Context) {
        val app = ctx.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { write(app, t, e) }
            prev?.uncaughtException(t, e)
        }
    }

    private fun write(ctx: Context, t: Thread, e: Throwable) {
        val text = buildString {
            appendLine("when: ${LocalDateTime.now()}")
            appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} android ${android.os.Build.VERSION.RELEASE}")
            appendLine("thread: ${t.name}")
            appendLine()
            append(Log.getStackTraceString(e).ifEmpty { e.stackTraceToString() })
        }
        ctx.filesDir.resolve(FILE).writeText(text.take(20000))
    }

    fun pending(ctx: Context): String? =
        ctx.filesDir.resolve(FILE).takeIf { it.exists() && it.length() > 0 }?.readText()

    fun clear(ctx: Context) {
        ctx.filesDir.resolve(FILE).delete()
    }
}
