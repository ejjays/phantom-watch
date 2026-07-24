package dev.phantomwatch

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoInstallService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val root = rootInActiveWindow ?: return
        val packageName = root.packageName?.toString() ?: return
        // Huawei shows an AppGallery "security check" page before the installer: only touch it while it shows our app
        val allowed = packageName in INSTALLERS ||
            (packageName.startsWith("com.huawei") && firstNode(root) { mentionsApp(it) } != null)
        if (!allowed) return
        var target: AccessibilityNodeInfo? = firstNode(root) { label(it) in BUTTONS } ?: return
        while (target != null && !target.isClickable) target = target.parent
        target?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    override fun onInterrupt() = Unit

    private fun mentionsApp(node: AccessibilityNodeInfo) =
        label(node).contains(getString(R.string.app_name).lowercase())

    private fun label(node: AccessibilityNodeInfo): String =
        (node.text ?: node.contentDescription)?.toString()?.trim()?.lowercase().orEmpty()

    private fun firstNode(root: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val queue = ArrayDeque(listOf(root))
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            if (match(node)) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { queue.add(it) }
        }
        return null
    }

    private companion object {
        val INSTALLERS = setOf("com.android.packageinstaller", "com.google.android.packageinstaller")
        val BUTTONS = setOf("install", "update", "continue", "install anyway", "update anyway")
    }
}
