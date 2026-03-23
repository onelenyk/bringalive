package dev.onelenyk.bringalive.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.onelenyk.bringalive.model.ScreenSummary
import dev.onelenyk.bringalive.model.UiElement
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "BringaliveA11y"

class BringaliveAccessibilityService : AccessibilityService() {

    @Volatile
    private var lastEventTime = 0L

    @Volatile
    var currentPackage: String? = null
        private set

    @Volatile
    var currentActivity: String? = null
        private set

    var overlay: FloatingOverlay? = null
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        overlay = FloatingOverlay(this)
        Log.i(TAG, "Service connected")
        observeVoiceState()
    }

    private fun observeVoiceState() {
        try {
            val engine = org.koin.java.KoinJavaComponent.getKoin()
                .getOrNull<dev.onelenyk.bringalive.voice.VoiceEngine>() ?: return
            val scope = kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main
            )
            scope.launch {
                engine.state.collect { state ->
                    when (state) {
                        dev.onelenyk.bringalive.voice.VoiceState.LISTENING ->
                            overlay?.update(OverlayState(visible = true, title = "Listening..."))
                        dev.onelenyk.bringalive.voice.VoiceState.RECORDING ->
                            overlay?.update(OverlayState(visible = true, title = "Recording speech..."))
                        dev.onelenyk.bringalive.voice.VoiceState.TRANSCRIBING ->
                            overlay?.update(OverlayState(visible = true, title = "Transcribing..."))
                        dev.onelenyk.bringalive.voice.VoiceState.THINKING ->
                            overlay?.update(OverlayState(visible = true, title = "AI thinking..."))
                        dev.onelenyk.bringalive.voice.VoiceState.SPEAKING ->
                            overlay?.update(OverlayState(visible = true, title = "Speaking...", isComplete = false))
                        dev.onelenyk.bringalive.voice.VoiceState.IDLE ->
                            overlay?.hide()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not observe voice state: ${e.message}")
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        lastEventTime = SystemClock.uptimeMillis()

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            currentPackage = event.packageName?.toString()
            currentActivity = event.className?.toString()
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        overlay?.hide()
        overlay = null
        if (instance == this) instance = null
    }

    // ========== Wait for Idle ==========

    suspend fun waitForIdle(idleTimeoutMs: Long = 500, globalTimeoutMs: Long = 5000): Boolean {
        val startTime = SystemClock.uptimeMillis()
        delay(300) // min wait for animation start
        while (SystemClock.uptimeMillis() - startTime < globalTimeoutMs) {
            val elapsed = SystemClock.uptimeMillis() - lastEventTime
            if (elapsed >= idleTimeoutMs) return true
            delay(100)
        }
        return false // timeout — proceed anyway
    }

    // ========== Screen Reading ==========

    private val nodeCache = mutableMapOf<Int, AccessibilityNodeInfo>()

    fun getScreenTree(): List<UiElement> {
        nodeCache.clear()
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        traverseNode(root, elements, depth = 0)
        return elements
    }

    fun getScreenSummary(): ScreenSummary {
        val elements = getScreenTree()
        val focused = findFocusedElement()
        return ScreenSummary(
            packageName = currentPackage ?: rootInActiveWindow?.packageName?.toString(),
            activityName = currentActivity,
            focusedElement = focused,
            elements = elements,
        )
    }

    private fun findFocusedElement(): String? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        val text = focused.text?.toString() ?: focused.contentDescription?.toString() ?: focused.hintText?.toString()
        val type = focused.className?.toString()?.substringAfterLast('.')
        return if (text != null) "$type: \"$text\"" else type
    }

    private fun traverseNode(
        node: AccessibilityNodeInfo,
        elements: MutableList<UiElement>,
        depth: Int,
    ) {
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        val id = node.viewIdResourceName
        val isClickable = node.isClickable
        val isEditable = node.isEditable
        val isScrollable = node.isScrollable
        val isFocused = node.isFocused || node.isAccessibilityFocused
        val className = node.className?.toString()?.substringAfterLast('.')

        if (text != null || desc != null || isClickable || isEditable || isScrollable) {
            val index = elements.size
            elements += UiElement(
                index = index,
                text = text,
                description = desc,
                resourceId = id,
                className = className,
                isClickable = isClickable,
                isEditable = isEditable,
                isScrollable = isScrollable,
                isFocused = isFocused,
                depth = depth,
            )
            nodeCache[index] = node
        }

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                traverseNode(child, elements, depth + 1)
            }
        }
    }

    // ========== Debug Dump ==========

    /** Dump the FULL accessibility tree to logcat for debugging. */
    fun dumpTree(): String {
        val root = rootInActiveWindow ?: return "(no root window)"
        val sb = StringBuilder()
        sb.appendLine("=== Accessibility Tree Dump ===")
        sb.appendLine("Package: ${root.packageName}")
        dumpNode(root, sb, depth = 0)
        val result = sb.toString()
        Log.d(TAG, result)
        return result
    }

    private fun dumpNode(node: AccessibilityNodeInfo, sb: StringBuilder, depth: Int) {
        val indent = "  ".repeat(depth)
        val cls = node.className?.toString()?.substringAfterLast('.') ?: "?"
        val text = node.text?.toString()?.take(50)
        val desc = node.contentDescription?.toString()?.take(50)
        val id = node.viewIdResourceName
        val flags = buildList {
            if (node.isClickable) add("click")
            if (node.isEditable) add("edit")
            if (node.isScrollable) add("scroll")
            if (node.isFocused) add("focus")
            if (node.isChecked) add("checked")
            if (node.isEnabled) add("enabled")
        }.joinToString(",")

        sb.appendLine("$indent$cls text=$text desc=$desc id=$id [$flags]")

        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dumpNode(it, sb, depth + 1) }
        }
    }

    // ========== Smart Element Finding (5-layer) ==========

    fun smartFind(query: String): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "smartFind(\"$query\"): rootInActiveWindow is null"); return null }

        // Layer 1: Resource ID (exact)
        if (query.contains(":id/")) {
            val byId = root.findAccessibilityNodeInfosByViewId(query)
            if (byId.isNotEmpty()) { Log.d(TAG, "smartFind(\"$query\"): found by viewId"); return byId.first() }
        }

        // Layer 2: Text (case-insensitive substring — built-in)
        val byText = root.findAccessibilityNodeInfosByText(query)
        if (byText.isNotEmpty()) { Log.d(TAG, "smartFind(\"$query\"): found by text (${byText.size} matches)"); return byText.first() }

        // Layer 3: Deep recursive search — check text, contentDescription, hintText, viewId
        val deep = findNodeRecursive(root) { node ->
            val nodeText = node.text?.toString()
            val nodeDesc = node.contentDescription?.toString()
            val nodeHint = node.hintText?.toString()
            val nodeId = node.viewIdResourceName

            nodeText?.contains(query, ignoreCase = true) == true ||
                nodeDesc?.contains(query, ignoreCase = true) == true ||
                nodeHint?.contains(query, ignoreCase = true) == true ||
                nodeId?.contains(query, ignoreCase = true) == true
        }
        if (deep != null) { Log.d(TAG, "smartFind(\"$query\"): found by deep search (text=${deep.text} desc=${deep.contentDescription})"); return deep }

        // Layer 3b: Fuzzy — Levenshtein distance
        val fuzzy = findNodeRecursive(root) { node ->
            node.text?.toString()?.let { levenshteinMatch(it, query) } == true ||
                node.contentDescription?.toString()?.let { levenshteinMatch(it, query) } == true
        }
        if (fuzzy != null) { Log.d(TAG, "smartFind(\"$query\"): found by fuzzy/Levenshtein match"); return fuzzy }

        // Layer 5: Class name heuristic
        val classQuery = query.lowercase()
        if (classQuery in listOf("button", "edittext", "input", "textfield", "switch", "checkbox")) {
            val byClass = findNodeRecursive(root) { node ->
                node.className?.toString()?.lowercase()?.contains(classQuery) == true &&
                    (node.isClickable || node.isEditable)
            }
            if (byClass != null) { Log.d(TAG, "smartFind(\"$query\"): found by class heuristic"); return byClass }
        }

        Log.w(TAG, "smartFind(\"$query\"): NOT FOUND across all layers")
        return null
    }

    /** Layer 4: Scroll to find — scrolls container until element found or max scrolls. */
    suspend fun scrollToFind(query: String, maxScrolls: Int = 10): AccessibilityNodeInfo? {
        for (i in 0 until maxScrolls) {
            val found = smartFind(query)
            if (found != null) return found

            val root = rootInActiveWindow ?: return null
            val scrollable = findNodeRecursive(root) { it.isScrollable } ?: return null

            val before = getVisibleTexts()
            val scrolled = scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            if (!scrolled) return null // end of content

            delay(400) // wait for new content

            val after = getVisibleTexts()
            if (before == after) return null // content didn't change — at end
        }
        return null
    }

    private fun getVisibleTexts(): Set<String> {
        val root = rootInActiveWindow ?: return emptySet()
        val texts = mutableSetOf<String>()
        collectTexts(root, texts)
        return texts
    }

    private fun collectTexts(node: AccessibilityNodeInfo, texts: MutableSet<String>) {
        node.text?.toString()?.let { texts.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectTexts(it, texts) }
        }
    }

    private fun levenshteinMatch(a: String, b: String): Boolean {
        val al = a.lowercase()
        val bl = b.lowercase()
        if (al.contains(bl) || bl.contains(al)) return true
        if (al.length < 3 || bl.length < 3) return false
        val distance = levenshteinDistance(al, bl)
        val maxLen = maxOf(al.length, bl.length)
        return distance.toFloat() / maxLen < 0.4f
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }

    // ========== Actions ==========

    fun tapByIndex(index: Int): Boolean {
        val node = nodeCache[index]
        if (node == null) {
            Log.w(TAG, "tapByIndex($index): node not in cache (cache size=${nodeCache.size})")
            return false
        }
        val result = performClickOnNode(node)
        Log.d(TAG, "tapByIndex($index): $result")
        return result
    }

    fun tapByQuery(query: String): Boolean {
        val node = smartFind(query)
        if (node == null) {
            Log.w(TAG, "tapByQuery(\"$query\"): element not found")
            return false
        }
        val result = performClickOnNode(node)
        Log.d(TAG, "tapByQuery(\"$query\"): $result (node=${node.className} text=${node.text} desc=${node.contentDescription})")
        return result
    }

    fun longPressByIndex(index: Int): Boolean {
        val node = nodeCache[index]
        if (node == null) { Log.w(TAG, "longPressByIndex($index): not in cache"); return false }
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun longPressByQuery(query: String): Boolean {
        val node = smartFind(query)
        if (node == null) { Log.w(TAG, "longPressByQuery(\"$query\"): not found"); return false }
        return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow
        if (root == null) { Log.e(TAG, "inputText: rootInActiveWindow is null"); return false }
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        val target = if (focused?.isEditable == true) {
            Log.d(TAG, "inputText: using focused editable: ${focused.className} text=${focused.text}")
            focused
        } else {
            val found = findNodeRecursive(root) { it.isEditable }
            if (found != null) Log.d(TAG, "inputText: found editable: ${found.className} text=${found.text}")
            else Log.w(TAG, "inputText: no editable field found on screen")
            found
        } ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val result = target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        Log.d(TAG, "inputText(\"$text\"): ACTION_SET_TEXT result=$result")
        return result
    }

    suspend fun focusAndType(fieldQuery: String, text: String): Boolean {
        Log.d(TAG, "focusAndType(\"$fieldQuery\", \"$text\")")
        val node = smartFind(fieldQuery)
        if (node == null) { Log.w(TAG, "focusAndType: field \"$fieldQuery\" not found"); return false }
        val clicked = performClickOnNode(node)
        Log.d(TAG, "focusAndType: clicked field=$clicked")
        delay(400)
        val typed = inputText(text)
        Log.d(TAG, "focusAndType: typed=$typed")
        return typed
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findNodeRecursive(root) { it.isScrollable } ?: return false
        val action = when (direction.lowercase()) {
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return false
        }
        return scrollable.performAction(action)
    }

    fun goBack(): Boolean = performGlobalAction(GLOBAL_ACTION_BACK)
    fun goHome(): Boolean = performGlobalAction(GLOBAL_ACTION_HOME)
    fun showRecents(): Boolean = performGlobalAction(GLOBAL_ACTION_RECENTS)
    fun openNotifications(): Boolean = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

    // ========== Gestures ==========

    fun swipeDirection(direction: String): Boolean {
        val metrics = resources.displayMetrics
        val w = metrics.widthPixels.toFloat()
        val h = metrics.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val margin = w * 0.2f

        return when (direction.lowercase()) {
            "left" -> swipe(w - margin, cy, margin, cy)
            "right" -> swipe(margin, cy, w - margin, cy)
            "up" -> swipe(cx, h * 0.7f, cx, h * 0.3f)
            "down" -> swipe(cx, h * 0.3f, cx, h * 0.7f)
            else -> false
        }
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long = 300): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture, null, null)
    }

    // ========== Helpers ==========

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    private fun findNodeRecursive(
        node: AccessibilityNodeInfo,
        predicate: (AccessibilityNodeInfo) -> Boolean,
    ): AccessibilityNodeInfo? {
        if (predicate(node)) return node
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                findNodeRecursive(child, predicate)?.let { return it }
            }
        }
        return null
    }

    companion object {
        @Volatile
        var instance: BringaliveAccessibilityService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}
