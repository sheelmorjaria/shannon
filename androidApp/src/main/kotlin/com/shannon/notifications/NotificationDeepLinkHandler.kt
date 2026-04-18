package com.shannon.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.shannon.MainActivity
import com.shannon.R

/**
 * Handles notification deep-linking to specific conversations.
 * Allows users to tap on notifications and be taken directly to relevant chats.
 */
class NotificationDeepLinkHandler(private val context: Context) {

    companion object {
        const val CONVERSATION_ACTION = "com.shannon.OPEN_CONVERSATION"
        const val REPLY_ACTION = "com.shannon.REPLY_TO_MESSAGE"
        const val CONTACT_HASH_EXTRA = "contact_hash"
        const val MESSAGE_CONTENT_EXTRA = "message_content"
        const val NOTIFICATION_ID_BASE = 2000 // Base ID for message notifications
    }

    /**
     * Create a notification for a new message that deep-links to the conversation.
     */
    fun createMessageNotification(
        notificationId: Int,
        contactHash: String,
        contactName: String,
        messageContent: String,
        channelId: String
    ): Notification {

        // Create intent to open conversation
        val conversationIntent = createConversationIntent(context, contactHash)
        val conversationPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            conversationIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create reply intent (if supported)
        val replyPendingIntent = createReplyPendingIntent(
            context,
            notificationId,
            contactHash,
            messageContent
        )

        // Build notification with conversation deep-link
        val builder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(contactName)
            .setContentText(messageContent)
            .setSmallIcon(R.drawable.ic_network_notification)
            .setContentIntent(conversationPendingIntent)
            .setAutoCancel(true) // Remove notification when tapped
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis())

        // Add reply action if supported (Android 7.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            // Note: Full text input would require RemoteInput setup
            builder.addAction(
                R.drawable.ic_network_notification,
                "Reply",
                replyPendingIntent
            )
        }

        return builder.build()
    }

    /**
     * Create a notification for an incoming voice call.
     */
    fun createVoiceCallNotification(
        notificationId: Int,
        callerHash: String,
        callerName: String,
        channelId: String
    ): Notification {

        // Create intent to open app and handle call
        val callIntent = createCallIntent(context, callerHash)
        val callPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            callIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create actions for call handling
        val acceptIntent = createCallActionIntent(context, callerHash, true)
        val acceptPendingIntent = PendingIntent.getService(
            context,
            notificationId * 10 + 1,
            acceptIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val rejectIntent = createCallActionIntent(context, callerHash, false)
        val rejectPendingIntent = PendingIntent.getService(
            context,
            notificationId * 10 + 2,
            rejectIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Incoming call")
            .setContentText(callerName)
            .setSmallIcon(R.drawable.ic_network_notification)
            .setContentIntent(callPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .addAction(R.drawable.ic_network_notification, "Accept", acceptPendingIntent)
            .addAction(R.drawable.ic_network_notification, "Reject", rejectPendingIntent)
            .setOngoing(true) // Don't allow swipe dismissal
            .build()
    }

    /**
     * Create intent to open specific conversation.
     */
    private fun createConversationIntent(
        context: Context,
        contactHash: String
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                   Intent.FLAG_ACTIVITY_SINGLE_TOP or
                   Intent.FLAG_ACTIVITY_NEW_TASK
            action = CONVERSATION_ACTION
            putExtra(CONTACT_HASH_EXTRA, contactHash)
        }
    }

    /**
     * Create intent to handle voice call.
     */
    private fun createCallIntent(
        context: Context,
        callerHash: String
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                   Intent.FLAG_ACTIVITY_SINGLE_TOP or
                   Intent.FLAG_ACTIVITY_NEW_TASK
            action = "com.shannon.HANDLE_CALL"
            putExtra(CONTACT_HASH_EXTRA, callerHash)
        }
    }

    /**
     * Create intent for call action (accept/reject).
     */
    private fun createCallActionIntent(
        context: Context,
        callerHash: String,
        accept: Boolean
    ): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = if (accept) "com.shannon.ACCEPT_CALL" else "com.shannon.REJECT_CALL"
            putExtra(CONTACT_HASH_EXTRA, callerHash)
        }
    }

    /**
     * Create pending intent for reply action.
     */
    private fun createReplyPendingIntent(
        context: Context,
        notificationId: Int,
        contactHash: String,
        messageContent: String
    ): PendingIntent? {
        val replyIntent = Intent(context, MainActivity::class.java).apply {
            action = REPLY_ACTION
            putExtra(CONTACT_HASH_EXTRA, contactHash)
            putExtra(MESSAGE_CONTENT_EXTRA, messageContent)
            putExtra("notification_id", notificationId)
        }

        return PendingIntent.getActivity(
            context,
            notificationId,
            replyIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Handle deep-link intent and return navigation destination.
     */
    fun handleDeepLinkIntent(intent: Intent): DeepLinkDestination? {
        return when (intent.action) {
            CONVERSATION_ACTION -> {
                val contactHash = intent.getStringExtra(CONTACT_HASH_EXTRA)
                if (contactHash != null) {
                    DeepLinkDestination.Conversation(contactHash)
                } else {
                    null
                }
            }
            "com.shannon.HANDLE_CALL" -> {
                val callerHash = intent.getStringExtra(CONTACT_HASH_EXTRA)
                if (callerHash != null) {
                    DeepLinkDestination.VoiceCall(callerHash)
                } else {
                    null
                }
            }
            "com.shannon.ACCEPT_CALL" -> {
                val callerHash = intent.getStringExtra(CONTACT_HASH_EXTRA)
                if (callerHash != null) {
                    DeepLinkDestination.AcceptCall(callerHash)
                } else {
                    null
                }
            }
            "com.shannon.REJECT_CALL" -> {
                val callerHash = intent.getStringExtra(CONTACT_HASH_EXTRA)
                if (callerHash != null) {
                    DeepLinkDestination.RejectCall(callerHash)
                } else {
                    null
                }
            }
            REPLY_ACTION -> {
                val contactHash = intent.getStringExtra(CONTACT_HASH_EXTRA)
                val messageContent = intent.getStringExtra(MESSAGE_CONTENT_EXTRA)
                val notificationId = intent.getIntExtra("notification_id", -1)
                if (contactHash != null && notificationId != -1) {
                    DeepLinkDestination.Reply(contactHash, messageContent ?: "", notificationId)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    /**
     * Show message notification and return notification ID.
     */
    fun showMessageNotification(
        contactHash: String,
        contactName: String,
        messageContent: String,
        channelId: String
    ): Int {
        val notificationId = NOTIFICATION_ID_BASE + contactHash.hashCode()
        val notification = createMessageNotification(
            notificationId,
            contactHash,
            contactName,
            messageContent,
            channelId
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        return notificationId
    }

    /**
     * Show voice call notification.
     */
    fun showVoiceCallNotification(
        callerHash: String,
        callerName: String,
        channelId: String
    ): Int {
        val notificationId = NOTIFICATION_ID_BASE + callerHash.hashCode() + 1000
        val notification = createVoiceCallNotification(
            notificationId,
            callerHash,
            callerName,
            channelId
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notification)

        return notificationId
    }

    /**
     * Cancel notification for specific conversation.
     */
    fun cancelConversationNotification(contactHash: String) {
        val notificationId = NOTIFICATION_ID_BASE + contactHash.hashCode()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)
    }

    /**
     * Cancel all Shannon notifications.
     */
    fun cancelAllNotifications() {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancelAll()
    }
}

/**
 * Represents a deep-link destination from notification tap.
 */
sealed class DeepLinkDestination {
    data class Conversation(val contactHash: String) : DeepLinkDestination()
    data class VoiceCall(val callerHash: String) : DeepLinkDestination()
    data class AcceptCall(val callerHash: String) : DeepLinkDestination()
    data class RejectCall(val callerHash: String) : DeepLinkDestination()
    data class Reply(val contactHash: String, val messageContent: String, val notificationId: Int) : DeepLinkDestination()
}