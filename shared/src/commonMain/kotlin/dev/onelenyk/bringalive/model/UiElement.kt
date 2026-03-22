package dev.onelenyk.bringalive.model

data class UiElement(
    val index: Int,
    val text: String?,
    val description: String?,
    val resourceId: String?,
    val className: String?,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isScrollable: Boolean,
    val isFocused: Boolean = false,
    val depth: Int,
)

data class ScreenSummary(
    val packageName: String?,
    val activityName: String?,
    val focusedElement: String?,
    val elements: List<UiElement>,
)

fun ScreenSummary.formatForLlm(): String {
    val sb = StringBuilder()

    // App context
    val appName = packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() } ?: "Unknown"
    sb.appendLine("App: $appName ($packageName)")
    if (activityName != null) {
        sb.appendLine("Activity: $activityName")
    }
    if (focusedElement != null) {
        sb.appendLine("Focused: $focusedElement")
    }
    sb.appendLine("---")

    if (elements.isEmpty()) {
        sb.appendLine("(no accessible elements found)")
        return sb.toString().trimEnd()
    }

    sb.appendLine("Elements (${elements.size}):")
    for (el in elements) {
        val indent = "  ".repeat(el.depth.coerceAtMost(4))
        val label = el.text ?: el.description ?: el.resourceId?.substringAfterLast('/') ?: ""
        val type = el.className ?: "View"
        val flags = buildList {
            if (el.isClickable) add("clickable")
            if (el.isEditable) add("editable")
            if (el.isScrollable) add("scrollable")
            if (el.isFocused) add("FOCUSED")
        }
        val flagStr = if (flags.isNotEmpty()) " (${flags.joinToString(", ")})" else ""

        if (label.isNotBlank() || flags.isNotEmpty()) {
            val labelPart = if (label.isNotBlank()) ": \"$label\"" else ""
            sb.appendLine("[${el.index}] $indent$type$labelPart$flagStr")
        }
    }
    return sb.toString().trimEnd()
}

// Keep old extension for backwards compat
fun List<UiElement>.formatForLlm(): String {
    if (isEmpty()) return "(empty screen — no accessible elements found)"
    val summary = ScreenSummary(null, null, null, this)
    return summary.elements.let {
        val sb = StringBuilder()
        for (el in it) {
            val indent = "  ".repeat(el.depth.coerceAtMost(4))
            val label = el.text ?: el.description ?: el.resourceId?.substringAfterLast('/') ?: ""
            val type = el.className ?: "View"
            val flags = buildList {
                if (el.isClickable) add("clickable")
                if (el.isEditable) add("editable")
                if (el.isScrollable) add("scrollable")
                if (el.isFocused) add("FOCUSED")
            }
            val flagStr = if (flags.isNotEmpty()) " (${flags.joinToString(", ")})" else ""
            if (label.isNotBlank() || flags.isNotEmpty()) {
                val labelPart = if (label.isNotBlank()) ": \"$label\"" else ""
                sb.appendLine("[${el.index}] $indent$type$labelPart$flagStr")
            }
        }
        sb.toString().trimEnd()
    }
}
