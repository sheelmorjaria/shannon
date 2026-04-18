package com.shannon.network

import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MessageRepositoryImplTest {

    private val localHash = "1234567890abcdef1234567890abcdef"
    private val remoteHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    private fun createRepo(
        client: FakeReticulumClient = FakeReticulumClient(),
        testScope: kotlinx.coroutines.test.TestScope
    ): Pair<FakeReticulumClient, MessageRepositoryImpl> {
        return client to MessageRepositoryImpl(client, localHash, testScope)
    }

    @Test
    fun `send transitions message through states and calls network`() = runTest {
        val (client, repo) = createRepo(testScope = this)

        val msg = Message(
            destinationHash = remoteHash,
            content = "Hello B",
            timestamp = System.currentTimeMillis()
        )

        val sent = repo.send(msg)

        // Verify state transitions: DRAFT -> QUEUED -> SENDING -> SENT
        assertEquals(MessageState.SENT, sent.state)

        // Verify the network client was called with an LXMF packet
        assertEquals(1, client.sentLxmfPackets.size)
        val packet = client.sentLxmfPackets[0]
        assertEquals("Hello B", packet.content)
        assertEquals(remoteHash, packet.destinationHash)
        assertEquals(localHash, packet.sourceHash)
    }

    @Test
    fun `send catches network error and marks message as failed`() = runTest {
        val client = FakeReticulumClient()
        client.shouldFailSend = true
        val repo = MessageRepositoryImpl(client, localHash, this)

        val msg = Message(
            destinationHash = remoteHash,
            content = "Will fail",
            timestamp = System.currentTimeMillis()
        )

        val result = repo.send(msg)
        assertEquals(MessageState.FAILED, result.state)
    }

    @Test
    fun `receive incoming packet creates incoming message`() = runTest {
        val client = FakeReticulumClient()
        val repo = MessageRepositoryImpl(client, localHash, this)

        // Start listening and simulate incoming packet
        repo.startListening()

        client.simulateIncomingLxmf(
            LxmfPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                content = "Hello from remote"
            )
        )

        // Advance dispatcher to process the collected flow
        testScheduler.advanceUntilIdle()

        // Check via getMessages (stored under source hash = remoteHash)
        val messages = repo.getMessages(remoteHash)
        assertEquals(1, messages.size)
        assertEquals("Hello from remote", messages[0].content)
        // Incoming message destinationHash is the local hash (packet was sent to us)
        assertEquals(localHash, messages[0].destinationHash)
        assertTrue(!messages[0].isOutgoing)

        repo.stopListening()
    }
}
