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
    private var until = 0L

    /** Set once we click Install in this session, so its own "installed" page is never clicked. */
    var clicked = false
        private set

    @Volatile
    var svc: AutoInstallService? = null

    fun arm() {
        until = SystemClock.uptimeMillis() + 120_000
        clicked = false
        AppLog.d("autoinstall armed")
        svc?.kick()
    }

    fun disarm() {
        until = 0L
        AppLog.d("autoinstall disarmed")
    }

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
        // Huawei shows an AppGallery "security check" page before the installer: only touch it while it shows our app
        if (packageName !in INSTALLERS && find(root, getString(R.string.app_name)).isEmpty()) return
        // Its "installed / you may also like" page also shows our name, and its cards have install buttons
        if (FINISHED.any { find(root, it).isNotEmpty() }) {
            // Ours is done: leave its page alone. A page left over from the last run has no install
            // button left, so it is cleared before this session's prompt would appear behind it.
            val stale = !AutoInstall.clicked && BUTTONS.none { find(root, it).isNotEmpty() }
            if (stale) click(root, DONE, "done", packageName) else AutoInstall.disarm()
            return
        }
        click(root, BUTTONS, "install", packageName)
    }

    private fun click(root: AccessibilityNodeInfo, labels: Set<String>, tag: String, pkg: String) {
        val hit = labels.firstNotNullOfOrNull { text -> find(root, text).firstOrNull { label(it) == text } } ?: return
        var target: AccessibilityNodeInfo? = hit
        while (target != null && !target.isClickable) target = target.parent
        if (target != null) {
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
        val BUTTONS = setOf("install", "update", "continue", "install anyway", "update anyway")
        val FINISHED = setOf("installation successful", "installed from", "you may also like")
        val DONE = setOf("done")
    }
}
