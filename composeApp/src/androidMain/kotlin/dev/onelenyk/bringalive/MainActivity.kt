package dev.onelenyk.bringalive

import android.Manifest
import android.app.Activity
import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import dev.onelenyk.bringalive.screen.ScreenCaptureManager
import dev.onelenyk.bringalive.service.VoiceForegroundService
import dev.onelenyk.bringalive.ui.App
import dev.onelenyk.bringalive.voice.AndroidVoiceEngine
import dev.onelenyk.bringalive.voice.VoiceEngine
import org.koin.java.KoinJavaComponent.getKoin

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val engine = getKoin().get<VoiceEngine>()
            engine.startListening()
        }
    }

    private val screenCaptureLauncher: ActivityResultLauncher<android.content.Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                Log.i(TAG, "Screen capture permission granted")
                ScreenCaptureManager.instance.setProjectionResult(result.resultCode, result.data!!)
            } else {
                Log.w(TAG, "Screen capture permission denied (resultCode=${result.resultCode})")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val engine = getKoin().get<VoiceEngine>()
        if (engine is AndroidVoiceEngine) {
            // Wire permission request
            engine.onPermissionNeeded = {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
            // Wire foreground service start/stop
            engine.onServiceStart = {
                VoiceForegroundService.start(this)
            }
            engine.onServiceStop = {
                VoiceForegroundService.stop(this)
            }
        }

        // Request screen capture permission on startup so tools can use it
        requestScreenCapturePermission()

        setContent {
            App()
        }
    }

    /**
     * Request MediaProjection screen capture permission.
     * This shows a system dialog asking the user to allow screen recording.
     */
    fun requestScreenCapturePermission() {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = projectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }
}
