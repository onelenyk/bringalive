package dev.onelenyk.bringalive.tools

import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.shizuku.ShizukuManager
import kotlinx.serialization.Serializable
import android.util.Base64

// --- Args ---

@Serializable
data class UiDumpArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class ShellScreenshotArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class InputTapArgs(val x: Int, val y: Int) : ToolArgs

@Serializable
data class InputSwipeArgs(
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val durationMs: Int = 300,
) : ToolArgs

@Serializable
data class InputKeyEventArgs(val keycode: String, val longPress: Boolean = false) : ToolArgs

// --- Tools ---

class UiAutomatorDumpTool(private val shizukuManager: ShizukuManager) : SimpleTool<UiDumpArgs>(
    argsType = typeToken<UiDumpArgs>(),
    name = "uiAutomatorDump",
    description = "Dump the current UI hierarchy as XML using uiautomator. Returns all visible elements with their text, content-description, resource-id, bounds, clickable/scrollable flags. Useful for finding element coordinates for input tap commands. Note: takes ~1-2 seconds.",
) {
    override suspend fun execute(args: UiDumpArgs): String {
        val dumpResult = shizukuManager.executeCommand("uiautomator dump /sdcard/ui_dump.xml")
        if (!dumpResult.isSuccess) {
            return dumpResult.toDisplayString()
        }
        val catResult = shizukuManager.executeCommand("cat /sdcard/ui_dump.xml")
        // Fire-and-forget cleanup
        shizukuManager.executeCommand("rm /sdcard/ui_dump.xml")
        return if (catResult.isSuccess) catResult.stdout else catResult.toDisplayString()
    }
}

class ShellScreenshotTool(private val shizukuManager: ShizukuManager) : SimpleTool<ShellScreenshotArgs>(
    argsType = typeToken<ShellScreenshotArgs>(),
    name = "shellScreenshot",
    description = "Take a screenshot using the shell screencap command (requires Shizuku). Returns a base64-encoded PNG. Faster setup than MediaProjection — no permission dialog needed.",
) {
    override suspend fun execute(args: ShellScreenshotArgs): String {
        val capResult = shizukuManager.executeCommand("screencap -p /sdcard/shell_screenshot.png")
        if (!capResult.isSuccess) {
            return capResult.toDisplayString()
        }
        val bytes = java.io.File("/sdcard/shell_screenshot.png").readBytes()
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        // Fire-and-forget cleanup
        shizukuManager.executeCommand("rm /sdcard/shell_screenshot.png")
        return "data:image/png;base64,$base64"
    }
}

class InputTapTool(private val shizukuManager: ShizukuManager) : SimpleTool<InputTapArgs>(
    argsType = typeToken<InputTapArgs>(),
    name = "inputTap",
    description = "Tap the screen at specific coordinates using ADB input command. Use uiAutomatorDump to find element bounds, then calculate center: x = left + width/2, y = top + height/2. Bounds format in XML: bounds=\"[left,top][right,bottom]\".",
) {
    override suspend fun execute(args: InputTapArgs): String {
        return shizukuManager.executeCommand("input tap ${args.x} ${args.y}").toDisplayString()
    }
}

class InputSwipeTool(private val shizukuManager: ShizukuManager) : SimpleTool<InputSwipeArgs>(
    argsType = typeToken<InputSwipeArgs>(),
    name = "inputSwipe",
    description = "Swipe from (x1,y1) to (x2,y2) over durationMs milliseconds. Use for scrolling (swipe up to scroll down: x1=540 y1=1500 x2=540 y2=500), pull-to-refresh, or drag gestures.",
) {
    override suspend fun execute(args: InputSwipeArgs): String {
        return shizukuManager.executeCommand(
            "input swipe ${args.x1} ${args.y1} ${args.x2} ${args.y2} ${args.durationMs}"
        ).toDisplayString()
    }
}

class InputKeyEventTool(private val shizukuManager: ShizukuManager) : SimpleTool<InputKeyEventArgs>(
    argsType = typeToken<InputKeyEventArgs>(),
    name = "inputKeyEvent",
    description = "Send a key event. Common keycodes: KEYCODE_BACK, KEYCODE_HOME, KEYCODE_POWER, KEYCODE_VOLUME_UP, KEYCODE_VOLUME_DOWN, KEYCODE_ENTER, KEYCODE_TAB, KEYCODE_DEL, KEYCODE_MEDIA_PLAY_PAUSE, KEYCODE_MEDIA_NEXT. Set longPress=true for long-press actions.",
) {
    override suspend fun execute(args: InputKeyEventArgs): String {
        val command = if (args.longPress) {
            "input keyevent --longpress ${args.keycode}"
        } else {
            "input keyevent ${args.keycode}"
        }
        return shizukuManager.executeCommand(command).toDisplayString()
    }
}

/** Returns all shell UI tools as a list for ToolRegistry. */
fun createShellUiTools(shizukuManager: ShizukuManager): List<Tool<*, *>> = listOf(
    UiAutomatorDumpTool(shizukuManager),
    ShellScreenshotTool(shizukuManager),
    InputTapTool(shizukuManager),
    InputSwipeTool(shizukuManager),
    InputKeyEventTool(shizukuManager),
)
