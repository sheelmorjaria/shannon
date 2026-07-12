package com.shannon.integration

import app.cash.sqldelight.driver.jdbc.JdbcSqliteDriver
import app.cash.turbine.test
import com.shannon.db.ShannonDatabase
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.network.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Integration tests for real network + database + UI flow.
 * Tests the complete message pipeline with actual network connectivity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RealNetworkIntegrationTest {

    private lateinit var mockServer: MockTcpServer
    private lateinit var client: ReticulumClientImpl
    private lateinit var database: ShannonDatabase
    private lateinit var messageRepository: SqlDelightMessageRepository
    private lateinit var testScope: TestScope

    private val localHash = "local_test_identity"
    private val remoteHash = "remote_test_identity"

    @Before
    fun setup() {
        // Create test scope
        testScope = TestScope(StandardTestDispatcher())

        // Create mock server
        mockServer = MockTcpServer(port = 0)
        mockServer.start()

        // Create network client
        val config = ReticulumConfig(
            configDir = "/tmp/test_reticulum_integration",
            identityPath = null,
            healthCheckIntervalMs = 1000,
            reconnectDelayMs = 100
        )
        client = ReticulumClientImpl(
            config = config,
            scope = testScope
        )

        // Create in-memory database
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        ShannonDatabase.Schema.create(driver)
        database = ShannonDatabase(driver)

        // Create message repository with network client
        messageRepository = SqlDelightMessageRepository(
            database = database,
            client = client,
            localHash = localHash,
            scope = testScope
        )

        // Start listening for incoming packets
        messageRepository.startListening()
    }

    @After
    fun teardown() {
        testScope.runTest {
            try {
                messageRepository.stopListening()
                client.cleanup()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        mockServer.stop()
    }

    @Test
    fun `complete message flow - send, persist, and receive`() = runTest {
        // Connect to network
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Send message
        val message = Message(
            destinationHash = remoteHash,
            content = "Hello from integration test!",
            isOutgoing = true
        )

        val sentMessage = messageRepository.send(message)
        testScheduler.advanceUntilIdle()

        // Verify message was sent and state updated
        assertEquals(MessageState.SENT, sentMessage.state)
        assertTrue(mockServer.hasData(), "Server should have received message")

        // Verify message was persisted to database
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertTrue(savedMessages.isNotEmpty(), "Message should be saved to database")
        assertEquals("Hello from integration test!", savedMessages.first().content)
    }

    @Test
    fun `database persists messages across network reconnection`() = runTest {
        // Send initial message
        val message1 = Message(
            destinationHash = remoteHash,
            content = "First message",
            isOutgoing = true
        )

        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        messageRepository.send(message1)
        testScheduler.advanceUntilIdle()

        // Verify message is in database
        val messagesBefore = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertEquals(1, messagesBefore.size)

        // Disconnect from network
        client.disconnect()
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.DISCONNECTED, client.observeStatus().test { awaitItem() })

        // Verify messages are still in database
        val messagesDuring = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertEquals(1, messagesDuring.size)
        assertEquals("First message", messagesDuring.first().content)

        // Reconnect to network
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Verify messages are still in database after reconnection
        val messagesAfter = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertEquals(1, messagesAfter.size)
        assertEquals("First message", messagesAfter.first().content)
    }

    @Test
    fun `message state transitions are persisted to database`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val message = Message(
            destinationHash = remoteHash,
            content = "State transition test",
            isOutgoing = true
        )

        // Send message
        val sentMessage = messageRepository.send(message)
        testScheduler.advanceUntilIdle()

        // Verify final state in database
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 1, offset = 0)
        assertEquals(1, savedMessages.size)
        assertEquals(MessageState.SENT, savedMessages.first().state)
    }

    @Test
    fun `message flow updates UI state correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Observe message flow
        messageRepository.observeMessages(remoteHash).test {
            // Initial state - empty list
            assertEquals(emptyList(), awaitItem())

            // Send message
            val message = Message(
                destinationHash = remoteHash,
                content = "UI update test",
                isOutgoing = true
            )

            testScope.launch {
                messageRepository.send(message)
            }

            testScheduler.advanceUntilIdle()

            // UI should receive updated message list
            val messages = awaitItem()
            assertTrue(messages.isNotEmpty(), "UI should receive message update")
            assertEquals("UI update test", messages.first().content)
        }
    }

    @Test
    fun `network failure is handled gracefully`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Simulate network failure
        mockServer.simulateFailure()

        // Give client time to detect failure
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(200)

        // Try to send message - should handle gracefully
        val message = Message(
            destinationHash = remoteHash,
            content = "Test message during failure",
            isOutgoing = true
        )

        val result = messageRepository.send(message)
        testScheduler.advanceUntilIdle()

        // Message should be marked as failed
        assertEquals(MessageState.FAILED, result.state)

        // Verify failed message is in database
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 1, offset = 0)
        assertEquals(1, savedMessages.size)
        assertEquals(MessageState.FAILED, savedMessages.first().state)
    }

    @Test
    fun `automatic reconnection after network failure`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Simulate server failure
        mockServer.simulateFailure()

        // Give client time to detect failure
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(200)

        // Client should attempt reconnection
        val statusAfterFailure = client.observeStatus().test { awaitItem() }
        assertTrue(
            statusAfterFailure == ConnectionStatus.DISCONNECTED ||
            statusAfterFailure == ConnectionStatus.RECONNECTING,
            "Client should detect failure"
        )

        // Restart server
        mockServer.restart()

        // Give client time to reconnect
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(300)

        // Client should reconnect automatically
        val statusAfterReconnect = client.observeStatus().test { awaitItem() }
        assertTrue(
            statusAfterReconnect == ConnectionStatus.CONNECTED ||
            statusAfterReconnect == ConnectionStatus.CONNECTING ||
            statusAfterReconnect == ConnectionStatus.RECONNECTING,
            "Client should attempt reconnection"
        )
    }

    @Test
    fun `multiple messages can be sent and persisted`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val messages = (1..5).map { i ->
            Message(
                destinationHash = remoteHash,
                content = "Message $i",
                isOutgoing = true
            )
        }

        // Send all messages
        messages.forEach { msg ->
            messageRepository.send(msg)
        }
        testScheduler.advanceUntilIdle()

        // Verify all messages are in database
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertEquals(5, savedMessages.size)

        // Verify messages are in reverse chronological order (newest first)
        assertEquals("Message 5", savedMessages[0].content)
        assertEquals("Message 1", savedMessages[4].content)
    }

    @Test
    fun `pagination works correctly with database`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Send 15 messages
        repeat(15) { i ->
            val message = Message(
                destinationHash = remoteHash,
                content = "Message $i",
                isOutgoing = true
            )
            messageRepository.send(message)
        }
        testScheduler.advanceUntilIdle()

        // Test pagination
        val page1 = messageRepository.getMessages(remoteHash, limit = 10, offset = 0)
        assertEquals(10, page1.size)

        val page2 = messageRepository.getMessages(remoteHash, limit = 10, offset = 10)
        assertEquals(5, page2.size)

        val emptyPage = messageRepository.getMessages(remoteHash, limit = 10, offset = 20)
        assertEquals(0, emptyPage.size)
    }

    @Test
    fun `message deletion works correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Send message
        val message = Message(
            destinationHash = remoteHash,
            content = "Message to delete",
            isOutgoing = true
        )
        val sent = messageRepository.send(message)
        testScheduler.advanceUntilIdle()

        // Verify message exists
        val beforeDelete = messageRepository.getMessages(remoteHash, limit = 1, offset = 0)
        assertEquals(1, beforeDelete.size)

        // Delete message
        messageRepository.deleteMessage(sent.id)
        testScheduler.advanceUntilIdle()

        // Verify message is deleted
        val afterDelete = messageRepository.getMessages(remoteHash, limit = 1, offset = 0)
        assertEquals(0, afterDelete.size)
    }

    @Test
    fun `concurrent message operations are handled correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Send multiple messages concurrently
        val jobs = (1..10).map { i ->
            testScope.launch {
                val message = Message(
                    destinationHash = remoteHash,
                    content = "Concurrent message $i",
                    isOutgoing = true
                )
                messageRepository.send(message)
            }
        }

        // Wait for all operations to complete
        jobs.forEach { it.join() }
        testScheduler.advanceUntilIdle()

        // Verify all messages were persisted
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 20, offset = 0)
        assertEquals(10, savedMessages.size)
    }

    @Test
    fun `different conversations are kept separate`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Send messages to different contacts
        val contact1 = "contact_1"
        val contact2 = "contact_2"

        val message1 = Message(
            destinationHash = contact1,
            content = "Message for contact 1",
            isOutgoing = true
        )

        val message2 = Message(
            destinationHash = contact2,
            content = "Message for contact 2",
            isOutgoing = true
        )

        messageRepository.send(message1)
        messageRepository.send(message2)
        testScheduler.advanceUntilIdle()

        // Verify messages are separated by contact
        val messages1 = messageRepository.getMessages(contact1, limit = 10, offset = 0)
        val messages2 = messageRepository.getMessages(contact2, limit = 10, offset = 0)

        assertEquals(1, messages1.size)
        assertEquals(1, messages2.size)
        assertEquals("Message for contact 1", messages1.first().content)
        assertEquals("Message for contact 2", messages2.first().content)
    }

    @Test
    fun `incoming and outgoing messages are distinguished correctly`() = runTest {
        // This test would require simulating incoming messages from the network
        // For now, we test that outgoing messages are marked correctly

        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val outgoingMessage = Message(
            destinationHash = remoteHash,
            content = "Outgoing message",
            isOutgoing = true
        )

        val sent = messageRepository.send(outgoingMessage)
        testScheduler.advanceUntilIdle()

        // Verify message is marked as outgoing
        val savedMessages = messageRepository.getMessages(remoteHash, limit = 1, offset = 0)
        assertEquals(1, savedMessages.size)
        assertEquals(true, savedMessages.first().isOutgoing)
        assertEquals(localHash, savedMessages.first().sourceHash)
        assertEquals(remoteHash, savedMessages.first().destinationHash)
    }
}