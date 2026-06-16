package com.example.reel_blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView

class BlockerAccessibilityService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var isOverlayShown = false
    private lateinit var prefs: SharedPreferences

    // These are the UI node keywords that appear when Reels/Shorts are on screen
    private val BLOCK_SIGNALS = listOf(
        // Instagram Reels
        "reel_viewer", "clips_tab", "reels_tray",
        "ReelsFragment", "IgReelsTray",
        // YouTube Shorts
        "shorts_shelf", "ShortsVideoPlayerView",
        "shortsContainer", "reel_player_page"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return

        // Only watch Instagram and YouTube
        if (pkg !in listOf("com.instagram.android", "com.google.android.youtube")) {
            removeOverlay()
            return
        }

        // If user launched from a shared link — allow it
        if (prefs.getBoolean("launched_from_share", false)) {
            removeOverlay()
            return
        }

        val root = rootInActiveWindow ?: return
        val detected = containsBlockSignal(root)

        if (detected) {
            showOverlay()
        } else {
            removeOverlay()
        }
    }

    private fun containsBlockSignal(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        if (BLOCK_SIGNALS.any {
            viewId.contains(it, ignoreCase = true) ||
            text.contains(it, ignoreCase = true) ||
            className.contains(it, ignoreCase = true)
        }) return true

        // Recursively check child nodes
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (containsBlockSignal(child)) return true
        }
        return false
    }

    private fun showOverlay() {
        if (isOverlayShown) return

        overlayView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F0000000"))
        }

        val message = TextView(this).apply {
            text = "🚫 Reels & Shorts are blocked\n\nAsk a friend to share one with you"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        overlayView?.addView(message, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { gravity = Gravity.CENTER })

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager?.addView(overlayView, params)
            isOverlayShown = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeOverlay() {
        if (!isOverlayShown) return
        try {
            windowManager?.removeView(overlayView)
            isOverlayShown = false
            overlayView = null
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onInterrupt() {
        removeOverlay()
    }

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }
}