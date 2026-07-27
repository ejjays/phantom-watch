package dev.phantomwatch

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.phantomwatch.core.AppLog

/** The clicker may only act inside the short window after this app commits its own install session. */
object AutoInstall {
    private var until = 0L

    fun arm() {
        until = SystemClock.uptimeMillis() + 120_000
        AppLog.d("autoinstall armed")
    }

    fun disarm() {
        until = 0L
        AppLog.d("autoinstall disarmed")
    }

    fun armed(): Boolean = until > SystemClock.uptimeMillis()
}

class AutoInstallService : AccessibilityService() {
    // Every node lookup crosses into the other app's main thread, so the package is filtered from the
    // event (free) and matches use one framework text search instead of walking the tree.
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return
        if (seen.add(packageName)) AppLog.d("a11y evt $packageName")
        if (!AutoInstall.armed()) return
        if (packageName !in INSTALLERS && !packageName.startsWith("com.huawei")) return
        val root = rootInActiveWindow ?: return
        // Huawei shows an AppGallery "security check" page before the installer: only touch it while it shows our app
        if (packageName !in INSTALLERS && find(root, getString(R.string.app_name)).isEmpty()) return
        // Its "installed / you may also like" page also shows our name, and its cards have install buttons
        if (FINISHED.any { find(root, it).isNotEmpty() }) {
            AutoInstall.disarm()
            return
        }
        val hit = BUTTONS.firstNotNullOfOrNull { text -> find(root, text).firstOrNull { label(it) == text } } ?: return
        var target: AccessibilityNodeInfo? = hit
        while (target != null && !target.isClickable) target = target.parent
        if (target != null) {
            AppLog.d("install click ${label(target)} in $packageName")
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
    }
}
