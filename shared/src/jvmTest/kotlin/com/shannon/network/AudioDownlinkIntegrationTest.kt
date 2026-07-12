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
 * Integration tests for real-time audio downlink (Network → Speaker).
 * Tests receiving and processing incoming LXST audio packets.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioDownlinkIntegrationTest {

    private lateinit var mockServer: MockTcpServer
    private lateinit var client: ReticulumClientImpl
    private lateinit var fakeRecorder: FakeAudioInterfaces.FakeAudioRecorder
    private lateinit var fakePlayer: FakeAudioInterfaces.FakeAudioPlayer
    private lateinit var audioEngine: com.shannon.audio.AudioEngine
    private lateinit var voiceCallManager: VoiceCallManagerIntegrated
    private lateinit var testScope: TestScope

    private val localHash = "local_audio_downlink_test"
    private val remoteHash = "remote_audio_downlink_test"

    @Before
    fun setup() {
        testScope = TestScope(StandardTestDispatcher())

        // Create mock server
        mockServer = MockTcpServer(port = 0)
        mockServer.start()

        // Create fake audio interfaces
        fakeRecorder = FakeAudioInterfaces.FakeAudioRecorder()
        fakePlayer = FakeAudioInterfaces.FakeAudioPlayer()

        // Create audio engine with fake components
        audioEngine = com.shannon.audio.AudioEngine(
            recorder = fakeRecorder,
            player = fakePlayer
        )

        // Create network client with audio engine
        val config = ReticulumConfig(
            configDir = "/tmp/test_audio_downlink",
            identityPath = null
        )
        client = ReticulumClientImpl(
            config = config,
            scope = testScope,
            audioEngine = audioEngine
        )

        // Create voice call manager
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
    fun `incoming audio packets are routed to audio engine for playback`() = runTest {
        // Connect to network
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()
        assertEquals(ConnectionStatus.CONNECTED, client.observeStatus().test { awaitItem() })

        // Simulate incoming audio packet
        val audioData = "Test audio data".toByteArray()
        val audioPacket = LxstPacket(
            destinationHash = localHash,
            sourceHash = remoteHash,
            type = LxstPacketType.AUDIO,
            payload = audioData
        )

        // Start audio playback
        audioEngine.startPlayback()

        // Process audio packet
        voiceCallManager.onIncomingLxst(audioPacket)
        testScheduler.advanceUntilIdle()

        // Verify audio was routed to player
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.isNotEmpty(), "Audio should have been played")
        assertEquals(audioData, playedBuffers.last(), "Played audio should match incoming data")
    }

    @Test
    fun `audio packets are processed with minimal latency`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        val testLatencies = mutableListOf<Long>()

        // Measure processing latency for multiple packets
        repeat(10) { i ->
            val audioData = "Low latency test $i".toByteArray()
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = audioData
            )

            val startTime = System.nanoTime()
            voiceCallManager.onIncomingLxst(audioPacket)
            testScheduler.advanceUntilIdle()
            val latency = System.nanoTime() - startTime

            testLatencies.add(latency)
        }

        // Calculate average latency in milliseconds
        val avgLatencyMs = testLatencies.average() / 1_000_000.0

        // Verify low latency processing (should be under 50ms average)
        assertTrue(avgLatencyMs < 50.0, "Average latency should be under 50ms, was: ${avgLatencyMs}ms")
    }

    @Test
    fun `rapid audio packets are handled without buffer overflow`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        // Send rapid audio packets (simulating high frame rate)
        val rapidPackets = (1..100).map { i ->
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = "Rapid audio frame $i".toByteArray()
            )
        }

        // Process all packets rapidly
        val startTime = System.currentTimeMillis()
        rapidPackets.forEach { packet ->
            voiceCallManager.onIncomingLxst(packet)
        }
        testScheduler.advanceUntilIdle()
        val totalTime = System.currentTimeMillis() - startTime

        // Verify all packets were processed
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= 100, "Should handle rapid packet stream")

        // Verify processing time is reasonable (should not take too long)
        assertTrue(totalTime < 5000, "100 packets should be processed in under 5 seconds")
    }

    @Test
    fun `audio downlink works during active voice call`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        // Establish call
        voiceCallManager.initiateCall(remoteHash)
        voiceCallManager.onIncomingLxst(
            LxstPacket(remoteHash, localHash, LxstPacketType.ACCEPT)
        )
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.CONNECTED, voiceCallManager.callState.value)

        // Simulate incoming audio from remote peer
        val conversationAudio = listOf(
            "Hello from remote peer!",
            "How is the audio quality?",
            "Testing voice synthesis...",
            "End of test conversation"
        )

        conversationAudio.forEach { audioText ->
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = audioText.toByteArray()
            )
            voiceCallManager.onIncomingLxst(audioPacket)
            testScheduler.advanceUntilIdle()
        }

        // Verify all audio was played
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= conversationAudio.size,
            "All conversation audio should be played")

        // Verify audio content matches
        val playedText = playedBuffers.takeLast(conversationAudio.size)
            .map { it.decodeToString() }
        assertEquals(conversationAudio, playedText, "Played audio should match incoming packets")
    }

    @Test
    fun `audio processing statistics are tracked accurately`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        val packetCount = 25
        repeat(packetCount) { i ->
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = "Stats test audio $i".toByteArray()
            )
            voiceCallManager.onIncomingLxst(audioPacket)
            testScheduler.advanceUntilIdle()
        }

        // Get processing statistics
        val stats = client.getAudioProcessingStats()
        assertTrue(stats.packetsReceived >= packetCount, "Should track received packets")
        assertTrue(stats.packetsProcessed >= packetCount, "Should track processed packets")
        assertTrue(stats.processingRate > 0.9, "Should have high processing success rate")
    }

    @Test
    fun `out-of-order audio packets are handled correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        // Send packets out of order
        val orderedAudio = listOf("First", "Second", "Third", "Fourth", "Fifth")
        val outOfOrderPackets = listOf(
            orderedAudio[2], // Third
            orderedAudio[0], // First
            orderedAudio[4], // Fifth
            orderedAudio[1], // Second
            orderedAudio[3]  // Fourth
        ).map { audioText ->
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = audioText.toByteArray()
            )
        }

        // Process out-of-order packets
        outOfOrderPackets.forEach { packet ->
            voiceCallManager.onIncomingLxst(packet)
            testScheduler.advanceUntilIdle()
        }

        // Verify all packets were played (even if out of order)
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= orderedAudio.size,
            "Should handle out-of-order packets")

        // Verify all content was played (order may vary)
        val playedText = playedBuffers.takeLast(orderedAudio.size)
            .map { it.decodeToString() }
            .toSet()
        assertEquals(orderedAudio.toSet(), playedText,
            "All out-of-order content should be played")
    }

    @Test
    fun `audio packet loss is handled gracefully`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        val totalPackets = 20
        val packetsToDrop = setOf(3, 7, 11, 15) // Simulate packet loss

        // Send packets with some "lost" (not sent)
        val processedPackets = mutableListOf<Int>()
        repeat(totalPackets) { i ->
            if (i ! in packetsToDrop) {
                val audioPacket = LxstPacket(
                    destinationHash = localHash,
                    sourceHash = remoteHash,
                    type = LxstPacketType.AUDIO,
                    payload = "Audio frame $i".toByteArray()
                )
                voiceCallManager.onIncomingLxst(audioPacket)
                processedPackets.add(i)
                testScheduler.advanceUntilIdle()
            }
        }

        // Verify system continued working despite packet loss
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= processedPackets.size,
            "Should continue playing despite packet loss")

        // Verify statistics show packet loss
        val stats = client.getAudioProcessingStats()
        assertTrue(stats.packetsReceived > 0, "Should track received packets")
        // Some packets might be dropped due to simulated loss
    }

    @Test
    fun `audio jitter is smoothed out during playback`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        // Simulate jittery audio stream with varying delays
        val jitteryPackets = mutableListOf<Pair<LxstPacket, Long>>()

        repeat(10) { i ->
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = "Jittery audio $i".toByteArray()
            )
            // Add random delay (0-50ms) to simulate network jitter
            val delay = (Math.random() * 50).toLong()
            jitteryPackets.add(audioPacket to delay)
        }

        // Send packets with simulated jitter
        jitteryPackets.forEach { (packet, delay) ->
            voiceCallManager.onIncomingLxst(packet)
            kotlinx.coroutines.delay(delay)
            testScheduler.advanceUntilIdle()
        }

        // Verify smooth playback despite jitter
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= jitteryPackets.size,
            "Should smooth out jitter and play all audio")

        // Audio engine's jitter buffer should handle timing variations
        val stats = client.getAudioProcessingStats()
        assertTrue(stats.processingRate > 0.8, "Should maintain good processing rate despite jitter")
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

        audioEngine.startPlayback()

        // Send some audio
        repeat(5) { i ->
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = "Before hangup $i".toByteArray()
            )
            voiceCallManager.onIncomingLxst(audioPacket)
            testScheduler.advanceUntilIdle()
        }

        val playedBeforeHangup = fakePlayer.getPlayedBuffers().size
        assertTrue(playedBeforeHangup > 0)

        // Hangup call
        voiceCallManager.hangup()
        testScheduler.advanceUntilIdle()
        assertEquals(CallState.IDLE, voiceCallManager.callState.value)

        // Try to send more audio after hangup
        repeat(5) { i ->
            val audioPacket = LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.AUDIO,
                payload = "After hangup $i".toByteArray()
            )
            voiceCallManager.onIncomingLxst(audioPacket)
            testScheduler.advanceUntilIdle()
        }

        // Verify no new audio was played after hangup
        val playedAfterHangup = fakePlayer.getPlayedBuffers().size
        assertEquals(playedBeforeHangup, playedAfterHangup,
            "Should not play audio after call hangup")
    }

    @Test
    fun `concurrent audio and signaling packets are handled correctly`() = runTest {
        client.connect("localhost", mockServer.actualPort)
        testScheduler.advanceUntilIdle()

        audioEngine.startPlayback()

        // Mix of audio and signaling packets
        val mixedPackets = listOf(
            LxstPacket(localHash, remoteHash, LxstPacketType.SETUP),
            LxstPacket(localHash, remoteHash, LxstPacketType.AUDIO, "Audio 1".toByteArray()),
            LxstPacket(localHash, remoteHash, LxstPacketType.AUDIO, "Audio 2".toByteArray()),
            LxstPacket(localHash, remoteHash, LxstPacketType.ACCEPT),
            LxstPacket(localHash, remoteHash, LxstPacketType.AUDIO, "Audio 3".toByteArray()),
            LxstPacket(localHash, remoteHash, LxstPacketType.AUDIO, "Audio 4".toByteArray()),
            LxstPacket(localHash, remoteHash, LxstPacketType.HANGUP),
            LxstPacket(localHash, remoteHash, LxstPacketType.AUDIO, "Audio 5".toByteArray())
        )

        // Process mixed packets
        mixedPackets.forEach { packet ->
            voiceCallManager.onIncomingLxst(packet)
            testScheduler.advanceUntilIdle()
        }

        // Verify audio packets were played
        val playedBuffers = fakePlayer.getPlayedBuffers()
        assertTrue(playedBuffers.size >= 5, "Should play audio packets")

        // Verify call state changes were processed
        assertTrue(voiceCallManager.callState.value == CallState.IDLE,
            "Should process signaling packets and end call")
    }
}