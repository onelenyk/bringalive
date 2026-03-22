package dev.onelenyk.bringalive.tools

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import kotlinx.serialization.Serializable

// --- Args ---

@Serializable
data class SetClipboardArgs(val text: String) : ToolArgs

@Serializable
data class ReadClipboardArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class OpenUrlArgs(val url: String) : ToolArgs

@Serializable
data class ShareTextArgs(val text: String, val subject: String = "") : ToolArgs

@Serializable
data class DeviceInfoArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class SetBrightnessArgs(val level: Int) : ToolArgs

// --- Tools ---

class SetClipboardTool(private val context: Context) : SimpleTool<SetClipboardArgs>(
    argsType = typeToken<SetClipboardArgs>(),
    name = "setClipboard",
    description = "Copy text to the device clipboard.",
) {
    override suspend fun execute(args: SetClipboardArgs): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("bringalive", args.text))
        return "Copied to clipboard: \"${args.text.take(100)}\""
    }
}

class ReadClipboardTool(private val context: Context) : SimpleTool<ReadClipboardArgs>(
    argsType = typeToken<ReadClipboardArgs>(),
    name = "readClipboard",
    description = "Read the current text from the device clipboard.",
) {
    override suspend fun execute(args: ReadClipboardArgs): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip == null || clip.itemCount == 0) return "Clipboard is empty."
        val text = clip.getItemAt(0).text?.toString() ?: return "Clipboard contains non-text data."
        return "Clipboard content: \"$text\""
    }
}

class OpenUrlTool(private val context: Context) : SimpleTool<OpenUrlArgs>(
    argsType = typeToken<OpenUrlArgs>(),
    name = "openUrl",
    description = "Open a URL in the default browser. Can also open tel:, mailto:, geo: URIs.",
) {
    override suspend fun execute(args: OpenUrlArgs): String {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(args.url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            "Opened: ${args.url}"
        } catch (e: Exception) {
            "Failed to open URL: ${e.message}"
        }
    }
}

class ShareTextTool(private val context: Context) : SimpleTool<ShareTextArgs>(
    argsType = typeToken<ShareTextArgs>(),
    name = "shareText",
    description = "Share text via the Android share sheet (can share to any app — messages, email, notes, etc.).",
) {
    override suspend fun execute(args: ShareTextArgs): String {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, args.text)
            if (args.subject.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, args.subject)
            }
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Share via").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Opened share sheet with text"
    }
}

class GetDeviceInfoTool(private val context: Context) : SimpleTool<DeviceInfoArgs>(
    argsType = typeToken<DeviceInfoArgs>(),
    name = "getDeviceInfo",
    description = "Get device information: model, Android version, battery level, screen brightness.",
) {
    override suspend fun execute(args: DeviceInfoArgs): String {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val battery = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

        val brightness = try {
            val raw = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            "${(raw * 100 / 255)}%"
        } catch (_: Exception) {
            "unknown"
        }

        return buildString {
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Battery: $battery%")
            appendLine("Brightness: $brightness")
        }.trimEnd()
    }
}

/** Returns all device tools as a list for ToolRegistry. */
fun createDeviceTools(context: Context): List<Tool<*, *>> = listOf(
    SetClipboardTool(context),
    ReadClipboardTool(context),
    OpenUrlTool(context),
    ShareTextTool(context),
    GetDeviceInfoTool(context),
)
