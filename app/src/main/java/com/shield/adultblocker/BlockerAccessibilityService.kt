package com.shield.adultblocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.net.Uri
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
    private var overlayBounds: Rect? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // STRICT ALLOWLIST OF WEB BROWSERS ONLY.
    // GPay, Navi, PhonePe, Paytm, WhatsApp, and banking apps are 100% excluded.
    private val allowedBrowserPackages = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "com.brave.browser",
        "com.brave.browser_beta",
        "com.brave.browser_nightly",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "org.mozilla.focus",
        "com.microsoft.emmx",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.duckduckgo.mobile.android",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "com.UCMobile.intl",
        "com.mi.globalbrowser",
        "com.heytap.browser",
        "com.vivo.browser",
        "org.torproject.torbrowser"
    )

    // System Settings & Package Installer packages (strictly for Achivity's own toggle/uninstall lock)
    private val allowedSettingsPackages = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.samsung.accessibility",
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
        "com.miui.securitycenter",
        "com.miui.packageinstaller"
    )

    private val ownNames: List<String> by lazy {
        listOfNotNull(
            runCatching { getString(R.string.accessibility_service_label) }.getOrNull(),
            runCatching { getString(R.string.app_name) }.getOrNull(),
            "achivity",
            "uninstall protection"
        ).map { it.lowercase().trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private val browserUrlBarIds = listOf(
        "com.android.chrome:id/url_bar",
        "com.brave.browser:id/url_bar",
        "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
        "org.mozilla.fenix:id/mozac_browser_toolbar_url_view",
        "com.microsoft.emmx:id/url_bar",
        "com.opera.browser:id/url_field",
        "com.sec.android.app.sbrowser:id/location_bar_edit_text",
        "com.duckduckgo.mobile.android:id/omnibarTextInput",
        "com.vivaldi.browser:id/url_bar",
        "com.kiwibrowser.browser:id/url_bar",
        "com.mi.globalbrowser:id/url"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        HostsRepository.initialize(applicationContext)

        // Enforce strict package filter at the OS level
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.packageNames = (allowedBrowserPackages + allowedSettingsPackages).toTypedArray()
            info.flags = AccessibilityServiceInfo.DEFAULT or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            serviceInfo = info
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // 1. If in Settings, ONLY protect Achivity's own Accessibility / Uninstall screen
        if (allowedSettingsPackages.contains(packageName)) {
            val root = rootInActiveWindow ?: return
            if (allowedSettingsPackages.contains(root.packageName?.toString())) {
                handleSettingsProtection(listOf(root))
            }
            return
        }

        removeToggleOverlay()

        // 2. STRICT PRIVACY & BANKING GUARD:
        // If the package is not in allowedBrowserPackages, exit immediately before touching rootInActiveWindow.
        if (!allowedBrowserPackages.contains(packageName)) {
            return
        }

        val rootNode = rootInActiveWindow ?: return
        val rootPkg = rootNode.packageName?.toString() ?: return
        if (!allowedBrowserPackages.contains(rootPkg)) {
            return
        }

        inspectBrowserNodes(rootNode)
    }

    // ---------------------------------------------------------------------------------------
    // Settings & Uninstall Protection (Locks ONLY Achivity's own settings/uninstall)
    // ---------------------------------------------------------------------------------------

    private fun isDeviceAdminCurrentlyActive(): Boolean {
        return try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            dpm.isAdminActive(ComponentName(this, UninstallProtectionReceiver::class.java))
        } catch (e: Exception) {
            false
        }
    }

    private fun handleSettingsProtection(roots: List<AccessibilityNodeInfo>) {
        if (roots.isEmpty()) {
            removeToggleOverlay()
            return
        }

        val adminActive = isDeviceAdminCurrentlyActive()

        // 0) Allow user to tap "Activate" on the Device Admin screen when enabling Button 3
        if (!adminActive) {
            for (root in roots) {
                val isActivatingAdmin =
                    findNodeByTextOrDesc(root, "activate this device admin") != null ||
                            findNodeByTextOrDesc(root, "device admin app") != null ||
                            findNodeExact(root, "activate") != null
                if (isActivatingAdmin) {
                    removeToggleOverlay()
                    return
                }
            }
        }

        // 1) "Stop Achivity?" / "Turn off Achivity?" dialog -> cancel immediately
        for (root in roots) {
            if (findNode(root) { n -> isStopOrUninstallDialogText(n, adminActive) } != null) {
                clickCancelDialog(root)
                return
            }
        }

        // 2) Device Admin DEACTIVATION screen for Achivity -> lock & navigate back
        if (adminActive) {
            for (root in roots) {
                val mentionsUs = ownNames.any { findNodeByTextOrDesc(root, it) != null }
                val hasDeactivate = findNodeByTextOrDesc(root, "deactivate") != null ||
                        findNodeByTextOrDesc(root, "remove active admin") != null
                if (mentionsUs && hasDeactivate) {
                    val deactivateNode = findNodeByTextOrDesc(root, "deactivate")
                    if (deactivateNode != null) {
                        val r = Rect()
                        clickableAncestor(deactivateNode).getBoundsInScreen(r)
                        if (r.width() > 100 && r.height() > 40) {
                            showDisabledToggleOverlay(r, "Achivity Admin Protection   [LOCKED ON 🔒]")
                        }
                    }
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
        }

        // 3) Achivity's specific Accessibility Service page -> cover the toggle switch & back out
        for (root in roots) {
            if (!isOnOurSpecificAccessibilityDetailPage(root)) continue

            val switches = mutableListOf<AccessibilityNodeInfo>()
            collectSwitches(root, switches)

            if (switches.isNotEmpty()) {
                val union = Rect()
                var first = true
                for (sw in switches) {
                    val r = Rect()
                    clickableAncestor(sw).getBoundsInScreen(r)
                    if (r.isEmpty) continue
                    if (first) {
                        union.set(r)
                        first = false
                    } else {
                        union.union(r)
                    }
                }
                if (!first && union.width() > 100 && union.height() > 40) {
                    showDisabledToggleOverlay(union, "Achivity Real-Time Blocker   [LOCKED ON 🔒]")
                }
            }
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }

        // 4) Achivity's App Info page (Force stop / Uninstall / Clear storage) -> block ONLY when Admin is active
        if (adminActive) {
            for (root in roots) {
                val mentionsUs = ownNames.any { findNodeByTextOrDesc(root, it) != null }
                val isAppInfoScreen = findNodeByTextOrDesc(root, "force stop") != null ||
                        findNodeByTextOrDesc(root, "uninstall") != null ||
                        findNodeByTextOrDesc(root, "clear data") != null ||
                        findNodeByTextOrDesc(root, "storage & cache") != null ||
                        findNodeByTextOrDesc(root, "storage usage") != null
                val isSingleAppDetail = findNodeByTextOrDesc(root, "force stop") != null
                if (mentionsUs && isAppInfoScreen && isSingleAppDetail) {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return
                }
            }
        }

        removeToggleOverlay()
    }

    private fun isOnOurSpecificAccessibilityDetailPage(root: AccessibilityNodeInfo): Boolean {
        val mentionsUs = ownNames.any { findNodeByTextOrDesc(root, it) != null }
        if (!mentionsUs) return false

        val isDeviceAdminScreen =
            findNodeByTextOrDesc(root, "device admin") != null ||
                    findNodeExact(root, "activate") != null ||
                    findNodeByTextOrDesc(root, "deactivate") != null
        if (isDeviceAdminScreen) return false

        val switches = mutableListOf<AccessibilityNodeInfo>()
        collectSwitches(root, switches)
        if (switches.isEmpty()) return false

        val hasDetailIndicators =
            findNodeByTextOrDesc(root, "use ") != null ||
                    findNodeByTextOrDesc(root, "shortcut") != null ||
                    findNodeByTextOrDesc(root, "options") != null ||
                    findNodeExact(root, "on") != null ||
                    findNodeExact(root, "off") != null ||
                    switches.size in 1..2

        val isMainAccessibilityMenu =
            findNodeByTextOrDesc(root, "downloaded apps") != null ||
                    findNodeByTextOrDesc(root, "installed apps") != null ||
                    findNodeByTextOrDesc(root, "installed services") != null ||
                    findNodeByTextOrDesc(root, "text-to-speech") != null ||
                    findNodeByTextOrDesc(root, "talkback") != null

        return hasDetailIndicators && !isMainAccessibilityMenu
    }

    private fun isStopOrUninstallDialogText(n: AccessibilityNodeInfo, adminActive: Boolean): Boolean {
        val t = ((n.text?.toString() ?: "") + " " + (n.contentDescription?.toString() ?: "")).lowercase()
        if (t.isBlank()) return false
        if (t.contains("prevents unauthorized uninstallation")) return false

        val mentionsUs = ownNames.any { t.contains(it) }
        val mentionsStopAccessibility = t.contains("stop") ||
                t.contains("turn off") ||
                t.contains("disable")
        val mentionsUninstall = adminActive && (t.contains("uninstall") || t.contains("deactivate"))

        return mentionsUs && (mentionsStopAccessibility || mentionsUninstall)
    }

    private fun collectSwitches(n: AccessibilityNodeInfo?, out: MutableList<AccessibilityNodeInfo>) {
        if (n == null) return
        val cls = n.className?.toString() ?: ""
        val viewId = n.viewIdResourceName ?: ""
        if (cls.contains("Switch") ||
            cls.contains("ToggleButton") ||
            cls.contains("CheckBox") ||
            viewId.contains("switch_widget") ||
            n.isCheckable
        ) {
            out.add(n)
        }
        for (i in 0 until n.childCountSafe()) collectSwitches(n.getChild(i), out)
    }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        var best: AccessibilityNodeInfo = node
        while (current != null && depth < 5) {
            val r = Rect()
            current.getBoundsInScreen(r)
            if (current.isClickable && r.width() > 200 && r.height() >= 50) {
                return current
            }
            if (r.width() > 200 && r.height() in 50..400) best = current
            current = current.parent
            depth++
        }
        return best
    }

    private fun findNodeByTextOrDesc(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        val q = query.lowercase()
        return findNode(node) { n ->
            val text = n.text?.toString()?.lowercase() ?: ""
            val desc = n.contentDescription?.toString()?.lowercase() ?: ""
            text.contains(q) || desc.contains(q)
        }
    }

    private fun findNodeExact(node: AccessibilityNodeInfo?, query: String): AccessibilityNodeInfo? {
        return findNode(node) { n ->
            n.text?.toString()?.trim().equals(query, ignoreCase = true) ||
                    n.contentDescription?.toString()?.trim().equals(query, ignoreCase = true)
        }
    }

    private fun findNode(
        node: AccessibilityNodeInfo?,
        predicate: (AccessibilityNodeInfo) -> Boolean
    ): AccessibilityNodeInfo? {
        if (node == null) return null
        if (predicate(node)) return node
        for (i in 0 until node.childCountSafe()) {
            val found = findNode(node.getChild(i), predicate)
            if (found != null) return found
        }
        return null
    }

    private fun AccessibilityNodeInfo.childCountSafe(): Int =
        runCatching { childCount }.getOrDefault(0)

    private fun clickCancelDialog(rootNode: AccessibilityNodeInfo) {
        val cancelWords = listOf("Cancel", "No", "Deny", "Close")
        for (word in cancelWords) {
            val node = findNodeExact(rootNode, word) ?: continue
            var target: AccessibilityNodeInfo? = node
            var depth = 0
            while (target != null && depth < 3) {
                if (target.isClickable) {
                    target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return
                }
                target = target.parent
                depth++
            }
        }
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    private fun showDisabledToggleOverlay(bounds: Rect, labelText: String) {
        val b = Rect(bounds)
        mainHandler.post {
            try {
                val existing = toggleLockOverlay
                if (existing != null) {
                    if (overlayBounds != b) {
                        val params = existing.layoutParams as WindowManager.LayoutParams
                        params.x = b.left
                        params.y = b.top
                        params.width = b.width()
                        params.height = b.height()
                        windowManager?.updateViewLayout(existing, params)
                        overlayBounds = b
                    }
                    return@post
                }

                val pillBackground = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 90f
                    setColor(Color.parseColor("#E6BDBDBD"))
                    setStroke(3, Color.parseColor("#88757575"))
                }

                val view = TextView(this).apply {
                    text = labelText
                    setTextColor(Color.parseColor("#616161"))
                    textSize = 16f
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    setPadding(56, 0, 56, 0)
                    background = pillBackground
                    isClickable = true
                    isFocusable = false
                }

                val params = WindowManager.LayoutParams(
                    b.width(),
                    b.height(),
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = b.left
                    y = b.top
                }

                windowManager?.addView(view, params)
                toggleLockOverlay = view
                overlayBounds = b
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
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                toggleLockOverlay = null
                overlayBounds = null
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Browser / URL filtering (Strictly runs on browsers only)
    // ---------------------------------------------------------------------------------------

    private fun inspectBrowserNodes(rootNode: AccessibilityNodeInfo) {
        // 1. Check known browser URL bar IDs first
        for (viewId in browserUrlBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            for (node in nodes) {
                val currentText = node.text?.toString() ?: continue
                if (processUrlOrSearchText(node, currentText)) return
            }
        }
        // 2. Fallback for browsers with custom URL bar IDs
        scanNodeTree(rootNode)
    }

    private fun scanNodeTree(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString() ?: ""
        if (text.isNotEmpty() && (node.className?.contains("EditText") == true || node.isFocused)) {
            if (processUrlOrSearchText(node, text)) return true
        }

        for (i in 0 until node.childCountSafe()) {
            if (scanNodeTree(node.getChild(i))) return true
        }
        return false
    }

    private fun processUrlOrSearchText(node: AccessibilityNodeInfo, text: String): Boolean {
        val lower = text.lowercase().trim()

        if (HostsRepository.isBlocked(lower)) {
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

        if (!node.isFocused) {
            when {
                (lower.contains("google.com/search?") || lower.contains("google.co.in/search?")) &&
                        !lower.contains("safe=active") -> {
                    launchSafeSearchRedirect(text, "safe=active")
                    return true
                }
                lower.contains("bing.com/search?") && !lower.contains("adlt=strict") -> {
                    launchSafeSearchRedirect(text, "adlt=strict")
                    return true
                }
                lower.contains("duckduckgo.com/?") && !lower.contains("kp=1") -> {
                    launchSafeSearchRedirect(text, "kp=1")
                    return true
                }
            }
        }

        return false
    }

    private fun launchSafeSearchRedirect(originalUrl: String, safeParam: String) {
        val base = if (originalUrl.startsWith("http")) originalUrl else "https://$originalUrl"
        val safeUrl = "$base&$safeParam"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    override fun onInterrupt() {
        removeToggleOverlay()
    }

    override fun onDestroy() {
        removeToggleOverlay()
        super.onDestroy()
    }
}
