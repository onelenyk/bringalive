package dev.onelenyk.bringalive.tools

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import android.util.Log
import ai.koog.agents.core.tools.SimpleTool
import ai.koog.agents.core.tools.Tool
import ai.koog.agents.core.tools.ToolArgs
import ai.koog.serialization.typeToken
import dev.onelenyk.bringalive.service.NotificationService
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "NotificationTools"

// ==================== Args ====================

@Serializable
data class ListNotificationsArgs(val placeholder: String = "") : ToolArgs

@Serializable
data class ReplyToNotificationArgs(
    val key: String,
    val text: String,
) : ToolArgs

@Serializable
data class DismissNotificationArgs(val key: String) : ToolArgs

// ==================== Helpers ====================

private fun requireNotificationService(): NotificationService {
    val svc = NotificationService.instance
    if (svc == null) {
        Log.e(TAG, "Notification listener service is NOT enabled!")
        throw IllegalStateException(
            "Notification listener not enabled. Ask the user to enable it in Android Settings -> Notification access -> Bringalive."
        )
    }
    return svc
}

private fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ==================== Tools ====================

class ListNotificationsTool : SimpleTool<ListNotificationsArgs>(
    argsType = typeToken<ListNotificationsArgs>(),
    name = "listNotifications",
    description = "List all current notifications on the device. Returns package name, title, text, timestamp, available actions, and whether a direct reply is possible. Use the 'key' field to reply or dismiss.",
) {
    override suspend fun execute(args: ListNotificationsArgs): String {
        val svc = requireNotificationService()
        val notifications = svc.getNotifications()

        if (notifications.isEmpty()) {
            return "No active notifications."
        }

        val sb = StringBuilder()
        sb.appendLine("Active notifications (${notifications.size}):")
        sb.appendLine()

        for ((index, n) in notifications.withIndex()) {
            sb.appendLine("[${index + 1}] ${n.packageName}")
            sb.appendLine("    Key: ${n.key}")
            if (n.title != null) sb.appendLine("    Title: ${n.title}")
            if (n.text != null) sb.appendLine("    Text: ${n.text}")
            if (n.bigText != null && n.bigText != n.text) {
                sb.appendLine("    Full text: ${n.bigText.take(200)}")
            }
            if (n.subText != null) sb.appendLine("    Sub: ${n.subText}")
            sb.appendLine("    Time: ${formatTime(n.postTime)}")
            sb.appendLine("    Ongoing: ${n.isOngoing}")
            sb.appendLine("    Can reply: ${n.hasReplyAction}")
            sb.appendLine("    Clearable: ${n.isClearable}")
            if (n.actionLabels.isNotEmpty()) {
                sb.appendLine("    Actions: ${n.actionLabels.joinToString(", ")}")
            }
            sb.appendLine()
        }

        return sb.toString().trimEnd()
    }
}

class ReplyToNotificationTool : SimpleTool<ReplyToNotificationArgs>(
    argsType = typeToken<ReplyToNotificationArgs>(),
    name = "replyToNotification",
    description = "Reply to a messaging notification (WhatsApp, Telegram, SMS, etc.) using the notification's direct reply action. Requires the notification 'key' from listNotifications and the reply text.",
) {
    override suspend fun execute(args: ReplyToNotificationArgs): String {
        val svc = requireNotificationService()

        // Find the actual StatusBarNotification to get the live Notification object
        val sbn = svc.findNotificationByKey(args.key)
            ?: return "Notification not found for key: ${args.key}. It may have been dismissed. Use listNotifications to get current keys."

        val notification = sbn.notification
        val actions = notification.actions
        if (actions.isNullOrEmpty()) {
            return "This notification has no actions. Cannot reply."
        }

        // Find the action with RemoteInput (direct reply)
        val replyAction = actions.firstOrNull { action ->
            action.remoteInputs?.isNotEmpty() == true
        } ?: return "This notification has no reply action. Available actions: ${actions.map { it.title }.joinToString(", ")}"

        val remoteInputs = replyAction.remoteInputs
            ?: return "Reply action has no remote inputs."

        // Build the reply intent with RemoteInput data
        val replyIntent = Intent()
        val replyBundle = Bundle()

        for (remoteInput in remoteInputs) {
            replyBundle.putCharSequence(remoteInput.resultKey, args.text)
        }

        android.app.RemoteInput.addResultsToIntent(remoteInputs, replyIntent, replyBundle)

        return try {
            replyAction.actionIntent.send(
                svc,
                0,
                replyIntent,
            )
            Log.i(TAG, "Reply sent to ${sbn.packageName}: \"${args.text.take(50)}\"")
            "Reply sent successfully to ${sbn.packageName}: \"${args.text}\""
        } catch (e: PendingIntent.CanceledException) {
            Log.e(TAG, "Reply failed - PendingIntent was cancelled: ${e.message}")
            "Reply failed: the notification's reply action was cancelled. The notification may have been updated."
        } catch (e: Exception) {
            Log.e(TAG, "Reply failed: ${e.message}")
            "Reply failed: ${e.message}"
        }
    }
}

class DismissNotificationTool : SimpleTool<DismissNotificationArgs>(
    argsType = typeToken<DismissNotificationArgs>(),
    name = "dismissNotification",
    description = "Dismiss (clear) a notification by its key. Use listNotifications to find the key. Cannot dismiss ongoing or non-clearable notifications.",
) {
    override suspend fun execute(args: DismissNotificationArgs): String {
        val svc = requireNotificationService()

        // Check if notification exists and is clearable
        val notifications = svc.getNotifications()
        val target = notifications.firstOrNull { it.key == args.key }

        if (target == null) {
            return "Notification not found for key: ${args.key}. Use listNotifications to get current keys."
        }

        if (!target.isClearable) {
            return "Cannot dismiss this notification (${target.packageName}: ${target.title}) - it is marked as non-clearable/ongoing."
        }

        return if (svc.dismissNotification(args.key)) {
            Log.i(TAG, "Dismissed notification: ${target.packageName} - ${target.title}")
            "Dismissed notification from ${target.packageName}: ${target.title ?: "(no title)"}"
        } else {
            "Failed to dismiss notification: ${target.packageName}"
        }
    }
}

// ==================== Registration ====================

fun createNotificationTools(): List<Tool<*, *>> = listOf(
    ListNotificationsTool(),
    ReplyToNotificationTool(),
    DismissNotificationTool(),
)
