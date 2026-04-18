package com.shannon.integration

import app.cash.turbine.test
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.network.ConnectionStatus
import com.shannon.network.MessageRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TwoClientIntegrationTest {

    private val hashA = "11111111111111111111111111111111"
    private val hashB = "22222222222222222222222222222222"

    @Test
    fun `client A sends message to client B`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        val clientB = network.clientB(hashB)
        network.connect()

        val repoA = MessageRepositoryImpl(clientA, hashA, this)
        val repoB = MessageRepositoryImpl(clientB, hashB, this)
        repoB.startListening()

        // Client A sends "Hello B"
        repoA.send(Message(
            destinationHash = hashB,
            content = "Hello B",
            timestamp = System.currentTimeMillis()
        ))

        testScheduler.advanceUntilIdle()

        // Client B should receive the message
        val bMessages = repoB.getMessages(hashA)
        assertEquals(1, bMessages.size)
        assertEquals("Hello B", bMessages[0].content)
        assertTrue(!bMessages[0].isOutgoing)

        repoB.stopListening()
    }

    @Test
    fun `bidirectional message exchange`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        val clientB = network.clientB(hashB)
        network.connect()

        val repoA = MessageRepositoryImpl(clientA, hashA, this)
        val repoB = MessageRepositoryImpl(clientB, hashB, this)
        repoA.startListening()
        repoB.startListening()

        // A -> B: "Hello B"
        repoA.send(Message(
            destinationHash = hashB,
            content = "Hello B",
            timestamp = 1000L
        ))
        testScheduler.advanceUntilIdle()

        // Verify B received A's message (B stores incoming under source hash = hashA)
        val bIncoming = repoB.getMessages(hashA).filter { !it.isOutgoing }
        assertEquals(1, bIncoming.size)
        assertEquals("Hello B", bIncoming[0].content)

        // B -> A: "Received, thanks!"
        repoB.send(Message(
            destinationHash = hashA,
            content = "Received, thanks!",
            timestamp = 2000L
        ))
        testScheduler.advanceUntilIdle()

        // Verify A received B's reply (A stores incoming under source hash = hashB)
        val aIncoming = repoA.getMessages(hashB).filter { !it.isOutgoing }
        assertEquals(1, aIncoming.size)
        assertEquals("Received, thanks!", aIncoming[0].content)
        assertTrue(!aIncoming[0].isOutgoing)

        repoA.stopListening()
        repoB.stopListening()
    }

    @Test
    fun `connection drop updates status to reconnecting`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        network.connect()

        // Collect status changes
        clientA.observeStatus().test {
            // Should start CONNECTED
            val first = awaitItem()
            assertEquals(ConnectionStatus.CONNECTED, first)

            // Drop the link
            network.disconnect()

            val second = awaitItem()
            assertEquals(ConnectionStatus.RECONNECTING, second)
        }
    }

    @Test
    fun `reconnection restores connected status`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        network.connect()

        clientA.observeStatus().test {
            assertEquals(ConnectionStatus.CONNECTED, awaitItem())

            network.disconnect()
            assertEquals(ConnectionStatus.RECONNECTING, awaitItem())

            network.reconnect()
            assertEquals(ConnectionStatus.CONNECTED, awaitItem())
        }
    }

    @Test
    fun `pending messages are flushed after reconnection`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        val clientB = network.clientB(hashB)
        network.connect()

        val repoB = MessageRepositoryImpl(clientB, hashB, this)
        repoB.startListening()

        // Drop the link
        network.disconnect()

        // A sends a packet while disconnected - gets queued
        clientA.sendLxmfPacket(com.shannon.network.LxmfPacket(
            destinationHash = hashB,
            sourceHash = hashA,
            content = "Queued message",
            timestamp = 1000L
        ))

        // Verify packet is pending
        assertEquals(1, network.pendingPacketsA.size)
        testScheduler.advanceUntilIdle()
        assertEquals(0, repoB.getMessages(hashA).size, "B should not receive during disconnect")

        // Reconnect and flush pending
        network.reconnect()
        network.flushPendingA()
        testScheduler.advanceUntilIdle()

        // Now B should receive the queued message
        val bMessages = repoB.getMessages(hashA)
        assertEquals(1, bMessages.size)
        assertEquals("Queued message", bMessages[0].content)

        repoB.stopListening()
    }

    @Test
    fun `send state transitions are correct end to end`() = runTest {
        val network = InMemoryNetwork()
        val clientA = network.clientA(hashA)
        val clientB = network.clientB(hashB)
        network.connect()

        val repoA = MessageRepositoryImpl(clientA, hashA, this)

        val sent = repoA.send(Message(
            destinationHash = hashB,
            content = "State test",
            timestamp = System.currentTimeMillis()
        ))

        assertEquals(MessageState.SENT, sent.state)
    }
}
