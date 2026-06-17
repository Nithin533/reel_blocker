package com.example.reel_blocker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PixelFormat
import android.media.AudioManager
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
    private var audioManager: AudioManager? = null
    private lateinit var prefs: SharedPreferences

    private val SHORTS_PLAYER_SIGNALS = listOf(
        "reel_player_page",
        "ShortsVideoPlayerView",
        "shortsContainer",
        "shorts_container"
    )

    private val SHORTS_SHELF_SIGNALS = listOf(
        "shorts_shelf"
    )

    private val INSTAGRAM_REELS_SIGNALS = listOf(
        "reel_viewer",
        "clips_tab",
        "reels_tray",
        "ReelsFragment",
        "IgReelsTray"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        prefs = getSharedPreferences("blocker_prefs", Context.MODE_PRIVATE)
    }

    private fun isTemporarilyAllowed(): Boolean {
        val allowUntil = prefs.getLong("allow_until", 0)
        return System.currentTimeMillis() < allowUntil
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            val pkg = event?.packageName?.toString() ?: return

            if (isTemporarilyAllowed()) {
                removeOverlay()
                return
            }

            when (pkg) {
                "com.google.android.youtube" -> handleYouTube()
                "com.instagram.android" -> handleInstagram()
                else -> removeOverlay()
            }
        } catch (e: Exception) {
            // Never let an exception here crash/freeze the service
            e.printStackTrace()
        }
    }

    private fun handleYouTube() {
        val root = rootInActiveWindow ?: return
        try {
            if (containsSignals(root, SHORTS_PLAYER_SIGNALS)) {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                performGlobalAction(GLOBAL_ACTION_BACK)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                removeOverlay()
                return
            }

            if (containsSignals(root, SHORTS_SHELF_SIGNALS)) {
                showOverlay()
            } else {
                removeOverlay()
            }
        } finally {
            root.recycle()
        }
    }

    private fun handleInstagram() {
        val root = rootInActiveWindow ?: return
        try {
            if (containsSignals(root, INSTAGRAM_REELS_SIGNALS)) {
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                performGlobalAction(GLOBAL_ACTION_BACK)
                audioManager?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
                removeOverlay()
            } else {
                removeOverlay()
            }
        } finally {
            root.recycle()
        }
    }

    // depth limit avoids ANR / freeze on extremely deep or cyclic view trees
    // child nodes are recycled to avoid memory leaks over long sessions
    private fun containsSignals(
        node: AccessibilityNodeInfo,
        signals: List<String>,
        depth: Int = 0
    ): Boolean {
        if (depth > 25) return false

        val viewId = node.viewIdResourceName ?: ""
        val text = node.text?.toString() ?: ""
        val className = node.className?.toString() ?: ""

        val matched = signals.any {
            viewId.contains(it, ignoreCase = true) ||
            text.contains(it, ignoreCase = true) ||
            className.contains(it, ignoreCase = true)
        }
        if (matched) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = containsSignals(child, signals, depth + 1)
            child.recycle()
            if (found) return true
        }
        return false
    }

    private fun showOverlay() {
        if (isOverlayShown) return

        overlayView = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
        }

        val message = TextView(this).apply {
            text = "🚫 Shorts are blocked"
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
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