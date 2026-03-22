package dev.onelenyk.bringalive.tools

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.accessibility.BringaliveAccessibilityService
import dev.onelenyk.bringalive.model.formatForLlm
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class ScreenArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class BackArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class HomeArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class ListAppsArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class TapAtIndexArgs(val index: Int) : ToolArgs

@Serializable
data class TapElementArgs(val query: String) : ToolArgs

@Serializable
data class TypeTextArgs(val text: String) : ToolArgs

@Serializable
data class ScrollScreenArgs(val direction: String) : ToolArgs

@Serializable
data class OpenAppArgs(val name: String) : ToolArgs

// --- Tools ---

private fun requireService(): BringaliveAccessibilityService =
    BringaliveAccessibilityService.instance
        ?: throw IllegalStateException(
            "Accessibility service not enabled. Ask the user to enable it in Android Settings → Accessibility → Bringalive."
        )

class GetScreenContentTool : SimpleTool<ScreenArgs>(
    argsType = typeToken<ScreenArgs>(),
    name = "getScreenContent",
    description = "Read the current screen content. Returns a numbered list of UI elements with type, text, and properties (clickable, editable, scrollable). Use [index] numbers with tapAtIndex.",
) {
    override suspend fun execute(args: ScreenArgs): String {
        val svc = requireService()
        val elements = svc.getScreenTree()
        return if (elements.isEmpty()) {
            "Screen appears empty or inaccessible."
        } else {
            "Screen content (${elements.size} elements):\n${elements.formatForLlm()}"
        }
    }
}

class TapAtIndexTool : SimpleTool<TapAtIndexArgs>(
    argsType = typeToken<TapAtIndexArgs>(),
    name = "tapAtIndex",
    description = "Tap/click a UI element by its [index] number from getScreenContent output.",
) {
    override suspend fun execute(args: TapAtIndexArgs): String {
        val svc = requireService()
        return if (svc.tapByIndex(args.index)) "Tapped element [${args.index}]"
        else "Failed to tap element [${args.index}] — it may not be clickable"
    }
}

class TapElementTool : SimpleTool<TapElementArgs>(
    argsType = typeToken<TapElementArgs>(),
    name = "tapElement",
    description = "Tap/click a UI element by searching for matching text or description on screen.",
) {
    override suspend fun execute(args: TapElementArgs): String {
        val svc = requireService()
        return if (svc.tapByQuery(args.query)) "Tapped element matching: \"${args.query}\""
        else "No tappable element found matching: \"${args.query}\""
    }
}

class TypeTextTool : SimpleTool<TypeTextArgs>(
    argsType = typeToken<TypeTextArgs>(),
    name = "typeText",
    description = "Type text into the currently focused input field. Tap an input field first if needed.",
) {
    override suspend fun execute(args: TypeTextArgs): String {
        val svc = requireService()
        return if (svc.inputText(args.text)) "Typed: \"${args.text}\""
        else "Failed to type — no focused text field found. Try tapping an input field first."
    }
}

class ScrollScreenTool : SimpleTool<ScrollScreenArgs>(
    argsType = typeToken<ScrollScreenArgs>(),
    name = "scrollScreen",
    description = "Scroll the screen. Direction: 'up', 'down', 'forward', or 'backward'.",
) {
    override suspend fun execute(args: ScrollScreenArgs): String {
        val svc = requireService()
        return if (svc.scroll(args.direction)) "Scrolled ${args.direction}"
        else "Failed to scroll — no scrollable element found"
    }
}

class PressBackTool : SimpleTool<BackArgs>(
    argsType = typeToken<BackArgs>(),
    name = "pressBack",
    description = "Press the system Back button.",
) {
    override suspend fun execute(args: BackArgs): String {
        requireService().goBack()
        return "Pressed Back"
    }
}

class PressHomeTool : SimpleTool<HomeArgs>(
    argsType = typeToken<HomeArgs>(),
    name = "pressHome",
    description = "Press the system Home button to go to the home screen.",
) {
    override suspend fun execute(args: HomeArgs): String {
        requireService().goHome()
        return "Pressed Home"
    }
}

class OpenAppTool(private val context: Context) : SimpleTool<OpenAppArgs>(
    argsType = typeToken<OpenAppArgs>(),
    name = "openApp",
    description = "Open an app by name (e.g. 'Chrome', 'Camera', 'Telegram', 'Settings', 'Phone', 'Messages', 'Gallery', 'Maps') or package name.",
) {
    // Well-known intents for system apps that may not match by label
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
        "music" to Intent(Intent.ACTION_VIEW).apply { type = "audio/*" },
        "maps" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0")),
        "browser" to Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://google.com")),
        "calculator" to Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_APP_CALCULATOR)
        },
        "calendar" to Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_APP_CALENDAR)
        },
        "email" to Intent().apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_APP_EMAIL)
        },
        "files" to Intent(Intent.ACTION_VIEW).apply {
            type = "resource/folder"
        },
    )

    override suspend fun execute(args: OpenAppArgs): String {
        val name = args.name.trim()

        // 1. Try well-known intent
        val knownKey = knownIntents.keys.firstOrNull { name.contains(it, ignoreCase = true) }
        if (knownKey != null) {
            return try {
                val intent = knownIntents[knownKey]!!.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                "Opened: $knownKey"
            } catch (e: Exception) {
                // Fall through to package search
                tryByPackageOrLabel(name)
            }
        }

        // 2. Try by package name or label
        return tryByPackageOrLabel(name)
    }

    private fun tryByPackageOrLabel(name: String): String {
        // Try as package name
        val launchIntent = context.packageManager.getLaunchIntentForPackage(name)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            return "Opened app: $name"
        }

        // Search by app label
        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull {
            pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true)
        }
        if (match != null) {
            val intent = pm.getLaunchIntentForPackage(match.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return "Opened: ${pm.getApplicationLabel(match)} (${match.packageName})"
            }
        }
        return "App not found: $name. Try using the exact package name."
    }
}

class ListInstalledAppsTool(private val context: Context) : SimpleTool<ListAppsArgs>(
    argsType = typeToken<ListAppsArgs>(),
    name = "listInstalledApps",
    description = "List installed apps on the device.",
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

/** Returns all phone agent tools as a list for ToolRegistry. */
fun createPhoneAgentTools(context: Context): List<Tool<*, *>> = listOf(
    GetScreenContentTool(),
    TapAtIndexTool(),
    TapElementTool(),
    TypeTextTool(),
    ScrollScreenTool(),
    PressBackTool(),
    PressHomeTool(),
    OpenAppTool(context),
    ListInstalledAppsTool(context),
)
