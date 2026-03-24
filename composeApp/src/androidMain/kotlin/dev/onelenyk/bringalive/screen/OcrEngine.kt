package dev.onelenyk.bringalive.screen

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val TAG = "OcrEngine"

data class OcrResult(
    val text: String,
    val boundingBox: Rect?,
    val confidence: Float,
)

class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Run OCR on a bitmap and return detected text blocks with positions.
     * Uses Google ML Kit on-device text recognition (no network needed).
     */
    suspend fun recognizeText(bitmap: Bitmap): List<OcrResult> {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        return suspendCancellableCoroutine { cont ->
            recognizer.process(inputImage)
                .addOnSuccessListener { visionText ->
                    val results = mutableListOf<OcrResult>()

                    for (block in visionText.textBlocks) {
                        for (line in block.lines) {
                            // Use line-level granularity for a good balance
                            // between detail and readability
                            val confidence = line.confidence ?: 0f
                            results.add(
                                OcrResult(
                                    text = line.text,
                                    boundingBox = line.boundingBox,
                                    confidence = confidence,
                                )
                            )
                        }
                    }

                    Log.i(TAG, "OCR complete: ${results.size} lines detected")
                    if (cont.isActive) cont.resume(results)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "OCR failed", e)
                    if (cont.isActive) cont.resumeWithException(e)
                }

            cont.invokeOnCancellation {
                // ML Kit tasks are not directly cancellable,
                // but we prevent the callback from resuming
            }
        }
    }

    /**
     * Run OCR and return all text concatenated (simple convenience method).
     */
    suspend fun recognizeFullText(bitmap: Bitmap): String {
        val results = recognizeText(bitmap)
        return results.joinToString("\n") { it.text }
    }

    fun close() {
        recognizer.close()
    }
}
