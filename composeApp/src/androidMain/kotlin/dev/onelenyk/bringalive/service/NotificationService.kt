package dev.onelenyk.bringalive.service

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

private const val TAG = "NotificationService"

/**
 * Listens to all notifications on the device.
 * Maintains a thread-safe list of active notifications that can be queried by agent tools.
 */
class NotificationService : NotificationListenerService() {

    private val activeNotifications = java.util.concurrent.ConcurrentHashMap<String, NotificationData>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        Log.i(TAG, "Notification listener connected")

        // Populate with existing active notifications
        try {
            for (sbn in getActiveNotifications()) {
                activeNotifications[sbn.key] = sbn.toNotificationData()
            }
            Log.i(TAG, "Loaded ${activeNotifications.size} existing notifications")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load existing notifications: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val data = sbn.toNotificationData()
        activeNotifications[sbn.key] = data
        Log.d(TAG, "Notification posted: ${data.packageName} - ${data.title}")
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        activeNotifications.remove(sbn.key)
        Log.d(TAG, "Notification removed: ${sbn.packageName} - key=${sbn.key}")
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.i(TAG, "Notification listener disconnected")
        if (instance == this) instance = null
        activeNotifications.clear()
    }

    // ========== Public API for tools ==========

    fun getNotifications(): List<NotificationData> {
        return activeNotifications.values.toList().sortedByDescending { it.postTime }
    }

    fun dismissNotification(key: String): Boolean {
        return try {
            cancelNotification(key)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss notification: ${e.message}")
            false
        }
    }

    fun findNotificationByKey(key: String): StatusBarNotification? {
        return try {
            getActiveNotifications().firstOrNull { it.key == key }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find notification: ${e.message}")
            null
        }
    }

    // ========== Helpers ==========

    private fun StatusBarNotification.toNotificationData(): NotificationData {
        val extras = notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()

        // Check for reply actions (direct reply via RemoteInput)
        val replyAction = notification.actions?.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        }
        val hasReplyAction = replyAction != null

        // Get all action labels
        val actionLabels = notification.actions?.map { it.title?.toString() ?: "unknown" } ?: emptyList()

        return NotificationData(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            bigText = bigText,
            subText = subText,
            infoText = infoText,
            postTime = postTime,
            isOngoing = isOngoing,
            hasReplyAction = hasReplyAction,
            actionLabels = actionLabels,
            isClearable = isClearable,
        )
    }

    companion object {
        @Volatile
        var instance: NotificationService? = null
            private set

        val isEnabled: Boolean get() = instance != null
    }
}

/**
 * Lightweight data class holding parsed notification information.
 * Thread-safe since it's immutable.
 */
data class NotificationData(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val subText: String?,
    val infoText: String?,
    val postTime: Long,
    val isOngoing: Boolean,
    val hasReplyAction: Boolean,
    val actionLabels: List<String>,
    val isClearable: Boolean,
)
