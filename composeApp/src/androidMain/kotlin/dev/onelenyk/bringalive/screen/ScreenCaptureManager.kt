package dev.onelenyk.bringalive.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "ScreenCaptureManager"

class ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    /** Whether the user has granted MediaProjection permission. */
    fun isAvailable(): Boolean = resultData != null && resultCode == Activity.RESULT_OK

    /**
     * Store the result from MediaProjectionManager's screen capture intent.
     * Called from MainActivity after the user grants permission.
     */
    fun setProjectionResult(resultCode: Int, data: Intent) {
        this.resultCode = resultCode
        this.resultData = data
        Log.i(TAG, "Projection result stored (code=$resultCode)")
    }

    /**
     * Capture the current screen as a Bitmap.
     * Creates a new MediaProjection + VirtualDisplay for each capture, then tears them down.
     */
    suspend fun captureScreen(context: Context): Bitmap? {
        val data = resultData ?: run {
            Log.e(TAG, "No projection result data — permission not granted")
            return null
        }
        if (resultCode != Activity.RESULT_OK) {
            Log.e(TAG, "Projection result code is not OK: $resultCode")
            return null
        }

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Create a fresh MediaProjection from the stored result
        val projection = try {
            projectionManager.getMediaProjection(resultCode, data.clone() as Intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MediaProjection", e)
            return null
        }

        if (projection == null) {
            Log.e(TAG, "MediaProjection is null")
            return null
        }

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null

        try {
            virtualDisplay = projection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                Handler(Looper.getMainLooper()),
            )

            // Wait for an image to arrive
            val bitmap = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<Bitmap?> { cont ->
                    imageReader.setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            try {
                                val planes = image.planes
                                val buffer = planes[0].buffer
                                val pixelStride = planes[0].pixelStride
                                val rowStride = planes[0].rowStride
                                val rowPadding = rowStride - pixelStride * width

                                val bmp = Bitmap.createBitmap(
                                    width + rowPadding / pixelStride,
                                    height,
                                    Bitmap.Config.ARGB_8888,
                                )
                                bmp.copyPixelsFromBuffer(buffer)

                                // Crop to actual screen size (remove row padding)
                                val cropped = if (rowPadding > 0) {
                                    Bitmap.createBitmap(bmp, 0, 0, width, height).also {
                                        if (it !== bmp) bmp.recycle()
                                    }
                                } else {
                                    bmp
                                }

                                if (cont.isActive) cont.resume(cropped)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing image", e)
                                if (cont.isActive) cont.resume(null)
                            } finally {
                                image.close()
                            }
                        }
                    }, Handler(Looper.getMainLooper()))

                    cont.invokeOnCancellation {
                        imageReader.setOnImageAvailableListener(null, null)
                    }
                }
            }

            Log.i(TAG, "Screen captured: ${bitmap?.width}x${bitmap?.height}")
            return bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error during screen capture", e)
            return null
        } finally {
            // Clean up
            try {
                virtualDisplay?.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing VirtualDisplay", e)
            }
            try {
                imageReader.setOnImageAvailableListener(null, null)
                imageReader.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing ImageReader", e)
            }
            try {
                projection.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping MediaProjection", e)
            }
        }
    }

    /**
     * Release any held resources. Call when the app is shutting down.
     */
    fun release() {
        mediaProjection?.stop()
        mediaProjection = null
        resultData = null
        resultCode = Activity.RESULT_CANCELED
        Log.i(TAG, "ScreenCaptureManager released")
    }

    companion object {
        @Volatile
        private var INSTANCE: ScreenCaptureManager? = null

        val instance: ScreenCaptureManager
            get() = INSTANCE ?: synchronized(this) {
                INSTANCE ?: ScreenCaptureManager().also { INSTANCE = it }
            }
    }
}
