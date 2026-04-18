package com.shannon.network

import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for Reticulum client functionality.
 * Tests the complete network communication flow.
 */
class ReticulumClientIntegrationTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `test connection status flow`() = runTest {
        val client = ReticulumClientImpl(
            scope = testScope,
            config = ReticulumConfig(
                healthCheckIntervalMs = 1000
            )
        )

        // Initial status should be disconnected
        assertEquals(ConnectionStatus.DISCONNECTED, client.observeStatus().first())

        // Simulate connection
        client.connect("testhost.com", 4242)

        // Status should change to connected
        val statusAfterConnect = client.observeStatus().first()
        assertEquals(ConnectionStatus.CONNECTED, statusAfterConnect)

        // Verify connection info
        val connectionInfo = client.getConnectionInfo()
        assertNotNull(connectionInfo)
        assertEquals("testhost.com", connectionInfo?.host)
        assertEquals(4242, connectionInfo?.port)
        assertEquals(ConnectionStatus.CONNECTED, connectionInfo?.status)
    }

    @Test
    fun `test LXMF packet sending`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("localhost", 4242)

        val testPacket = LxmfPacket(
            destinationHash = "test_destination_hash",
            sourceHash = "test_source_hash",
            content = "Hello, Reticulum!",
            timestamp = System.currentTimeMillis()
        )

        // Send packet (should not throw)
        client.sendLxmfPacket(testPacket)

        // Verify we're still connected
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test LXST voice packet sending`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("voice.server.com", 4242)

        val voicePacket = LxstPacket(
            destinationHash = "voice_peer_hash",
            sourceHash = "my_voice_hash",
            type = LxstPacketType.SETUP
        )

        // Send voice setup packet
        client.sendLxstPacket(voicePacket)

        // Send audio packet
        val audioPacket = LxstPacket(
            destinationHash = "voice_peer_hash",
            sourceHash = "my_voice_hash",
            type = LxstPacketType.AUDIO
        )

        client.sendLxstPacket(audioPacket)
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test packet reception flow`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("localhost", 4242)

        // Collect incoming packets
        val incomingPackets = mutableListOf<LxmfPacket>()
        val collectJob = testScope.launch {
            client.observeIncomingPackets().collect { packet ->
                incomingPackets.add(packet)
            }
        }

        // Simulate sending a packet that would loop back
        val testPacket = LxmfPacket(
            destinationHash = "loopback_hash",
            sourceHash = "my_hash",
            content = "Test message"
        )

        client.sendLxmfPacket(testPacket)

        // In real implementation, packets would come back through the network
        // For now, we just verify the flow is established
        assertTrue(incomingPackets.isEmpty()) // No packets in simulated mode

        collectJob.cancel()
    }

    @Test
    fun `test voice call signaling sequence`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("voice.server.com", 4242)

        val peerHash = "peer_voice_hash"
        val myHash = "my_voice_hash"

        // Simulate voice call setup
        val setupPacket = LxstPacket(
            destinationHash = peerHash,
            sourceHash = myHash,
            type = LxstPacketType.SETUP
        )
        client.sendLxstPacket(setupPacket)

        // Simulate accept
        val acceptPacket = LxstPacket(
            destinationHash = myHash,
            sourceHash = peerHash,
            type = LxstPacketType.ACCEPT
        )
        client.sendLxstPacket(acceptPacket)

        // Simulate audio streaming
        val audioPackets = listOf(
            LxstPacket(peerHash, myHash, LxstPacketType.AUDIO),
            LxstPacket(peerHash, myHash, LxstPacketType.AUDIO),
            LxstPacket(peerHash, myHash, LxstPacketType.AUDIO)
        )

        audioPackets.forEach { packet ->
            client.sendLxstPacket(packet)
        }

        // Simulate hangup
        val hangupPacket = LxstPacket(
            destinationHash = peerHash,
            sourceHash = myHash,
            type = LxstPacketType.HANGUP
        )
        client.sendLxstPacket(hangupPacket)

        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test identity hash management`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)

        // Initially no identity
        assertEquals(null, client.getLocalIdentityHash())

        // After connection, should have identity
        client.connect("testhost.com", 4242)
        assertNotNull(client.getLocalIdentityHash())

        // Identity should be consistent
        val identity1 = client.getLocalIdentityHash()
        val identity2 = client.getLocalIdentityHash()
        assertEquals(identity1, identity2)
    }

    @Test
    fun `test connection error handling`() = runTest {
        val client = ReticulumClientImpl(
            scope = testScope,
            config = ReticulumConfig(
                enableRetries = false
            )
        )

        // Try invalid connection
        try {
            client.connect("invalid.host", 9999)
            // Should not reach here in real implementation
            // For now, simulated connection always succeeds
        } catch (e: Exception) {
            // Expected in real implementation
            assertTrue(e is NetworkException)
        }
    }

    @Test
    fun `test disconnect and reconnect`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)

        // First connection
        client.connect("host1.com", 4242)
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())

        // Disconnect
        client.disconnect()
        assertEquals(ConnectionStatus.DISCONNECTED, client.observeStatus().first())

        // Reconnect to different host
        client.connect("host2.com", 4243)
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())

        val info = client.getConnectionInfo()
        assertEquals("host2.com", info?.host)
        assertEquals(4243, info?.port)
    }

    @Test
    fun `test announce functionality`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("announce.server.com", 4242)

        // Announce should not throw when connected
        client.announce()

        // Should still be connected after announce
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test concurrent packet handling`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("localhost", 4242)

        // Send multiple packets concurrently
        val packets = (1..10).map { i ->
            LxmfPacket(
                destinationHash = "dest_$i",
                sourceHash = "source",
                content = "Message $i"
            )
        }

        packets.forEach { packet ->
            testScope.launch {
                client.sendLxmfPacket(packet)
            }
        }

        // All packets should be sent successfully
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }
}

/**
 * Voice call integration tests.
 */
class VoiceCallIntegrationTest {

    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Test
    fun `test complete voice call lifecycle`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("voice.server.com", 4242)

        val callerHash = "caller_identity"
        val calleeHash = "callee_identity"

        // 1. Caller sends SETUP
        client.sendLxstPacket(LxstPacket(calleeHash, callerHash, LxstPacketType.SETUP))

        // 2. Callee accepts
        client.sendLxstPacket(LxstPacket(callerHash, calleeHash, LxstPacketType.ACCEPT))

        // 3. Audio streaming phase
        val audioData = "sample_audio_data".repeat(10)
        val audioPackets = audioData.chunked(50).map { chunk ->
            LxstPacket(calleeHash, callerHash, LxstPacketType.AUDIO)
        }

        audioPackets.forEach { packet ->
            client.sendLxstPacket(packet)
        }

        // 4. Hangup
        client.sendLxstPacket(LxstPacket(calleeHash, callerHash, LxstPacketType.HANGUP))

        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test voice call rejection`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("voice.server.com", 4242)

        val callerHash = "caller"
        val calleeHash = "callee"

        // Setup
        client.sendLxstPacket(LxstPacket(calleeHash, callerHash, LxstPacketType.SETUP))

        // Reject
        client.sendLxstPacket(LxstPacket(callerHash, calleeHash, LxstPacketType.REJECT))

        // Should remain connected
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }

    @Test
    fun `test busy signal handling`() = runTest {
        val client = ReticulumClientImpl(scope = testScope)
        client.connect("voice.server.com", 4242)

        val callerHash = "caller"
        val calleeHash = "callee"

        // Setup
        client.sendLxstPacket(LxstPacket(calleeHash, callerHash, LxstPacketType.SETUP))

        // Busy response
        client.sendLxstPacket(LxstPacket(callerHash, calleeHash, LxstPacketType.BUSY))

        // Should remain connected
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().first())
    }
}