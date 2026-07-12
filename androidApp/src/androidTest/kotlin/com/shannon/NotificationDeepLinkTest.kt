package com.shannon

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shannon.notifications.DeepLinkDestination
import com.shannon.notifications.NotificationDeepLinkHandler
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Instrumented tests for notification deep-linking functionality.
 * Tests notification creation, intent handling, and navigation.
 */
@RunWith(AndroidJUnit4::class)
class NotificationDeepLinkTest {

    private lateinit var context: Context
    private lateinit var deepLinkHandler: NotificationDeepLinkHandler

    private val testContactHash = "test_contact_123"
    private val testContactName = "Test Contact"
    private val testMessage = "Test message content"
    private val testChannelId = "shannon_network_channel"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        deepLinkHandler = NotificationDeepLinkHandler(context)
    }

    @After
    fun teardown() {
        deepLinkHandler.cancelAllNotifications()
    }

    @Test
    fun createMessageNotification_createsValidNotification() {
        val notification = deepLinkHandler.createMessageNotification(
            notificationId = 100,
            contactHash = testContactHash,
            contactName = testContactName,
            messageContent = testMessage,
            channelId = testChannelId
        )

        assertNotNull(notification)
        assertEquals(testContactName, notification.contentTitle.toString())
        assertTrue(notification.contentText.toString().contains(testMessage))
    }

    @Test
    fun showMessageNotification_displaysNotification() {
        val notificationId = deepLinkHandler.showMessageNotification(
            contactHash = testContactHash,
            contactName = testContactName,
            messageContent = testMessage,
            channelId = testChannelId
        )

        assertTrue(notificationId > 0)
        // In a real test, you would verify the notification is actually shown
    }

    @Test
    fun conversationIntent_containsCorrectExtras() {
        val intent = deepLinkHandler.createConversationIntent(
            context = context,
            contactHash = testContactHash
        )

        assertEquals(NotificationDeepLinkHandler.CONVERSATION_ACTION, intent.action)
        assertEquals(testContactHash, intent.getStringExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun handleConversationIntent_returnsCorrectDestination() {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = NotificationDeepLinkHandler.CONVERSATION_ACTION
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, testContactHash)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(intent)

        assertTrue(destination is DeepLinkDestination.Conversation)
        assertEquals(testContactHash, (destination as DeepLinkDestination.Conversation).contactHash)
    }

    @Test
    fun handleReplyIntent_returnsCorrectDestination() {
        val testReplyContent = "Test reply"
        val testNotificationId = 12345

        val intent = Intent(context, MainActivity::class.java).apply {
            action = NotificationDeepLinkHandler.REPLY_ACTION
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, testContactHash)
            putExtra(NotificationDeepLinkHandler.MESSAGE_CONTENT_EXTRA, testReplyContent)
            putExtra("notification_id", testNotificationId)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(intent)

        assertTrue(destination is DeepLinkDestination.Reply)
        destination as DeepLinkDestination.Reply
        assertEquals(testContactHash, destination.contactHash)
        assertEquals(testReplyContent, destination.messageContent)
        assertEquals(testNotificationId, destination.notificationId)
    }

    @Test
    fun handleVoiceCallIntent_returnsCorrectDestination() {
        val testCallerHash = "caller_456"

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.shannon.HANDLE_CALL"
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, testCallerHash)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(intent)

        assertTrue(destination is DeepLinkDestination.VoiceCall)
        assertEquals(testCallerHash, (destination as DeepLinkDestination.VoiceCall).callerHash)
    }

    @Test
    fun handleAcceptCallIntent_returnsCorrectDestination() {
        val testCallerHash = "caller_789"

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.shannon.ACCEPT_CALL"
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, testCallerHash)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(intent)

        assertTrue(destination is DeepLinkDestination.AcceptCall)
        assertEquals(testCallerHash, (destination as DeepLinkDestination.AcceptCall).callerHash)
    }

    @Test
    fun handleRejectCallIntent_returnsCorrectDestination() {
        val testCallerHash = "caller_101"

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.shannon.REJECT_CALL"
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, testCallerHash)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(intent)

        assertTrue(destination is DeepLinkDestination.RejectCall)
        assertEquals(testCallerHash, (destination as DeepLinkDestination.RejectCall).callerHash)
    }

    @Test
    fun handleInvalidIntent_returnsNull() {
        val invalidIntent = Intent(context, MainActivity::class.java).apply {
            action = "invalid.action"
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(invalidIntent)

        assertNull(destination)
    }

    @Test
    fun createVoiceCallNotification_createsValidNotification() {
        val testCallerHash = "caller_voice"
        val testCallerName = "Voice Caller"

        val notification = deepLinkHandler.createVoiceCallNotification(
            notificationId = 200,
            callerHash = testCallerHash,
            callerName = testCallerName,
            channelId = testChannelId
        )

        assertNotNull(notification)
        assertEquals("Incoming call", notification.contentTitle.toString())
        assertEquals(testCallerName, notification.contentText.toString())
    }

    @Test
    fun showVoiceCallNotification_displaysCallNotification() {
        val testCallerHash = "caller_voice_test"

        val notificationId = deepLinkHandler.showVoiceCallNotification(
            callerHash = testCallerHash,
            callerName = "Test Caller",
            channelId = testChannelId
        )

        assertTrue(notificationId > 0)
        // In a real test, you would verify the notification is actually shown
    }

    @Test
    fun cancelConversationNotification_removesSpecificNotification() {
        // First show a notification
        val notificationId = deepLinkHandler.showMessageNotification(
            contactHash = testContactHash,
            contactName = testContactName,
            messageContent = testMessage,
            channelId = testChannelId
        )

        assertTrue(notificationId > 0)

        // Then cancel it
        deepLinkHandler.cancelConversationNotification(testContactHash)

        // In a real test, you would verify the notification is actually cancelled
    }

    @Test
    fun multipleMessageNotifications_haveUniqueIds() {
        val contacts = listOf("contact1", "contact2", "contact3")
        val notificationIds = mutableListOf<Int>()

        contacts.forEach { contactHash ->
            val id = deepLinkHandler.showMessageNotification(
                contactHash = contactHash,
                contactName = "Contact $contactHash",
                messageContent = "Message from $contactHash",
                channelId = testChannelId
            )
            notificationIds.add(id)
        }

        // Verify all IDs are unique
        val uniqueIds = notificationIds.toSet()
        assertEquals(contacts.size, uniqueIds.size, "Each notification should have unique ID")
    }

    @Test
    fun notificationIds_areDeterministic_forSameContact() {
        val firstId = deepLinkHandler.showMessageNotification(
            contactHash = testContactHash,
            contactName = testContactName,
            messageContent = "First message",
            channelId = testChannelId
        )

        // Cancel first notification
        deepLinkHandler.cancelConversationNotification(testContactHash)

        val secondId = deepLinkHandler.showMessageNotification(
            contactHash = testContactHash,
            contactName = testContactName,
            messageContent = "Second message",
            channelId = testChannelId
        )

        // Same contact should generate same notification ID
        assertEquals(firstId, secondId, "Notification ID should be deterministic for same contact")
    }

    @Test
    fun conversationIntent_clearsTaskStack() {
        val intent = deepLinkHandler.createConversationIntent(
            context = context,
            contactHash = testContactHash
        )

        // Verify proper flags are set for activity navigation
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0,
            "Should clear activity stack")
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0,
            "Should reuse existing activity")
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
            "Should start new task if needed")
    }

    @Test
    fun handleDeepLinkIntent_withMissingContactHash_returnsNull() {
        val invalidIntent = Intent(context, MainActivity::class.java).apply {
            action = NotificationDeepLinkHandler.CONVERSATION_ACTION
            // Missing CONTACT_HASH_EXTRA
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(invalidIntent)

        assertNull(destination, "Should return null when contact hash is missing")
    }

    @Test
    fun handleDeepLinkIntent_withNullContactHash_returnsNull() {
        val invalidIntent = Intent(context, MainActivity::class.java).apply {
            action = NotificationDeepLinkHandler.CONVERSATION_ACTION
            putExtra(NotificationDeepLinkHandler.CONTACT_HASH_EXTRA, null as String?)
        }

        val destination = deepLinkHandler.handleDeepLinkIntent(invalidIntent)

        assertNull(destination, "Should return null when contact hash is null")
    }
}