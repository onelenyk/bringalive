package dev.onelenyk.bringalive.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "BootReceiver"

/**
 * Starts the foreground service automatically when the device boots.
 * Requires RECEIVE_BOOT_COMPLETED permission.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.i(TAG, "Boot completed — starting VoiceForegroundService")
            VoiceForegroundService.start(context)
        }
    }
}
