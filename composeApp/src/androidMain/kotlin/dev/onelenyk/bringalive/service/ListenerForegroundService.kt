package dev.onelenyk.bringalive.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import dev.onelenyk.bringalive.MainActivity
import dev.onelenyk.bringalive.listener.AndroidPassiveListenerEngine
import dev.onelenyk.bringalive.listener.ListenerState
import dev.onelenyk.bringalive.listener.PassiveListenerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.java.KoinJavaComponent.getKoin

private const val TAG = "ListenerService"
private const val CHANNEL_ID = "bringalive_listener"
private const val NOTIFICATION_ID = 1002

class ListenerForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Listener service created")
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Passive listener ready"))
        observeListenerState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Stop requested")
                val engine = getKoin().get<PassiveListenerEngine>()
                engine.stopListening()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.i(TAG, "Listener service destroyed")
        stateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun observeListenerState() {
        val engine = getKoin().get<PassiveListenerEngine>()
        stateJob = scope.launch {
            engine.state.collect { state ->
                val text = when (state) {
                    ListenerState.IDLE -> "Passive listener ready"
                    ListenerState.LISTENING -> "Listening for speech..."
                    ListenerState.RECORDING -> "Recording speech..."
                    ListenerState.TRANSCRIBING -> "Transcribing..."
                }
                updateNotification(text)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Passive Listener",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows when bringalive passive listener is active"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, ListenerForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("bringalive — listener")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openPi)
            .addAction(android.R.drawable.ic_delete, "Stop", stopPi)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    companion object {
        const val ACTION_STOP = "dev.onelenyk.bringalive.STOP_LISTENER"

        fun start(context: Context) {
            val intent = Intent(context, ListenerForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ListenerForegroundService::class.java))
        }
    }
}
