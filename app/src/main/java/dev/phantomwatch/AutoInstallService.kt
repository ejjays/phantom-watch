package dev.phantomwatch

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.phantomwatch.core.AppLog

/** The clicker may only act inside the short window after this app commits its own install session. */
object AutoInstall {
    @Volatile
    private var until = 0L

    @Volatile
    private var expectedLabel = ""

    @Volatile
    private var expectedSession = -1

    @Volatile
    var clicked = false
        private set

    @Volatile
    var svc: AutoInstallService? = null

    fun arm(sessionId: Int, label: String) {
        expectedSession = sessionId
        expectedLabel = label.trim().lowercase()
        clicked = false
        until = SystemClock.uptimeMillis() + 120_000
        AppLog.d("autoinstall armed")
        svc?.kick()
    }

    fun disarm() {
        until = 0L
        expectedLabel = ""
        expectedSession = -1
        AppLog.d("autoinstall disarmed")
    }

    fun expectedLabel(): String = expectedLabel

    fun expectedSession(): Int = expectedSession

    fun markClicked() {
        clicked = true
    }

    fun armed(): Boolean = until > SystemClock.uptimeMillis()
}

class AutoInstallService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    // A stale "Done" page from the last run is already on screen, so it fires no event: poll it.
    private val poll = object : Runnable {
        override fun run() {
            if (!AutoInstall.armed()) return
            rootInActiveWindow?.let { scan(it.packageName?.toString().orEmpty(), it) }
            handler.postDelayed(this, 500)
        }
    }

    fun kick() {
        handler.removeCallbacks(poll)
        handler.post(poll)
    }

    override fun onServiceConnected() {
        AutoInstall.svc = this
    }

    override fun onDestroy() {
        if (AutoInstall.svc === this) AutoInstall.svc = null
        handler.removeCallbacks(poll)
        super.onDestroy()
    }

    // Every node lookup crosses into the other app's main thread, so the package is filtered from the
    // event (free) and matches use one framework text search instead of walking the tree.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (seen.add(packageName)) AppLog.d("a11y evt $packageName")
        if (!AutoInstall.armed()) return
        val root = rootInActiveWindow ?: return
        scan(packageName, root)
    }

    private fun scan(packageName: String, root: AccessibilityNodeInfo) {
        if (packageName !in INSTALLERS && !packageName.startsWith("com.huawei")) return
        if (packageName in INSTALLERS &&
            AutoInstall.expectedSession() !in packageManager.packageInstaller.mySessions.map { it.sessionId }
        ) {
            AutoInstall.disarm()
            return
        }
        val target = AutoInstall.expectedLabel()
        if (target.isEmpty() || find(root, target).none { label(it) == target }) return
        if (FINISHED.any { find(root, it).isNotEmpty() }) {
            AutoInstall.disarm()
            return
        }
        click(root, BUTTONS, "install", packageName)
    }

    private fun click(root: AccessibilityNodeInfo, labels: Set<String>, tag: String, pkg: String) {
        val hit = labels.firstNotNullOfOrNull { text ->
            find(root, text).firstOrNull { node ->
                label(node) == text && (node.isClickable || node.parent?.isClickable == true)
            }
        } ?: return
        var target: AccessibilityNodeInfo? = hit
        while (target != null && !target.isClickable) target = target.parent
        if (target != null) {
            val sessions = packageManager.packageInstaller.mySessions.map { it.sessionId }
            if (!AutoInstall.armed() || AutoInstall.expectedSession() !in sessions) {
                AutoInstall.disarm()
                return
            }
            AppLog.d("install $tag click ${label(target)} in $pkg")
            if (tag == "install") AutoInstall.markClicked()
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
    }

    override fun onInterrupt() = Unit

    private fun label(node: AccessibilityNodeInfo): String =
        (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase().orEmpty()

    private fun find(root: AccessibilityNodeInfo, text: String): List<AccessibilityNodeInfo> =
        runCatching { root.findAccessibilityNodeInfosByText(text) }.getOrNull().orEmpty()

    private companion object {
        val seen = mutableSetOf<String>()
        val INSTALLERS = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        val BUTTONS = setOf("install", "update")
        val FINISHED = setOf("installation successful", "installed from", "you may also like")
    }
}
