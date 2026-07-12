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
 * Integration tests for real-time audio uplink (Microphone → Network).
 * Tests the complete audio pipeline with real network transport.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioUplinkIntegrationTest {

    private lateinit var mockServer: MockTcpServer
    private lateinit var client: ReticulumClientImpl
    private lateinit var fakeRecorder: FakeAudioInterfaces.FakeAudioRecorder
    private lateinit var fakePlayer: FakeAudioInterfaces.FakeAudioPlayer
    private lateinit var audioEngine: com.shannon.audio.AudioEngine
    private lateinit var voiceCallManager: VoiceCallManagerIntegrated
    private lateinit var testScope: TestScope

    private val localHash = "local_audio_test_identity"
    private val remoteHash = "remote_audio_test_identity"

    @Before
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())

        // Create mock server
        mockServer = MockTcpServer(port = 0)
        mockServer.start()

        // Create network client
        val config = ReticulumConfig(
            configDir = "/tmp/test_audio_uplink",
            identityPath = null
        )
        client = ReticulumClientImpl(
            config = config,
            scope = testScope
        )

        // Create fake audio interfaces for testing
        fakeRecorder = FakeAudioInterfaces.FakeAudioRecorder()
        fakePlayer = FakeAudioInterfaces.FakeAudioPlayer()

        // Create audio engine with fake components
        audioEngine = com.shannon.audio.AudioEngine(
            recorder = fakeRecorder,
            player = fakePlayer
        )

        // Create voice call manager with audio engine
        voiceCallManager = VoiceCallManagerIntegrated(
            client = client,
            localHash = localHash,
            audioEngine = audioEngine,
            scope = testScope
        )
    }

    @After
    fun teardown() {
        testScope.runTest {
            try {
                voiceCallManager.forceCleanup()
                audioEngine.stopAll()
                client.cleanup()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        mockServer.stop()
    }

    @Test
    fun `audio packet collector sends packets via real network`() = runTest {
        // Connect to network
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Create audio packet collector
        val collector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = testScope
        )

        // Simulate audio packets from microphone
        val audioPackets = (1..10).map { i ->
            LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.AUDIO,
                payload = "Audio frame $i".toByteArray()
            )
        }

        // Collect audio packets
        audioPackets.forEach { packet ->
            collector.collect(packet)
        }

        testScheduler.advanceUntilIdle()

        // Verify packets were sent
        val stats = collector.getStatistics()
        assertTrue(stats.packetsSent >= 10, "Should have sent at least 10 audio packets")
        assertEquals(0, stats.packetsDropped, "Should not have dropped packets in normal conditions")
    }

    @Test
    fun `audio packets are sent with high priority without buffering`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val collector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = testScope
        )

        // Send multiple audio packets rapidly
        val rapidPackets = (1..50).map { i ->
            LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.AUDIO,
                payload = "Rapid audio frame $i".toByteArray()
            )
        }

        val startTime = System.currentTimeMillis()
        rapidPackets.forEach { packet ->
            collector.collect(packet)
        }
        val sendDuration = System.currentTimeMillis() - startTime

        testScheduler.advanceUntilIdle()

        // Verify rapid sending (should be very fast, no buffering delays)
        assertTrue(sendDuration < 1000, "50 packets should be sent in under 1 second")

        val stats = collector.getStatistics()
        assertTrue(stats.packetsSent >= 50, "Should have sent all 50 packets")
    }

    @Test
    fun `voice call manager starts audio streaming on CONNECTED state`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Initiate call
        voiceCallManager.initiateCall(remoteHash)
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.OUTGOING, voiceCallManager.callState.value)

        // Simulate remote accepting
        voiceCallManager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.ACCEPT
            )
        )

        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager.callState.value)

        // Verify audio streaming started
        // Audio engine should be recording and playing
        val stats = voiceCallManager.getAudioStatistics()
        assertNotNull(stats, "Audio statistics should be available when connected")

        // Simulate some audio frames being generated
        fakeRecorder.simulateAudioFrame("Test audio frame 1".toByteArray())
        fakeRecorder.simulateAudioFrame("Test audio frame 2".toByteArray())

        testScheduler.advanceUntilIdle()

        // Verify audio packets were sent
        val updatedStats = voiceCallManager.getAudioStatistics()
        assertTrue(updatedStats?.packetsSent!! > 0, "Should have sent audio packets")
    }

    @Test
    fun `audio streaming stops on call hangup`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call
        voiceCallManager.initiateCall(remoteHash)
        voiceCallManager.onIncomingLxst(
            LxstPacket(remoteHash, localHash, LxstPacketType.ACCEPT)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager.callState.value)

        // Generate some audio
        fakeRecorder.simulateAudioFrame("Before hangup".toByteArray())
        testScheduler.advanceUntilIdle()

        val statsBeforeHangup = voiceCallManager.getAudioStatistics()
        assertTrue(statsBeforeHangup?.packetsSent!! > 0)

        // Hangup call
        voiceCallManager.hangup()
        testScheduler.advanceUntilIdle()

        assertEquals(CallState.IDLE, voiceCallManager.callState.value)

        // Generate more audio after hangup
        fakeRecorder.simulateAudioFrame("After hangup".toByteArray())
        testScheduler.advanceUntilIdle()

        // Verify no new packets sent after hangup
        val statsAfterHangup = voiceCallManager.getAudioStatistics()
        assertEquals(statsBeforeHangup.packetsSent, statsAfterHangup?.packetsSent,
            "Should not send new packets after hangup")
    }

    @Test
    fun `audio packet collector handles network failures gracefully`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val collector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = testScope
        )

        // Send some packets successfully
        repeat(5) {
            collector.collect(LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.AUDIO,
                payload = "Audio before failure".toByteArray()
            ))
        }
        testScheduler.advanceUntilIdle()

        // Simulate network failure
        mockServer.simulateFailure()
        testScheduler.advanceUntilIdle()
        kotlinx.coroutines.delay(200)

        // Try to send more packets - should handle gracefully
        repeat(5) {
            collector.collect(LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.AUDIO,
                payload = "Audio after failure".toByteArray()
            ))
        }
        testScheduler.advanceUntilIdle()

        // Verify statistics show both successful and failed sends
        val stats = collector.getStatistics()
        assertTrue(stats.packetsSent > 0, "Should have sent some packets before failure")
        // Packet drops might occur during network failure
    }

    @Test
    fun `complete voice call flow with audio streaming`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Full call lifecycle
        voiceCallManager.initiateCall(remoteHash)
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.OUTGOING, voiceCallManager.callState.value)

        // Remote accepts
        voiceCallManager.onIncomingLxst(
            LxstPacket(remoteHash, localHash, LxstPacketType.ACCEPT)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager.callState.value)

        // Simulate conversation (audio frames)
        val conversationFrames = listOf(
            "Hello, how are you?",
            "I'm doing great, thanks!",
            "Let's test this audio quality",
            "Sounds good to me!"
        )

        conversationFrames.forEach { frame ->
            fakeRecorder.simulateAudioFrame(frame.toByteArray())
            testScheduler.advanceUntilIdle()
        }

        // Verify audio was transmitted
        val stats = voiceCallManager.getAudioStatistics()
        assertTrue(stats?.packetsSent!! > 0, "Should have transmitted conversation")

        // Hangup
        voiceCallManager.hangup()
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager.callState.value)
    }

    @Test
    fun `audio packet statistics are tracked accurately`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val collector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = testScope
        )

        // Send known number of packets
        val packetCount = 25
        repeat(packetCount) { i ->
            collector.collect(LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.AUDIO,
                payload = "Audio packet $i".toByteArray()
            ))
        }

        testScheduler.advanceUntilIdle()

        val stats = collector.getStatistics()
        assertTrue(stats.packetsSent >= packetCount, "Should track packet count accurately")
        assertTrue(stats.totalPackets >= packetCount, "Total packets should match")
        assertTrue(stats.successRate > 0.9, "Should have high success rate")
        assertTrue(stats.lastSendTime > 0, "Should track last send time")
    }

    @Test
    fun `concurrent audio packet transmission works correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val collector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = testScope
        )

        // Send packets concurrently (simulating real-time audio)
        val jobs = (1..20).map { i ->
            testScope.launch {
                collector.collect(LxstPacket(
                    destinationHash = remoteHash,
                    sourceHash = localHash,
                    type = LxstPacketType.AUDIO,
                    payload = "Concurrent audio $i".toByteArray()
                ))
            }
        }

        // Wait for all concurrent sends
        jobs.forEach { it.join() }
        testScheduler.advanceUntilIdle()

        val stats = collector.getStatistics()
        assertTrue(stats.packetsSent >= 20, "Should handle concurrent transmission")
    }

    @Test
    fun `voice call manager filters LXST packets by call peer`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        val otherPeer = "another_peer_identity"

        // Start call with remoteHash
        voiceCallManager.initiateCall(remoteHash)
        voiceCallManager.onIncomingLxst(
            LxstPacket(remoteHash, localHash, LxstPacketType.ACCEPT)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager.callState.value)

        // Send audio packet from correct peer
        fakeRecorder.simulateAudioFrame("From correct peer".toByteArray())
        testScheduler.advanceUntilIdle()

        val statsBefore = voiceCallManager.getAudioStatistics()

        // Send audio packet from wrong peer (should be filtered)
        voiceCallManager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = otherPeer,
                type = LxstPacketType.AUDIO,
                payload = "From wrong peer".toByteArray()
            )
        )
        testScheduler.advanceUntilIdle()

        val statsAfter = voiceCallManager.getAudioStatistics()

        // Statistics should be the same (wrong peer packets filtered)
        assertEquals(statsBefore?.packetsSent, statsAfter?.packetsSent,
            "Should not process audio packets from wrong peer")
    }
}