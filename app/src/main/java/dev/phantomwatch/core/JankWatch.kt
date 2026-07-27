package dev.phantomwatch.core

import android.app.Activity
import android.os.Debug
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.FrameMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import dev.phantomwatch.BuildConfig
import kotlinx.coroutines.flow.collect

/**
 * Frame-time watchdog. [start] when a scroll begins, [stop] when it ends; AppLog gets one summary
 * line per scroll plus one line per second that dropped frames, with the main-thread CPU cost of
 * the worst frame so a CPU stall can be told apart from a render/GPU stall.
 */
object JankWatch {
    private const val SLOW_MS = 24L

    private val active = mutableSetOf<String>()
    private var running = false
    private var tag = ""
    private var last = 0L
    private var lastCpu = 0L
    private var since = 0L
    private var frames = 0
    private var slow = 0
    private var worst = 0L
    private var worstCpu = 0L

    fun start(t: String) {
        val first = active.isEmpty()
        active.add(t)
        if (!first) return
        tag = t
        running = true
        last = 0L
        reset()
        Choreographer.getInstance().postFrameCallback(frame)
    }

    fun stop(t: String) {
        active.remove(t)
        if (active.isNotEmpty() || !running) return
        running = false
        if (frames > 10) AppLog.d("scroll $tag ${frames + 1}f slow $slow worst ${worst}ms cpu ${worstCpu}ms${hint()}")
    }

    private fun reset() {
        since = SystemClock.uptimeMillis()
        frames = 0
        slow = 0
        worst = 0L
        worstCpu = 0L
    }

    /** A long frame with little CPU time means the main thread is waiting on the renderer. */
    private fun hint(): String = when {
        worst < SLOW_MS -> ""
        worstCpu * 4 >= worst * 3 -> " cpu-bound"
        worstCpu * 2 < worst -> " gpu-bound"
        else -> " mixed"
    }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(nanos: Long) {
            if (!running) return
            val now = SystemClock.uptimeMillis()
            val cpu = Debug.threadCpuTimeNanos()
            if (last > 0L) {
                val d = now - last
                val dc = if (cpu < 0 || lastCpu < 0) 0L else (cpu - lastCpu) / 1_000_000
                frames++
                if (d > SLOW_MS) slow++
                if (d > worst) worst = d
                if (dc > worstCpu) worstCpu = dc
            }
            last = now
            lastCpu = cpu
            if (now - since >= 1000) {
                val counts = Recomp.take()
                if (slow > 0) {
                    AppLog.d("jank $tag $slow/$frames >${SLOW_MS}ms worst ${worst}ms cpu ${worstCpu}ms${hint()}")
                    if (counts.isNotEmpty()) AppLog.d("recomp $counts")
                }
                reset()
            }
            if (running) Choreographer.getInstance().postFrameCallback(this)
        }
    }
}

@Composable
fun TrackScrollJank(tag: String, scrolling: () -> Boolean) {
    LaunchedEffect(tag) {
        snapshotFlow { scrolling() }.collect { if (it) JankWatch.start(tag) else JankWatch.stop(tag) }
    }
}

/** Counts how many times each tagged composable body actually runs, so a per-frame
 * recomposition shows up as a count rather than a guess. */
object Recomp {
    private val counts = HashMap<String, Int>()

    fun tick(tag: String) {
        if (!BuildConfig.DEBUG && !BuildConfig.LOGUI) return
        counts[tag] = (counts[tag] ?: 0) + 1
    }

    fun take(): String {
        if (counts.isEmpty()) return ""
        val out = counts.entries.joinToString(" ") { "${it.key} ${it.value}" }
        counts.clear()
        return out
    }
}

/**
 * Per-frame phase split of the worst frame each second, from the platform's FrameMetrics.
 * JankWatch only proves the main thread was busy; this says whether the time went to the
 * Compose animation callback (recomposition), measure/layout, draw, or render sync.
 */
object FramePhases {
    private const val SLOW_MS = 24.0
    private var attached = false
    private var frames = 0
    private var slow = 0
    private var since = SystemClock.uptimeMillis()
    private var worst = 0.0
    private var anim = 0.0
    private var layout = 0.0
    private var draw = 0.0
    private var sync = 0.0
    private var gpu = 0.0

    fun attach(activity: Activity) {
        if (attached || !(BuildConfig.DEBUG || BuildConfig.LOGUI)) return
        attached = true
        // Frame metrics only exist once the decor view has a ViewRootImpl.
        activity.window.decorView.post {
            runCatching {
                activity.window.addOnFrameMetricsAvailableListener({ _, m, _ ->
                    val total = ms(m, FrameMetrics.TOTAL_DURATION)
                    frames++
                    if (total > SLOW_MS) slow++
                    if (total > worst) {
                        worst = total
                        anim = ms(m, FrameMetrics.ANIMATION_DURATION)
                        layout = ms(m, FrameMetrics.LAYOUT_MEASURE_DURATION)
                        draw = ms(m, FrameMetrics.DRAW_DURATION)
                        sync = ms(m, FrameMetrics.SYNC_DURATION)
                        gpu = ms(m, FrameMetrics.COMMAND_ISSUE_DURATION)
                    }
                    val now = SystemClock.uptimeMillis()
                    if (now - since < 1000) return@addOnFrameMetricsAvailableListener
                    if (slow > 0) {
                        AppLog.d(
                            "frame $slow/$frames >24ms worst ${worst.toInt()}ms anim ${anim.toInt()}" +
                                " layout ${layout.toInt()} draw ${draw.toInt()} sync ${sync.toInt()} gpu ${gpu.toInt()}",
                        )
                    }
                    frames = 0
                    slow = 0
                    worst = 0.0
                    since = now
                }, Handler(Looper.getMainLooper()))
            }
        }
    }

    private fun ms(m: FrameMetrics, id: Int): Double =
        runCatching { m.getMetric(id) }.getOrDefault(0L) / 1_000_000.0
}
