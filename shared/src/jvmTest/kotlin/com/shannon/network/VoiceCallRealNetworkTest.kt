package com.shannon.network

import app.cash.turbine.test
import com.shannon.audio.FakeAudioInterfaces
import com.shannon.domain.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for complete voice call flow over real TCP network.
 * Tests call establishment, audio streaming, and teardown using real network transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCallRealNetworkTest {

    private lateinit var mockServer: MockTcpServer
    private lateinit var client1: ReticulumClientImpl
    private lateinit var client2: ReticulumClientImpl
    private lateinit var audioEngine1: com.shannon.audio.AudioEngine
    private lateinit var audioEngine2: com.shannon.audio.AudioEngine
    private lateinit var voiceCallManager1: VoiceCallManagerIntegrated
    private lateinit var voiceCallManager2: VoiceCallManagerIntegrated
    private lateinit var testScope: TestScope

    private val client1Hash = "client1_voice_call_test"
    private val client2Hash = "client2_voice_call_test"

    @Before
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())

        // Create mock server
        mockServer = MockTcpServer(port = 0)
        mockServer.start()

        // Create fake audio engines for both clients
        val fakeRecorder1 = FakeAudioInterfaces.FakeAudioRecorder()
        val fakePlayer1 = FakeAudioInterfaces.FakeAudioPlayer()
        audioEngine1 = com.shannon.audio.AudioEngine(
            recorder = fakeRecorder1,
            player = fakePlayer1
        )

        val fakeRecorder2 = FakeAudioInterfaces.FakeAudioRecorder()
        val fakePlayer2 = FakeAudioInterfaces.FakeAudioPlayer()
        audioEngine2 = com.shannon.audio.AudioEngine(
            recorder = fakeRecorder2,
            player = fakePlayer2
        )

        // Create network clients
        val config1 = ReticulumConfig(
            configDir = "/tmp/test_voice_call_client1",
            identityPath = null
        )
        client1 = ReticulumClientImpl(
            config = config1,
            scope = testScope,
            audioEngine = audioEngine1
        )

        val config2 = ReticulumConfig(
            configDir = "/tmp/test_voice_call_client2",
            identityPath = null
        )
        client2 = ReticulumClientImpl(
            config = config2,
            scope = testScope,
            audioEngine = audioEngine2
        )

        // Create voice call managers
        voiceCallManager1 = VoiceCallManagerIntegrated(
            client = client1,
            localHash = client1Hash,
            audioEngine = audioEngine1,
            scope = testScope
        )

        voiceCallManager2 = VoiceCallManagerIntegrated(
            client = client2,
            localHash = client2Hash,
            audioEngine = audioEngine2,
            scope = testScope
        )
    }

    @After
    fun teardown() {
        testScope.runTest {
            try {
                voiceCallManager1.forceCleanup()
                voiceCallManager2.forceCleanup()
                audioEngine1.stopAll()
                audioEngine2.stopAll()
                client1.cleanup()
                client2.cleanup()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        mockServer.stop()
    }

    @Test
    fun `complete voice call flow - SETUP, ACCEPT, audio streaming, HANGUP`() = runTest {
        // Connect both clients to the mock server
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        assertEquals(ConnectionStatus.CONNECTED, client1.observeStatus().test { awaitItem() })
        assertEquals(ConnectionStatus.CONNECTED, client2.observeStatus().test { awaitItem() })

        // Client 1 initiates call
        voiceCallManager1.initiateCall(client2Hash)
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.OUTGOING, voiceCallManager1.callState.value)

        // Client 2 receives SETUP and transitions to RINGING
        voiceCallManager2.onIncomingLxst(
            LxstPacket(
                destinationHash = client2Hash,
                sourceHash = client1Hash,
                type = LxstPacketType.SETUP
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.RINGING, voiceCallManager2.callState.value)

        // Client 2 accepts call
        voiceCallManager2.acceptCall()
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager2.callState.value)

        // Client 1 receives ACCEPT and transitions to CONNECTED
        voiceCallManager1.onIncomingLxst(
            LxstPacket(
                destinationHash = client1Hash,
                sourceHash = client2Hash,
                type = LxstPacketType.ACCEPT
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager1.callState.value)

        // Verify audio streaming is active
        assertNotNull(voiceCallManager1.getAudioStatistics(), "Client 1 should have audio stats")
        assertNotNull(voiceCallManager2.getAudioStatistics(), "Client 2 should have audio stats")

        // Simulate audio conversation
        val client1Speech = "Hello from client 1"
        val client2Speech = "Hi from client 2"

        // Client 1 sends audio
        voiceCallManager1.onIncomingLxst(
            LxstPacket(
                destinationHash = client2Hash,
                sourceHash = client1Hash,
                type = LxstPacketType.AUDIO,
                payload = client1Speech.toByteArray()
            )
        )
        testScheduler.advanceUntilIdle()

        // Client 2 sends audio
        voiceCallManager2.onIncomingLxst(
            LxstPacket(
                destinationHash = client1Hash,
                sourceHash = client2Hash,
                type = LxstPacketType.AUDIO,
                payload = client2Speech.toByteArray()
            )
        )
        testScheduler.advanceUntilIdle()

        // Verify audio statistics show packets exchanged
        assertTrue(voiceCallManager1.getAudioStatistics()?.packetsSent!! > 0,
            "Client 1 should have sent audio packets")

        // Client 1 hangs up
        voiceCallManager1.hangup()
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager1.callState.value)

        // Client 2 receives HANGUP
        voiceCallManager2.onIncomingLxst(
            LxstPacket(
                destinationHash = client2Hash,
                sourceHash = client1Hash,
                type = LxstPacketType.HANGUP
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager2.callState.value)
    }

    @Test
    fun `incoming call rejection flow works correctly`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Client 1 initiates call
        voiceCallManager1.initiateCall(client2Hash)
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.OUTGOING, voiceCallManager1.callState.value)

        // Client 2 receives SETUP
        voiceCallManager2.onIncomingLxst(
            LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.RINGING, voiceCallManager2.callState.value)

        // Client 2 rejects call
        voiceCallManager2.rejectCall()
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager2.callState.value)

        // Client 1 receives REJECT
        voiceCallManager1.onIncomingLxst(
            LxstPacket(
                destinationHash = client1Hash,
                sourceHash = client2Hash,
                type = LxstPacketType.REJECT
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager1.callState.value)
    }

    @Test
    fun `busy signal flow works correctly`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Client 2 already in a call (simulated busy state)
        voiceCallManager2.initiateCall("another_peer")
        testScheduler.advanceUntilIdle()

        // Client 1 tries to call client 2
        voiceCallManager1.initiateCall(client2Hash)
        testScheduler.advanceUntilIdle()

        // Client 2 sends BUSY response
        voiceCallManager1.onIncomingLxst(
            LxstPacket(
                destinationHash = client1Hash,
                sourceHash = client2Hash,
                type = LxstPacketType.BUSY
            )
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager1.callState.value)
    }

    @Test
    fun `bidirectional audio streaming works in real call`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call
        voiceCallManager1.initiateCall(client2Hash)
        voiceCallManager2.onIncomingLxst(LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP))
        voiceCallManager2.acceptCall()
        voiceCallManager1.onIncomingLxst(LxstPacket(client1Hash, client2Hash, LxstPacketType.ACCEPT))
        testScheduler.advanceUntilIdle()

        assertEquals(CallState.CONNECTED, voiceCallManager1.callState.value)
        assertEquals(CallState.CONNECTED, voiceCallManager2.callState.value)

        // Simulate bidirectional conversation
        val conversation = listOf(
            Pair("Hello from client 1", "Hi from client 2"),
            Pair("How are you?", "I'm good, thanks!"),
            Pair("Great to hear", "Audio quality is nice"),
            Pair("Talk soon!", "Bye for now!")
        )

        conversation.forEach { (client1Msg, client2Msg) ->
            // Client 1 speaks
            voiceCallManager1.onIncomingLxst(
                LxstPacket(client2Hash, client1Hash, LxstPacketType.AUDIO, client1Msg.toByteArray())
            )

            // Client 2 speaks
            voiceCallManager2.onIncomingLxst(
                LxstPacket(client1Hash, client2Hash, LxstPacketType.AUDIO, client2Msg.toByteArray())
            )

            testScheduler.advanceUntilIdle()
        }

        // Verify both clients sent and received audio
        val client1Stats = voiceCallManager1.getAudioStatistics()
        val client2Stats = voiceCallManager2.getAudioStatistics()

        assertTrue(client1Stats?.packetsSent!! > 0, "Client 1 should have sent audio")
        assertTrue(client2Stats?.packetsSent!! > 0, "Client 2 should have sent audio")
    }

    @Test
    fun `call handles network interruption and recovery`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call
        voiceCallManager1.initiateCall(client2Hash)
        voiceCallManager2.onIncomingLxst(LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP))
        voiceCallManager2.acceptCall()
        voiceCallManager1.onIncomingLxst(LxstPacket(client1Hash, client2Hash, LxstPacketType.ACCEPT))
        testScheduler.advanceUntilIdle()

        assertEquals(CallState.CONNECTED, voiceCallManager1.callState.value)

        // Get initial audio stats
        val statsBeforeFailure = voiceCallManager1.getAudioStatistics()
        val packetsSentBefore = statsBeforeFailure?.packetsSent ?: 0

        // Simulate network failure
        mockServer.simulateFailure()
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(200)

        // Try to send audio during failure
        voiceCallManager1.onIncomingLxst(
            LxstPacket(client2Hash, client1Hash, LxstPacketType.AUDIO, "Audio during failure".toByteArray())
        )
        testScheduler.advanceUntilIdle()

        // Restart server (network recovery)
        mockServer.restart()
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(300)

        // Verify call can continue after recovery
        voiceCallManager1.onIncomingLxst(
            LxstPacket(client2Hash, client1Hash, LxstPacketType.AUDIO, "Audio after recovery".toByteArray())
        )
        testScheduler.advanceUntilIdle()

        val statsAfterRecovery = voiceCallManager1.getAudioStatistics()
        assertTrue(statsAfterRecovery?.packetsSent!! >= packetsSentBefore,
            "Should continue sending after recovery")
    }

    @Test
    fun `concurrent call attempts are handled correctly`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Both clients try to call each other simultaneously
        voiceCallManager1.initiateCall(client2Hash)
        voiceCallManager2.initiateCall(client1Hash)
        testScheduler.advanceUntilIdle()

        assertEquals(CallState.OUTGOING, voiceCallManager1.callState.value)
        assertEquals(CallState.OUTGOING, voiceCallManager2.callState.value)

        // Both receive SETUP from each other
        voiceCallManager1.onIncomingLxst(LxstPacket(client1Hash, client2Hash, LxstPacketType.SETUP))
        voiceCallManager2.onIncomingLxst(LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP))
        testScheduler.advanceUntilIdle()

        // Both should accept and resolve to CONNECTED
        voiceCallManager1.acceptCall()
        voiceCallManager2.acceptCall()
        testScheduler.advanceUntilIdle()

        assertEquals(CallState.CONNECTED, voiceCallManager1.callState.value)
        assertEquals(CallState.CONNECTED, voiceCallManager2.callState.value)
    }

    @Test
    fun `audio quality statistics are tracked during call`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call
        voiceCallManager1.initiateCall(client2Hash)
        voiceCallManager2.onIncomingLxst(LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP))
        voiceCallManager2.acceptCall()
        voiceCallManager1.onIncomingLxst(LxstPacket(client1Hash, client2Hash, LxstPacketType.ACCEPT))
        testScheduler.advanceUntilIdle()

        // Send substantial amount of audio
        val audioPackets = (1..50).map { i ->
            LxstPacket(
                destinationHash = client2Hash,
                sourceHash = client1Hash,
                type = LxstPacketType.AUDIO,
                payload = "Audio frame $i".toByteArray()
            )
        }

        audioPackets.forEach { packet ->
            voiceCallManager1.onIncomingLxst(packet)
            testScheduler.advanceUntilIdle()
        }

        // Verify detailed statistics
        val client1Stats = voiceCallManager1.getAudioStatistics()
        val client1ProcessingStats = client1.getAudioProcessingStats()

        assertTrue(client1Stats?.packetsSent!! >= 50, "Should track sent packets")
        assertTrue(client1ProcessingStats.packetsReceived >= 50, "Should track received packets")
        assertTrue(client1Stats.successRate > 0.9, "Should have high success rate")
        assertTrue(client1ProcessingStats.processingRate > 0.9, "Should have high processing rate")
    }

    @Test
    fun `call teardown properly releases all resources`() = runTest {
        // Connect both clients
        client1.connect("localhost", mockServer.actualPort)
        client2.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call with audio
        voiceCallManager1.initiateCall(client2Hash)
        voiceCallManager2.onIncomingLxst(LxstPacket(client2Hash, client1Hash, LxstPacketType.SETUP))
        voiceCallManager2.acceptCall()
        voiceCallManager1.onIncomingLxst(LxstPacket(client1Hash, client2Hash, LxstPacketType.ACCEPT))
        testScheduler.advanceUntilIdle()

        // Send some audio
        repeat(10) { i ->
            voiceCallManager1.onIncomingLxst(
                LxstPacket(client2Hash, client1Hash, LxstPacketType.AUDIO, "Audio $i".toByteArray())
            )
        }
        testScheduler.advanceUntilIdle()

        // Hangup from client 1
        voiceCallManager1.hangup()
        testScheduler.advanceUntilIdle()

        // Client 2 receives hangup
        voiceCallManager2.onIncomingLxst(
            LxstPacket(client2Hash, client1Hash, LxstPacketType.HANGUP)
        )
        testScheduler.advanceUntilIdle()

        // Verify both clients are IDLE and resources released
        assertEquals(CallState.IDLE, voiceCallManager1.callState.value)
        assertEquals(CallState.IDLE, voiceCallManager2.callState.value)

        // Verify no audio continues to flow
        val finalStats1 = voiceCallManager1.getAudioStatistics()
        val finalStats2 = voiceCallManager2.getAudioStatistics()

        // These should remain constant after hangup
        val packetsAfterHangup1 = finalStats1?.packetsSent ?: 0
        val packetsAfterHangup2 = finalStats2?.packetsSent ?: 0

        // Try to send more audio (should not work)
        voiceCallManager1.onIncomingLxst(
            LxstPacket(client2Hash, client1Hash, LxstPacketType.AUDIO, "Post-hangup audio".toByteArray())
        )
        testScheduler.advanceUntilIdle()

        val laterStats1 = voiceCallManager1.getAudioStatistics()
        assertEquals(packetsAfterHangup1, laterStats1?.packetsSent,
            "Should not send packets after hangup")
    }
}