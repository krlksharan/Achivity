package com.shield.adultblocker

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

class BlockerAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var toggleLockOverlay: TextView? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val browserUrlBarIds = listOf(
        "com.android.chrome:id/url_bar",
        "com.brave.browser:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx:id/url_bar",
        "com.opera.browser:id/url_field",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.duckduckgo.mobile.android:id/omnibarTextInput"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        HostsRepository.initialize(applicationContext)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // 1. Ignore our own app, System UI, and Launcher
        if (packageName == applicationContext.packageName ||
            packageName == "com.android.systemui" ||
            packageName.contains("launcher")
        ) {
            removeToggleOverlay()
            return
        }

        val rootNode = rootInActiveWindow ?: return

        // 2. TARGETED TOGGLE DISABLE IN SETTINGS
        if (packageName.contains("settings") ||
            packageName.contains("packageinstaller") ||
            packageName.contains("permissioncontroller")
        ) {
            handleSettingsProtection(rootNode)
            return
        } else {
            removeToggleOverlay()
        }

        // 3. MILLISECOND BROWSER INTERCEPTION & SAFESEARCH ENFORCEMENT
        inspectBrowserNodes(rootNode)
    }

    private fun handleSettingsProtection(rootNode: AccessibilityNodeInfo) {
        // Backup safety: if the "Stop Shield Real-Time Blocker?" dialog ever appears, click Cancel in 0ms
        if (findNodeByTextOrDesc(rootNode, "Stop Shield Real-Time Blocker") != null ||
            findNodeByTextOrDesc(rootNode, "Turn off Shield") != null
        ) {
            clickCancelDialog(rootNode)
            return
        }

        // Check if we are specifically on the "Shield Real-Time Blocker" Accessibility page
        val toggleNode = findNodeByTextOrDesc(rootNode, "Use Shield Real-Time Blocker")
        val optionsNode = findNodeByTextOrDesc(rootNode, "Shield Real-Time Blocker shortcut")

        if (toggleNode != null || optionsNode != null) {
            // Find the exact bounds of the blue "Use Shield Real-Time Blocker" pill
            val bounds = Rect()
            val containerNode = findClickableParent(toggleNode) ?: toggleNode
            containerNode?.getBoundsInScreen(bounds)

            // Fallback coordinates if Android returns empty bounds for the custom switch widget
            if (bounds.width() < 200 || bounds.height() < 50) {
                val displayMetrics = resources.displayMetrics
                bounds.set(
                    (displayMetrics.widthPixels * 0.04).toInt(),
                    (displayMetrics.heightPixels * 0.16).toInt(),
                    (displayMetrics.widthPixels * 0.96).toInt(),
                    (displayMetrics.heightPixels * 0.26).toInt()
                )
            }

            showDisabledToggleOverlay(bounds)
            return
        }

        // Also block "App info" / "Force stop" / "Uninstall" page for ShieldBlocker
        val isOurAppInfo = findNodeByTextOrDesc(rootNode, "ShieldBlocker") != null &&
                (findNodeByTextOrDesc(rootNode, "Force stop") != null ||
                        findNodeByTextOrDesc(rootNode, "Uninstall") != null)

        val isOurDeviceAdmin = findNodeByTextOrDesc(rootNode, "Shield Uninstall Protection") != null &&
                findNodeByTextOrDesc(rootNode, "Deactivate") != null

        if (isOurAppInfo || isOurDeviceAdmin) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // On main Accessibility list or any other Settings page: remove overlay so user can use Settings normally
        removeToggleOverlay()
    }

    private fun findClickableParent(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        var current = node
        while (current != null) {
            val rect = Rect()
            current.getBoundsInScreen(rect)
            if (rect.width() > 300 && rect.height() in 80..400) {
                return current
            }
            current = current.parent
        }
        return node
    }

    private fun findNodeByTextOrDesc(
        node: AccessibilityNodeInfo?,
        query: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        val q = query.lowercase()
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""

        if (text.contains(q) || desc.contains(q)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val found = findNodeByTextOrDesc(node.getChild(i), query)
            if (found != null) return found
        }
        return null
    }

    private fun showDisabledToggleOverlay(bounds: Rect) {
        mainHandler.post {
            try {
                if (toggleLockOverlay == null) {
                    val pillBackground = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = 90f
                        setColor(Color.parseColor("#E6BDBDBD")) // Greyed-out disabled look
                        setStroke(3, Color.parseColor("#88757575"))
                    }

                    val view = TextView(this).apply {
                        text = "Use Shield Real-Time Blocker   [LOCKED ON 🔒]"
                        setTextColor(Color.parseColor("#616161"))
                        textSize = 16f
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        setPadding(56, 0, 56, 0)
                        background = pillBackground
                        // Absorb all clicks so the underlying switch cannot be toggled off
                        isClickable = true
                        isFocusable = true
                    }

                    val params = WindowManager.LayoutParams(
                        bounds.width(),
                        bounds.height(),
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = bounds.left
                        y = bounds.top
                    }

                    windowManager?.addView(view, params)
                    toggleLockOverlay = view
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun removeToggleOverlay() {
        mainHandler.post {
            try {
                toggleLockOverlay?.let {
                    windowManager?.removeView(it)
                    toggleLockOverlay = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun clickCancelDialog(rootNode: AccessibilityNodeInfo) {
        val cancelWords = listOf("Cancel", "CANCEL", "No", "Deny")
        for (word in cancelWords) {
            val node = findNodeByTextOrDesc(rootNode, word)
            if (node != null) {
                if (node.isClickable) {
                    node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                } else if (node.parent?.isClickable == true) {
                    node.parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun inspectBrowserNodes(rootNode: AccessibilityNodeInfo) {
        for (viewId in browserUrlBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val currentText = node.text?.toString() ?: continue
                if (processUrlOrSearchText(node, currentText)) return
            }
        }
        scanNodeTree(rootNode)
    }

    private fun scanNodeTree(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString() ?: ""
        if (text.isNotEmpty() && (node.className?.contains("EditText") == true || node.isFocused)) {
            if (processUrlOrSearchText(node, text)) return true
        }

        for (i in 0 until node.childCount) {
            if (scanNodeTree(node.getChild(i))) return true
        }
        return false
    }

    private fun processUrlOrSearchText(node: AccessibilityNodeInfo, text: String): Boolean {
        val lower = text.lowercase()

        if (HostsRepository.isUrlOrQueryBlocked(lower)) {
            if (node.isEditable) {
                val args = Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    "https://www.google.com/?safe=active"
                )
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            performGlobalAction(GLOBAL_ACTION_HOME)
            return true
        }

        if (!node.isFocused &&
            (lower.contains("google.com/search?") || lower.contains("google.co.in/search?")) &&
            !lower.contains("safe=active")
        ) {
            launchSafeSearchRedirect(text)
            return true
        }

        return false
    }

    private fun launchSafeSearchRedirect(originalUrl: String) {
        val safeUrl = if (originalUrl.startsWith("http")) {
            "$originalUrl&safe=active"
        } else {
            "https://$originalUrl&safe=active"
        }
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(safeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        removeToggleOverlay()
    }
}
