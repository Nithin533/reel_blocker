class BlockerAccessibilityService : AccessibilityService() {

    private val REELS_SIGNALS = listOf(
        "reel_viewer", "ReelsFragment", "clips_tab", "shorts_shelf",
        "ShortsVideoPlayerView", "shorts_player"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg !in listOf("com.instagram.android", "com.google.android.youtube")) return

        val root = rootInActiveWindow ?: return
        val nodeText = getAllNodeText(root)

        if (REELS_SIGNALS.any { nodeText.contains(it, ignoreCase = true) }) {
            if (!isFromFriendShare()) {
                showBlockOverlay()
            }
        } else {
            removeBlockOverlay()
        }
    }

    private fun isFromFriendShare(): Boolean {
        // Check if launched via share intent from messaging app
        // You store this flag when your app intercepts the share intent
        return SharedPrefs.wasLaunchedFromShare
    }
}