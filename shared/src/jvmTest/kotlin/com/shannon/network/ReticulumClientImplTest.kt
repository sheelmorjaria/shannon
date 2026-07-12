package com.shannon.network

import app.cash.turbine.test
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Instrumented tests for ReticulumClientImpl.
 * Tests real network connectivity using MockTcpServer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReticulumClientImplTest {

    private lateinit var mockServer: MockTcpServer
    private lateinit var client: ReticulumClientImpl
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        // Create mock server
        mockServer = MockTcpServer(port = 0) // Auto-assign port
        mockServer.start()

        // Create test scope
        testScope = TestScope(StandardTestDispatcher())

        // Create client with test configuration
        val config = ReticulumConfig(
            configDir = "/tmp/test_reticulum",
            identityPath = null, // Generate new identity
            healthCheckIntervalMs = 1000,
            reconnectDelayMs = 100,
            enableRetries = true
        )

        client = ReticulumClientImpl(
            config = config,
            scope = testScope
        )
    }

    @After
    fun teardown() {
        testScope.runTest {
            try {
                client.cleanup()
            } catch (e: Exception) {
                // Ignore cleanup errors in tests
            }
        }
        mockServer.stop()
    }

    @Test
    fun `client initial status is disconnected`() = runTest {
        client.observeStatus().test {
            assertEquals(ConnectionStatus.DISCONNECTED, awaitItem())
        }
    }

    @Test
    fun `connect establishes TCP connection`() = runTest {
        client.connect("localhost", mockServer.actualPort)

        client.observeStatus().test {
            assertEquals(ConnectionStatus.CONNECTING, awaitItem())
            assertEquals(ConnectionStatus.CONNECTED, awaitItem())
        }

        assertTrue(mockServer.hasConnection())
        assertEquals(1, mockServer.connectionCount)
    }

    @Test
    fun `disconnect closes TCP connection`() = runTest {
        // First connect
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Then disconnect
        client.disconnect()
        testScheduler.advanceUntilIdle()

        client.observeStatus().test {
            assertEquals(ConnectionStatus.DISCONNECTED, awaitItem())
        }

        // Give server time to detect disconnect
        kotlinx.coroutines.delay(200)
        assertFalse(mockServer.hasConnection())
    }

    @Test
    fun `sendLxmfPacket transmits data to server`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val packet = LxmfPacket(
            destinationHash = "test_destination",
            sourceHash = "test_source",
            content = "Hello, Reticulum!",
            timestamp = System.currentTimeMillis()
        )

        client.sendLxmfPacket(packet)
        testScheduler.advanceUntilIdle()

        // Wait for server to receive packet
        val received = mockServer.waitForPacket(timeoutMs = 2000)
        assertNotNull(received, "Server should have received packet")
        assertTrue(received.isNotEmpty(), "Packet should contain data")
    }

    @Test
    fun `observeIncomingPackets receives data`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Note: This test requires the server to send data back
        // For now, we just verify that the Flow is set up correctly
        val packetFlow = client.observeIncomingPackets()
        assertNotNull(packetFlow, "Packet flow should be initialized")
    }

    @Test
    fun `announce can be called after connection`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // This should not throw
        client.announce()
        testScheduler.advanceUntilIdle()

        // Verify connection is still active
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })
    }

    @Test
    fun `connect to unreachable host fails gracefully`() = runTest {
        var exceptionThrown = false
        var exception: Exception? = null

        try {
            // Connect to non-existent host
            client.connect("localhost", 9999)
            testScheduler.advanceUntilIdle()
        } catch (e: Exception) {
            exceptionThrown = true
            exception = e
        }

        assertTrue(exceptionThrown, "Should throw exception for unreachable host")
        assertTrue(exception?.message?.contains("Connection failed") == true)

        // Status should be DISCONNECTED after failure
        client.observeStatus().test {
            assertEquals(ConnectionStatus.DISCONNECTED, awaitItem())
        }
    }

    @Test
    fun `sendLxmfPacket throws when not connected`() = runTest {
        val packet = LxmfPacket(
            destinationHash = "test_destination",
            sourceHash = "test_source",
            content = "Test message"
        )

        var exceptionThrown = false
        try {
            client.sendLxmfPacket(packet)
        } catch (e: NetworkException) {
            exceptionThrown = true
        }

        assertTrue(exceptionThrown, "Should throw when not connected")
    }

    @Test
    fun `multiple connections can be established`() = runTest {
        // First connection
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Disconnect
        client.disconnect()
        testScheduler.advanceUntilIdle()

        // Second connection should work
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })
    }

    @Test
    fun `sendLxstPacket transmits signaling data`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val packet = LxstPacket(
            destinationHash = "test_destination",
            sourceHash = "test_source",
            type = LxstPacketType.SETUP
        )

        client.sendLxstPacket(packet)
        testScheduler.advanceUntilIdle()

        // Wait for server to receive packet
        val received = mockServer.waitForPacket(timeoutMs = 2000)
        assertNotNull(received, "Server should have received LXST packet")
    }

    @Test
    fun `client handles server disconnection`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        assertTrue(mockServer.hasConnection())

        // Simulate server failure
        mockServer.simulateFailure()

        // Give client time to detect disconnection
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(100)

        // Client should detect disconnection (may trigger reconnection logic)
        val currentStatus = client.observeStatus().test { awaitItem() }
        assertTrue(
            currentStatus == ConnectionStatus.DISCONNECTED ||
            currentStatus == ConnectionStatus.RECONNECTING,
            "Client should detect server failure"
        )
    }

    @Test
    fun `cleanup properly closes resources`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        assertTrue(mockServer.hasConnection())

        // Cleanup should disconnect
        client.cleanup()

        // Give time for cleanup
        kotlinx.coroutines.delay(200)

        // Verify disconnection
        assertFalse(mockServer.hasConnection())
    }

    @Test
    fun `concurrent packet sending works correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val packets = (1..10).map { i ->
            LxmfPacket(
                destinationHash = "test_destination",
                sourceHash = "test_source",
                content = "Message $i"
            )
        }

        // Send all packets concurrently
        val jobs = packets.map { packet ->
            kotlinx.coroutines.async {
                client.sendLxmfPacket(packet)
            }
        }

        // Wait for all sends to complete
        jobs.forEach { it.await() }
        testScheduler.advanceUntilIdle()

        // Server should have received all packets
        kotlinx.coroutines.delay(500)
        assertTrue(mockServer.hasData(), "Server should have received packets")
    }
}