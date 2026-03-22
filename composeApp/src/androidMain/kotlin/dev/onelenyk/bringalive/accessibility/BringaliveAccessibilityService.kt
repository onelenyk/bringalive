package dev.onelenyk.bringalive.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.onelenyk.bringalive.model.UiElement

class BringaliveAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to events — tools poll on demand
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
    }

    // --- Screen Reading ---

    fun getScreenTree(): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val elements = mutableListOf<UiElement>()
        traverseNode(root, elements, depth = 0)
        return elements
    }

    /** Cached nodes for action execution — cleared on each getScreenTree call. */
    private val nodeCache = mutableMapOf<Int, AccessibilityNodeInfo>()

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
        val className = node.className?.toString()?.substringAfterLast('.')

        // Collect only meaningful elements
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

    // --- Actions ---

    fun tapByIndex(index: Int): Boolean {
        val node = nodeCache[index] ?: return false
        return performClickOnNode(node)
    }

    fun tapByQuery(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val node = findNodeByText(root, query)
        if (node != null) {
            val result = performClickOnNode(node)
            return result
        }
        return false
    }

    fun inputText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = findFocusedEditText(root) ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val scrollable = findScrollableNode(root) ?: return false
        val action = when (direction.lowercase()) {
            "down", "forward" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up", "backward" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return false
        }
        return scrollable.performAction(action)
    }

    fun goBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun goHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun showRecents(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    fun openNotifications(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
    }

    // --- Helpers ---

    private fun performClickOnNode(node: AccessibilityNodeInfo): Boolean {
        // Try clicking the node directly
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }
        // Walk up to find a clickable parent
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }
        return false
    }

    private fun findNodeByText(root: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        // Try findAccessibilityNodeInfosByText first
        val byText = root.findAccessibilityNodeInfosByText(query)
        if (byText.isNotEmpty()) return byText.first()

        // Try content description match
        return findNodeRecursive(root) { node ->
            node.contentDescription?.toString()?.contains(query, ignoreCase = true) == true ||
                node.text?.toString()?.contains(query, ignoreCase = true) == true
        }
    }

    private fun findFocusedEditText(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Try to find focused node first
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused?.isEditable == true) return focused

        // Fall back to first editable node
        return findNodeRecursive(root) { it.isEditable }
    }

    private fun findScrollableNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        return findNodeRecursive(root) { it.isScrollable }
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
