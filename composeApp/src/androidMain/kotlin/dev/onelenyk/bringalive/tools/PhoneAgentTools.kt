package dev.onelenyk.bringalive.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.accessibility.BringaliveAccessibilityService
import dev.onelenyk.bringalive.model.formatForLlm
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

private const val TAG = "PhoneTools"

// ==================== Args ====================

@Serializable data class ScreenSummaryArgs(val placeholder: String = "") : ToolArgs
@Serializable data class ActiveAppArgs(val placeholder: String = "") : ToolArgs
@Serializable data class TapAtIndexArgs(val index: Int) : ToolArgs
@Serializable data class TapElementArgs(val query: String) : ToolArgs
@Serializable data class TapAndWaitArgs(val query: String, val waitMs: Long = 1500) : ToolArgs
@Serializable data class LongPressArgs(val query: String) : ToolArgs
@Serializable data class TypeTextArgs(val text: String) : ToolArgs
@Serializable data class FocusAndTypeArgs(val fieldQuery: String, val text: String) : ToolArgs
@Serializable data class ScrollScreenArgs(val direction: String) : ToolArgs
@Serializable data class ScrollToFindArgs(val query: String, val maxScrolls: Int = 10) : ToolArgs
@Serializable data class SwipeArgs(val direction: String) : ToolArgs
@Serializable data class BackArgs(val placeholder: String = "") : ToolArgs
@Serializable data class HomeArgs(val placeholder: String = "") : ToolArgs
@Serializable data class OpenAppArgs(val name: String) : ToolArgs
@Serializable data class ListAppsArgs(val placeholder: String = "") : ToolArgs
@Serializable data class WaitArgs(val ms: Long = 1000) : ToolArgs

@Serializable
data class ActionStep(
    val action: String,
    val target: String = "",
    val text: String = "",
    val waitMs: Long = 0,
)

@Serializable data class PerformActionArgs(val steps: List<ActionStep>) : ToolArgs

// ==================== Helpers ====================

private fun requireService(): BringaliveAccessibilityService {
    val svc = BringaliveAccessibilityService.instance
    if (svc == null) {
        Log.e(TAG, "Accessibility service is NOT enabled!")
        throw IllegalStateException(
            "Accessibility service not enabled. Ask the user to enable it in Android Settings → Accessibility → Bringalive."
        )
    }
    return svc
}

// ==================== Screen Reading ====================

class GetScreenSummaryTool : SimpleTool<ScreenSummaryArgs>(
    argsType = typeToken<ScreenSummaryArgs>(),
    name = "getScreenContent",
    description = "Read the current screen. Returns: active app, activity, focused element, and a numbered list of all UI elements. Use [index] numbers with tapAtIndex.",
) {
    override suspend fun execute(args: ScreenSummaryArgs): String {
        val summary = requireService().getScreenSummary()
        return summary.formatForLlm()
    }
}

class GetActiveAppTool : SimpleTool<ActiveAppArgs>(
    argsType = typeToken<ActiveAppArgs>(),
    name = "getActiveApp",
    description = "Get the currently active app name and package.",
) {
    override suspend fun execute(args: ActiveAppArgs): String {
        val svc = requireService()
        val pkg = svc.currentPackage ?: svc.rootInActiveWindow?.packageName?.toString() ?: "unknown"
        val activity = svc.currentActivity ?: "unknown"
        return "Active app: $pkg\nActivity: $activity"
    }
}

// ==================== Tap Actions ====================

class TapAtIndexTool : SimpleTool<TapAtIndexArgs>(
    argsType = typeToken<TapAtIndexArgs>(),
    name = "tapAtIndex",
    description = "Tap a UI element by its [index] number from getScreenContent. Fast, no wait.",
) {
    override suspend fun execute(args: TapAtIndexArgs): String {
        return if (requireService().tapByIndex(args.index)) "Tapped [${args.index}]"
        else "Failed to tap [${args.index}]"
    }
}

class TapElementTool : SimpleTool<TapElementArgs>(
    argsType = typeToken<TapElementArgs>(),
    name = "tapElement",
    description = "Find and tap a UI element by text (fuzzy match). No wait after tap.",
) {
    override suspend fun execute(args: TapElementArgs): String {
        return if (requireService().tapByQuery(args.query)) "Tapped \"${args.query}\""
        else "Not found: \"${args.query}\""
    }
}

class TapAndWaitTool : SimpleTool<TapAndWaitArgs>(
    argsType = typeToken<TapAndWaitArgs>(),
    name = "tapAndWait",
    description = "Find and tap a UI element, then wait for the screen to settle. Use when tapping navigates to a new screen.",
) {
    override suspend fun execute(args: TapAndWaitArgs): String {
        val svc = requireService()
        if (!svc.tapByQuery(args.query)) return "Not found: \"${args.query}\""
        svc.waitForIdle(idleTimeoutMs = 500, globalTimeoutMs = args.waitMs)
        return "Tapped \"${args.query}\" and waited for screen"
    }
}

class LongPressTool : SimpleTool<LongPressArgs>(
    argsType = typeToken<LongPressArgs>(),
    name = "longPress",
    description = "Long press on a UI element by text (fuzzy match). Opens context menus.",
) {
    override suspend fun execute(args: LongPressArgs): String {
        return if (requireService().longPressByQuery(args.query)) "Long pressed \"${args.query}\""
        else "Not found: \"${args.query}\""
    }
}

// ==================== Text Input ====================

class TypeTextTool : SimpleTool<TypeTextArgs>(
    argsType = typeToken<TypeTextArgs>(),
    name = "typeText",
    description = "Type text into the currently focused input field.",
) {
    override suspend fun execute(args: TypeTextArgs): String {
        return if (requireService().inputText(args.text)) "Typed: \"${args.text}\""
        else "No focused input field. Tap an input first."
    }
}

class FocusAndTypeTool : SimpleTool<FocusAndTypeArgs>(
    argsType = typeToken<FocusAndTypeArgs>(),
    name = "focusAndType",
    description = "Find an input field by text/description, tap it to focus, then type text. Combines tap + type in one step.",
) {
    override suspend fun execute(args: FocusAndTypeArgs): String {
        return if (requireService().focusAndType(args.fieldQuery, args.text))
            "Focused \"${args.fieldQuery}\" and typed \"${args.text}\""
        else "Could not find or focus field \"${args.fieldQuery}\""
    }
}

// ==================== Scroll ====================

class ScrollScreenTool : SimpleTool<ScrollScreenArgs>(
    argsType = typeToken<ScrollScreenArgs>(),
    name = "scrollScreen",
    description = "Scroll the screen. Direction: 'up' or 'down'.",
) {
    override suspend fun execute(args: ScrollScreenArgs): String {
        return if (requireService().scroll(args.direction)) "Scrolled ${args.direction}"
        else "No scrollable element found"
    }
}

class ScrollToFindTool : SimpleTool<ScrollToFindArgs>(
    argsType = typeToken<ScrollToFindArgs>(),
    name = "scrollToFind",
    description = "Scroll down until an element with matching text is found. Returns success/failure. Max 10 scrolls by default.",
) {
    override suspend fun execute(args: ScrollToFindArgs): String {
        val node = requireService().scrollToFind(args.query, args.maxScrolls)
        return if (node != null) "Found \"${args.query}\" after scrolling"
        else "\"${args.query}\" not found after ${args.maxScrolls} scrolls"
    }
}

// ==================== Gestures ====================

class SwipeTool : SimpleTool<SwipeArgs>(
    argsType = typeToken<SwipeArgs>(),
    name = "swipe",
    description = "Swipe gesture. Direction: 'left', 'right', 'up', 'down'. Use for tab navigation, dismissing items, pull-to-refresh.",
) {
    override suspend fun execute(args: SwipeArgs): String {
        return if (requireService().swipeDirection(args.direction)) "Swiped ${args.direction}"
        else "Swipe failed"
    }
}

// ==================== Navigation ====================

class PressBackTool : SimpleTool<BackArgs>(
    argsType = typeToken<BackArgs>(),
    name = "pressBack",
    description = "Press the system Back button.",
) {
    override suspend fun execute(args: BackArgs): String {
        requireService().goBack()
        delay(500)
        return "Pressed Back"
    }
}

class PressHomeTool : SimpleTool<HomeArgs>(
    argsType = typeToken<HomeArgs>(),
    name = "pressHome",
    description = "Go to the home screen.",
) {
    override suspend fun execute(args: HomeArgs): String {
        requireService().goHome()
        delay(500)
        return "Pressed Home"
    }
}

class WaitTool : SimpleTool<WaitArgs>(
    argsType = typeToken<WaitArgs>(),
    name = "wait",
    description = "Wait for a specified number of milliseconds. Use after actions that need time to complete (loading, animations).",
) {
    override suspend fun execute(args: WaitArgs): String {
        delay(args.ms)
        return "Waited ${args.ms}ms"
    }
}

// ==================== App Launch ====================

class OpenAppTool(private val context: Context) : SimpleTool<OpenAppArgs>(
    argsType = typeToken<OpenAppArgs>(),
    name = "openApp",
    description = "Open an app by name (e.g. 'Chrome', 'Camera', 'Telegram', 'Settings') or package name. Waits for app to load.",
) {
    private val knownIntents = mapOf(
        "camera" to Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
        "photo" to Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
        "video" to Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE),
        "settings" to Intent(android.provider.Settings.ACTION_SETTINGS),
        "wifi" to Intent(android.provider.Settings.ACTION_WIFI_SETTINGS),
        "bluetooth" to Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS),
        "battery" to Intent(Intent.ACTION_POWER_USAGE_SUMMARY),
        "phone" to Intent(Intent.ACTION_DIAL),
        "dialer" to Intent(Intent.ACTION_DIAL),
        "contacts" to Intent(Intent.ACTION_VIEW, android.provider.ContactsContract.Contacts.CONTENT_URI),
        "messages" to Intent(Intent.ACTION_MAIN).apply { type = "vnd.android-dir/mms-sms" },
        "sms" to Intent(Intent.ACTION_MAIN).apply { type = "vnd.android-dir/mms-sms" },
        "gallery" to Intent(Intent.ACTION_VIEW).apply { type = "image/*" },
        "photos" to Intent(Intent.ACTION_VIEW).apply { type = "image/*" },
        "maps" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0")),
        "browser" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://google.com")),
        "calculator" to Intent().apply { action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_APP_CALCULATOR) },
        "calendar" to Intent().apply { action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_APP_CALENDAR) },
        "email" to Intent().apply { action = Intent.ACTION_MAIN; addCategory(Intent.CATEGORY_APP_EMAIL) },
    )

    override suspend fun execute(args: OpenAppArgs): String {
        val name = args.name.trim()
        val result = tryKnownIntent(name) ?: tryByPackageOrLabel(name) ?: return "App not found: $name"
        // Wait for app to load
        BringaliveAccessibilityService.instance?.waitForIdle(800, 5000)
        return result
    }

    private fun tryKnownIntent(name: String): String? {
        val key = knownIntents.keys.firstOrNull { name.contains(it, ignoreCase = true) } ?: return null
        return try {
            context.startActivity(knownIntents[key]!!.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            "Opened: $key"
        } catch (_: Exception) { null }
    }

    private fun tryByPackageOrLabel(name: String): String? {
        context.packageManager.getLaunchIntentForPackage(name)?.let {
            context.startActivity(it.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
            return "Opened: $name"
        }
        val pm = context.packageManager
        val match = pm.getInstalledApplications(PackageManager.GET_META_DATA).firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        } ?: return null
        val intent = pm.getLaunchIntentForPackage(match.packageName) ?: return null
        context.startActivity(intent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        return "Opened: ${pm.getApplicationLabel(match)} (${match.packageName})"
    }
}

class ListInstalledAppsTool(private val context: Context) : SimpleTool<ListAppsArgs>(
    argsType = typeToken<ListAppsArgs>(),
    name = "listInstalledApps",
    description = "List all installed apps.",
) {
    override suspend fun execute(args: ListAppsArgs): String {
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { "${pm.getApplicationLabel(it)} (${it.packageName})" }
            .sorted()
        return "Installed apps (${apps.size}):\n${apps.joinToString("\n")}"
    }
}

// ==================== Compound Action ====================

class PerformActionTool(private val context: Context) : SimpleTool<PerformActionArgs>(
    argsType = typeToken<PerformActionArgs>(),
    name = "performAction",
    description = """Execute a sequence of phone actions. Each step runs, waits for screen to settle, then proceeds.
Actions: openApp, tap, tapIndex, type, focusAndType, scroll, scrollToFind, swipe, longPress, back, home, wait.
Example: [{"action":"openApp","target":"Telegram"},{"action":"tap","target":"Alice"},{"action":"focusAndType","target":"message","text":"hey"},{"action":"tap","target":"Send"}]""",
) {
    override suspend fun execute(args: PerformActionArgs): String {
        val svc = requireService()
        val overlay = svc.overlay
        val results = mutableListOf<String>()
        val totalStart = SystemClock.uptimeMillis()

        // Show overlay
        overlay?.startAction("Performing ${args.steps.size} steps")

        for ((i, step) in args.steps.withIndex()) {
            val stepStart = SystemClock.uptimeMillis()

            overlay?.addStep(i, step.action, step.target)

            val success = try {
                executeStep(svc, step)
            } catch (e: Exception) {
                overlay?.failStep(i)
                results += "✗ Step ${i + 1}: ${step.action} \"${step.target}\" — ERROR: ${e.message}"
                val result = formatResults(results, totalStart, failed = true)
                hideOverlayDelayed(overlay)
                return result
            }

            val stepMs = SystemClock.uptimeMillis() - stepStart

            if (!success) {
                overlay?.failStep(i)
                results += "✗ Step ${i + 1}: ${step.action} \"${step.target}\" — FAILED (${stepMs}ms)"
                val result = formatResults(results, totalStart, failed = true)
                hideOverlayDelayed(overlay)
                return result
            }

            overlay?.completeStep(i, stepMs)
            results += "✓ Step ${i + 1}: ${step.action} \"${step.target}\" (${stepMs}ms)"

            // Wait for idle after action
            val waitMs = if (step.waitMs > 0) step.waitMs else defaultWait(step.action)
            if (waitMs > 0) svc.waitForIdle(idleTimeoutMs = waitMs / 3, globalTimeoutMs = waitMs)
        }

        overlay?.complete()
        hideOverlayDelayed(overlay)
        return formatResults(results, totalStart, failed = false)
    }

    private suspend fun hideOverlayDelayed(overlay: dev.onelenyk.bringalive.accessibility.FloatingOverlay?) {
        delay(3000) // show result for 3 seconds
        overlay?.hide()
    }

    private suspend fun executeStep(svc: BringaliveAccessibilityService, step: ActionStep): Boolean {
        Log.d(TAG, "executeStep: action=${step.action} target=${step.target} text=${step.text}")
        return when (step.action.lowercase()) {
            "openapp" -> {
                val tool = OpenAppTool(context)
                val result = tool.execute(OpenAppArgs(step.target))
                !result.startsWith("App not found")
            }
            "tap" -> svc.tapByQuery(step.target)
            "tapindex" -> svc.tapByIndex(step.target.toIntOrNull() ?: return false)
            "type" -> svc.inputText(step.text.ifBlank { step.target })
            "focusandtype" -> svc.focusAndType(step.target, step.text)
            "scroll" -> svc.scroll(step.target.ifBlank { "down" })
            "scrolltofind" -> svc.scrollToFind(step.target) != null
            "swipe" -> svc.swipeDirection(step.target.ifBlank { "left" })
            "longpress" -> svc.longPressByQuery(step.target)
            "back" -> { svc.goBack(); true }
            "home" -> { svc.goHome(); true }
            "wait" -> { delay(step.waitMs.coerceAtLeast(100)); true }
            else -> false
        }
    }

    private fun defaultWait(action: String): Long = when (action.lowercase()) {
        "openapp" -> 3000
        "tap", "tapindex", "tapandwait" -> 1500
        "type", "focusandtype" -> 500
        "scroll", "scrolltofind" -> 1000
        "back", "home" -> 1500
        "swipe" -> 800
        "longpress" -> 1000
        else -> 500
    }

    private fun formatResults(results: List<String>, totalStart: Long, failed: Boolean): String {
        val totalMs = SystemClock.uptimeMillis() - totalStart
        val header = if (failed) "Action chain FAILED" else "All ${results.size} steps completed"
        return "$header (${totalMs}ms total):\n${results.joinToString("\n")}"
    }
}

// ==================== Registration ====================

@Serializable data class DumpArgs(val placeholder: String = "") : ToolArgs

class DumpScreenTool : SimpleTool<DumpArgs>(
    argsType = typeToken<DumpArgs>(),
    name = "dumpScreen",
    description = "Debug tool: dump the raw accessibility tree to see all elements including hidden ones. Use when elements can't be found by other tools.",
) {
    override suspend fun execute(args: DumpArgs): String {
        val dump = requireService().dumpTree()
        return dump.take(4000) // limit to avoid token explosion
    }
}

fun createPhoneAgentTools(context: Context): List<Tool<*, *>> = listOf(
    GetScreenSummaryTool(),
    GetActiveAppTool(),
    TapAtIndexTool(),
    TapElementTool(),
    TapAndWaitTool(),
    LongPressTool(),
    TypeTextTool(),
    FocusAndTypeTool(),
    ScrollScreenTool(),
    ScrollToFindTool(),
    SwipeTool(),
    PressBackTool(),
    PressHomeTool(),
    WaitTool(),
    OpenAppTool(context),
    ListInstalledAppsTool(context),
    PerformActionTool(context),
    DumpScreenTool(),
)
