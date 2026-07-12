package com.shannon.ui

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ChatScreenUiTest {

    private val testContactHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    @Test
    fun `outgoing message bubble is displayed`() = runDesktopComposeUiTest {
        val message = Message(
            destinationHash = testContactHash,
            content = "Hello from me",
            timestamp = 1000L,
            state = MessageState.SENT,
            isOutgoing = true
        )

        setContent {
            MessageBubble(message = message)
        }

        onNodeWithText("Hello from me").assertIsDisplayed()
    }

    @Test
    fun `incoming message bubble is displayed`() = runDesktopComposeUiTest {
        val message = Message(
            destinationHash = testContactHash,
            content = "Hello from remote",
            timestamp = 1000L,
            state = MessageState.SENT,
            isOutgoing = false
        )

        setContent {
            MessageBubble(message = message)
        }

        onNodeWithText("Hello from remote").assertIsDisplayed()
    }

    @Test
    fun `empty state shows no messages placeholder`() = runDesktopComposeUiTest {
        setContent {
            ConversationScreen(messages = emptyList())
        }

        onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test
    fun `non-empty state does not show placeholder`() = runDesktopComposeUiTest {
        val messages = listOf(
            Message(
                destinationHash = testContactHash,
                content = "Existing message",
                timestamp = 1000L,
                state = MessageState.SENT,
                isOutgoing = true
            )
        )

        setContent {
            ConversationScreen(messages = messages)
        }

        // "No messages yet" should NOT be displayed
        onNodeWithText("No messages yet").assertDoesNotExist()
        // But the existing message should be visible
        onNodeWithText("Existing message").assertIsDisplayed()
    }

    @Test
    fun `multiple messages are displayed`() = runDesktopComposeUiTest {
        val messages = listOf(
            Message(destinationHash = testContactHash, content = "First", timestamp = 1000L, state = MessageState.SENT, isOutgoing = true),
            Message(destinationHash = testContactHash, content = "Second", timestamp = 2000L, state = MessageState.SENT, isOutgoing = false),
            Message(destinationHash = testContactHash, content = "Third", timestamp = 3000L, state = MessageState.SENT, isOutgoing = true)
        )

        setContent {
            ConversationScreen(messages = messages)
        }

        onNodeWithText("First").assertIsDisplayed()
        onNodeWithText("Second").assertIsDisplayed()
        onNodeWithText("Third").assertIsDisplayed()
    }

    @Test
    fun `dark theme renders text visible against background`() = runDesktopComposeUiTest {
        val message = Message(
            destinationHash = testContactHash,
            content = "Dark theme test",
            timestamp = 1000L,
            state = MessageState.SENT,
            isOutgoing = true
        )

        setContent {
            ShannonTheme(darkTheme = true) {
                MessageBubble(message = message)
            }
        }

        onNodeWithText("Dark theme test").assertIsDisplayed()
    }

    @Test
    fun `light theme renders text visible`() = runDesktopComposeUiTest {
        val message = Message(
            destinationHash = testContactHash,
            content = "Light theme test",
            timestamp = 1000L,
            state = MessageState.SENT,
            isOutgoing = false
        )

        setContent {
            ShannonTheme(darkTheme = false) {
                MessageBubble(message = message)
            }
        }

        onNodeWithText("Light theme test").assertIsDisplayed()
    }

    @Test
    fun `send button is displayed`() = runDesktopComposeUiTest {
        setContent {
            ConversationScreen(messages = emptyList())
        }

        onNodeWithText("Send").assertIsDisplayed()
    }
}
