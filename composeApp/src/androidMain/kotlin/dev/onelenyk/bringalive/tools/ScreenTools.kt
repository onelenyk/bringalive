package dev.onelenyk.bringalive.tools

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.screen.OcrEngine
import dev.onelenyk.bringalive.screen.ScreenCaptureManager
import kotlinx.serialization.Serializable
import java.io.ByteArrayOutputStream

private const val TAG = "ScreenTools"

// ==================== Args ====================

@Serializable
data class CaptureScreenArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class OcrScreenArgs(val placeholder: String = "") : ToolArgs

// ==================== Tools ====================

class CaptureScreenTool(private val context: Context) : SimpleTool<CaptureScreenArgs>(
    argsType = typeToken<CaptureScreenArgs>(),
    name = "captureScreen",
    description = "Capture the current screen as an image. Returns a base64-encoded PNG string. Requires MediaProjection permission to be granted first.",
) {
    override suspend fun execute(args: CaptureScreenArgs): String {
        val manager = ScreenCaptureManager.instance
        if (!manager.isAvailable()) {
            return "ERROR: Screen capture permission not granted. Ask the user to grant screen capture permission in the app."
        }

        val bitmap = manager.captureScreen(context)
            ?: return "ERROR: Failed to capture screen. The capture returned null."

        return try {
            val base64 = bitmapToBase64(bitmap)
            Log.i(TAG, "Screen captured: ${bitmap.width}x${bitmap.height}, base64 length=${base64.length}")
            bitmap.recycle()
            "data:image/png;base64,$base64"
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap to base64", e)
            bitmap.recycle()
            "ERROR: Failed to encode screenshot: ${e.message}"
        }
    }
}

class OcrScreenTool(private val context: Context) : SimpleTool<OcrScreenArgs>(
    argsType = typeToken<OcrScreenArgs>(),
    name = "ocrScreen",
    description = "Capture the current screen and run OCR (optical character recognition) to extract all visible text. Returns detected text lines with their positions on screen. Uses on-device ML Kit — no network needed.",
) {
    private val ocrEngine = OcrEngine()

    override suspend fun execute(args: OcrScreenArgs): String {
        val manager = ScreenCaptureManager.instance
        if (!manager.isAvailable()) {
            return "ERROR: Screen capture permission not granted. Ask the user to grant screen capture permission in the app."
        }

        val bitmap = manager.captureScreen(context)
            ?: return "ERROR: Failed to capture screen. The capture returned null."

        return try {
            val results = ocrEngine.recognizeText(bitmap)
            bitmap.recycle()

            if (results.isEmpty()) {
                return "No text detected on screen."
            }

            val sb = StringBuilder()
            sb.appendLine("OCR Results (${results.size} text lines detected):")
            sb.appendLine("─".repeat(50))

            for ((index, result) in results.withIndex()) {
                val box = result.boundingBox
                val position = if (box != null) {
                    "  [x=${box.left}, y=${box.top}, w=${box.width()}, h=${box.height()}]"
                } else {
                    ""
                }
                val conf = if (result.confidence > 0f) {
                    " (conf=${String.format("%.0f%%", result.confidence * 100)})"
                } else {
                    ""
                }
                sb.appendLine("${index + 1}. \"${result.text}\"$conf$position")
            }

            Log.i(TAG, "OCR complete: ${results.size} lines")
            sb.toString().trimEnd()
        } catch (e: Exception) {
            Log.e(TAG, "OCR failed", e)
            bitmap.recycle()
            "ERROR: OCR failed: ${e.message}"
        }
    }
}

// ==================== Helpers ====================

private fun bitmapToBase64(bitmap: Bitmap): String {
    val stream = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
    val byteArray = stream.toByteArray()
    return Base64.encodeToString(byteArray, Base64.NO_WRAP)
}

// ==================== Registration ====================

fun createScreenTools(context: Context): List<Tool<*, *>> = listOf(
    CaptureScreenTool(context),
    OcrScreenTool(context),
)
