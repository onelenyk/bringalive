package dev.onelenyk.bringalive

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import dev.onelenyk.bringalive.service.VoiceForegroundService
import dev.onelenyk.bringalive.ui.App
import dev.onelenyk.bringalive.voice.AndroidVoiceEngine
import dev.onelenyk.bringalive.voice.VoiceEngine
import org.koin.java.KoinJavaComponent.getKoin

class MainActivity : ComponentActivity() {

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val engine = getKoin().get<VoiceEngine>()
            engine.startListening()
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

        setContent {
            App()
        }
    }
}
