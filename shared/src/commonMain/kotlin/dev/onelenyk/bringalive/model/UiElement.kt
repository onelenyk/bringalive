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
    val depth: Int,
)

fun List<UiElement>.formatForLlm(): String {
    if (isEmpty()) return "(empty screen — no accessible elements found)"

    val sb = StringBuilder()
    for (el in this) {
        val indent = "  ".repeat(el.depth.coerceAtMost(4))
        val label = el.text ?: el.description ?: el.resourceId?.substringAfterLast('/') ?: ""
        val type = el.className ?: "View"
        val flags = buildList {
            if (el.isClickable) add("clickable")
            if (el.isEditable) add("editable")
            if (el.isScrollable) add("scrollable")
        }
        val flagStr = if (flags.isNotEmpty()) " (${flags.joinToString(", ")})" else ""

        if (label.isNotBlank()) {
            sb.appendLine("[${ el.index}] $indent$type: \"$label\"$flagStr")
        } else if (flags.isNotEmpty()) {
            sb.appendLine("[${el.index}] $indent$type$flagStr")
        }
    }
    return sb.toString().trimEnd()
}
