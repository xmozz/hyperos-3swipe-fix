package com.example.homefix

import android.accessibilityservice.AccessibilityService
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFixService : AccessibilityService() {

    companion object {
        const val TAG = "HomeFix"
        const val LOG_FILE = "homefix.log"

        /** App-level kill switch, toggled from MainActivity. */
        @Volatile var enabled = true

        /** Time window (ms) after leaving an app during which a drawer-open is treated as the bug. */
        @Volatile var timeoutMs = 3000L

        /** Extra delay (ms) before firing HOME. Lower = faster, higher = smoother. */
        @Volatile var reactionMs = 0L

        /** Delay (ms) between the 1st and 2nd HOME action (search -> drawer -> home). */
        @Volatile var secondDelayMs = 600L

        /** Verbose logging (event + inspection detail). Off by default. */
        @Volatile var debug = false

        private const val LAUNCHER_PKG = "com.miui.home"
        private const val APP_FINDER_PKG = "com.mi.appfinder"

        /** Packages whose window events must never count as "the previous app". */
        private val IGNORED = setOf(
            "com.android.systemui", "com.miui.systemui",
            "com.android.settings", "com.miui.securitycenter",
            "com.android.permissioncontroller", "com.miui.permissioncontroller",
            "com.android.packageinstaller", "com.miui.packageinstaller",
            "com.google.android.inputmethod.latin", "com.sohu.inputmethod.sogou.xiaomi",
            "com.google.android.googlequicksearchbox", "miui.systemui.plugin",
            "com.mi.appfinder"
        )

        private const val MAX_ATTEMPTS = 2
        private const val HOME_STABLE_MS = 600L
        private const val MAX_LOG_BYTES = 512 * 1024L

        private val TAB_RE = Regex("Tab \\d+ of \\d+")
        private val INSPECT_DELAYS = longArrayOf(0L, 120L, 300L, 600L, 1000L)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val homeRunnable = Runnable { performGlobalAction(GLOBAL_ACTION_HOME) }
    private var lastAppPackage: String? = null
    private var lastAppActiveAt = 0L
    private var lastHomeActionAt = 0L
    private var homeAttempts = 0
    private var fixing = false
    private var homeSeenAt = 0L
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    override fun onServiceConnected() {
        super.onServiceConnected()
        enabled = Prefs.isEnabled(this)
        timeoutMs = Prefs.getTimeoutMs(this)
        reactionMs = Prefs.getReactionMs(this)
        secondDelayMs = Prefs.getSecondDelayMs(this)
        debug = Prefs.isDebug(this)
        startForegroundNotification()
        log("=== service connected, enabled=$enabled, timeout=$timeoutMs ms, reaction=$reactionMs ms, second=$secondDelayMs ms ===")
    }

    override fun onDestroy() {
        log("=== service destroyed ===")
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val e = event ?: return
        if (!enabled) return
        val pkg = e.packageName?.toString() ?: "null"
        val cls = e.className?.toString() ?: "null"
        val now = SystemClock.elapsedRealtime()
        when (e.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                vlog("STATE   pkg=$pkg cls=$cls")
                if (isApp(pkg)) {
                    if (pkg != lastAppPackage) vlog("app    : pkg=$pkg cls=$cls")
                    lastAppPackage = pkg
                    lastAppActiveAt = now
                }
                scheduleInspect()
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                vlog("WINDOWS pkg=$pkg cls=$cls")
                scheduleInspect()
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isApp(pkg)) lastAppActiveAt = now
            }
            else ->
                vlog("EVENT(${e.eventType}) pkg=$pkg cls=$cls")
        }
    }

    private fun isApp(pkg: String) = pkg != "null" && pkg !in IGNORED && pkg != LAUNCHER_PKG

    private fun scheduleInspect() {
        handler.removeCallbacks(inspectRunnable)
        for (d in INSPECT_DELAYS) handler.postDelayed(inspectRunnable, d)
    }

    private val inspectRunnable = Runnable { inspectAndFix() }

    private fun inspectAndFix() {
        val now = SystemClock.elapsedRealtime()
        val state = launcherState()
        when (state) {
            0 -> { // still inside an app
                fixing = false
                homeAttempts = 0
                homeSeenAt = 0L
                vlog("other  : not launcher")
            }
            1 -> { // actual home screen (has workspace widgets)
                if (fixing) {
                    if (homeSeenAt == 0L) homeSeenAt = now
                    if (now - homeSeenAt > HOME_STABLE_MS) {
                        log("home   : stable home, clearing context")
                        fixing = false
                        homeAttempts = 0
                        homeSeenAt = 0L
                        lastAppPackage = null
                    }
                    // not stable yet -> keep fixing, but don't fire
                } else {
                    vlog("home   : launcher (no fix)")
                }
            }
            else -> { // 2 = drawer/search, 3 = blank/transitional launcher
                homeSeenAt = 0L
                if (!fixing) {
                    if (lastAppPackage != null && now - lastAppActiveAt < timeoutMs) {
                        fixing = true
                        lastHomeActionAt = 0L
                        homeAttempts = 0
                        log("fix    : started (came from $lastAppPackage)")
                    } else {
                        vlog("launcher: no recent app context")
                        return
                    }
                }
                fire(now, if (state == 2) "drawer" else "blank")
            }
        }
    }

    private fun fire(now: Long, where: String) {
        if (homeAttempts >= MAX_ATTEMPTS) {
            vlog("fix    : [$where] max attempts")
            return
        }
        if (homeAttempts > 0 && now - lastHomeActionAt < secondDelayMs) {
            vlog("fix    : [$where] gap ${now - lastHomeActionAt}ms")
            return
        }
        log("FIX    : [$where] -> GLOBAL_ACTION_HOME (attempt ${homeAttempts + 1})")
        homeAttempts++
        lastHomeActionAt = now
        if (reactionMs <= 0L) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else {
            handler.postDelayed(homeRunnable, reactionMs)
        }
    }

    /** 0 = not in launcher, 1 = home, 2 = drawer/search, 3 = blank/transitional. */
    private fun launcherState(): Int {
        val root = rootInActiveWindow
        if (root != null) {
            val pkg = root.packageName?.toString() ?: "?"
            if (pkg == APP_FINDER_PKG) {
                root.recycle()
                vlog("active : pkg=$pkg (app finder)")
                return 2
            }
            if (pkg == LAUNCHER_PKG) {
                val drawer = findDrawerIndicator(root)
                val home = findHomeIndicator(root)
                root.recycle()
                vlog("active : pkg=$pkg drawer=$drawer home=$home")
                return when {
                    drawer -> 2
                    home -> 1
                    else -> 3
                }
            }
            root.recycle()
            return 0
        }
        for (w in windows) {
            val r = w.root ?: continue
            val p = r.packageName?.toString()
            if (p == APP_FINDER_PKG) {
                r.recycle()
                return 2
            }
            if (p == LAUNCHER_PKG && findDrawerIndicator(r)) {
                r.recycle()
                return 2
            }
            r.recycle()
        }
        return 0
    }

    private fun findDrawerIndicator(node: AccessibilityNodeInfo): Boolean {
        if (node.viewIdResourceName == "com.miui.home:id/all_apps_setting_button") return true
        if (TAB_RE.containsMatchIn(node.contentDescription?.toString() ?: "")) return true
        if (node.className?.toString() == "android.widget.EditText" &&
            node.packageName?.toString() == LAUNCHER_PKG
        ) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findDrawerIndicator(child)) return true
        }
        return false
    }

    private fun findHomeIndicator(node: AccessibilityNodeInfo): Boolean {
        val id = node.viewIdResourceName ?: ""
        if (id == "com.miui.home:id/workspace" || id == "com.miui.home:id/widget_container") return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (findHomeIndicator(child)) return true
        }
        return false
    }

    override fun onInterrupt() {}

    private fun startForegroundNotification() {
        val notification = Notifications.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                HomeFixApplication.NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(HomeFixApplication.NOTIFICATION_ID, notification)
        }
    }

    /** Always written (important actions only). */
    private fun log(msg: String) = write(msg)

    /** Written only when debug mode is on. */
    private fun vlog(msg: String) {
        if (debug) write(msg)
    }

    private fun write(msg: String) {
        Log.d(TAG, msg)
        try {
            val f = File(filesDir, LOG_FILE)
            if (f.length() > MAX_LOG_BYTES) f.delete()
            f.appendText(fmt.format(Date()) + "  " + msg + "\n")
        } catch (_: Exception) {
        }
    }
}
