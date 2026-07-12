package com.shannon.audio

import com.shannon.network.AudioCodec
import com.shannon.network.LxstPacket
import com.shannon.network.LxstPacketType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngineTest {

    @Test
    fun `starting audio without permission results in error state`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = false)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        val result = engine.startRecording()

        assertFalse(result.isSuccess)
        assertTrue(result.errorMsg!!.contains("permission", ignoreCase = true))
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `startRecording calls audioRecorder start`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        val result = engine.startRecording()

        assertTrue(result.isSuccess)
        assertTrue(recorder.isRecording)
    }

    @Test
    fun `stopRecording stops audioRecorder`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        engine.startRecording()
        assertTrue(recorder.isRecording)

        engine.stopRecording()
        assertFalse(recorder.isRecording)
    }

    @Test
    fun `audio buffer from mic is packetized`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val packetCollector = FakeLxstPacketCollector()
        val engine = AudioEngine(
            recorder = recorder,
            player = player,
            codec = AudioCodec.CODEC2_1200,
            packetCollector = packetCollector
        )

        engine.startRecording()

        // Simulate microphone producing an audio buffer
        val audioData = ByteArray(160) { it.toByte() } // 20ms of 8kHz 16-bit audio
        recorder.simulateAudioBuffer(audioData)

        // Verify the engine produced an AUDIO packet
        assertEquals(1, packetCollector.packets.size)
        assertEquals(LxstPacketType.AUDIO, packetCollector.packets[0].type)
        assertTrue(packetCollector.packets[0].payload != null)
    }

    @Test
    fun `received audio packet is passed to player`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player, codec = AudioCodec.OPUS)

        // Start playback before receiving audio
        engine.startPlayback()

        // Simulate receiving an audio packet from network
        val audioPayload = ByteArray(160) { (it * 2).toByte() }
        engine.onAudioPacketReceived(audioPayload)

        // Verify the player received the data
        assertEquals(1, player.playedBuffers.size)
        assertEquals(audioPayload.toList(), player.playedBuffers[0].toList())
    }

    @Test
    fun `received audio packet is dropped when no codec match`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player, codec = null)

        val audioPayload = ByteArray(160) { it.toByte() }
        engine.onAudioPacketReceived(audioPayload)

        // No codec negotiated, so audio should be dropped
        assertEquals(0, player.playedBuffers.size)
    }

    @Test
    fun `engine stops all audio on call end`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        engine.startRecording()
        player.startPlaying()
        assertTrue(recorder.isRecording)
        assertTrue(player.isPlaying)

        engine.stopAll()

        assertFalse(recorder.isRecording)
        assertFalse(player.isPlaying)
    }

    @Test
    fun `player start and stop lifecycle`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        engine.startPlayback()
        assertTrue(player.isPlaying)

        engine.stopPlayback()
        assertFalse(player.isPlaying)
    }

    @Test
    fun `multiple audio buffers produce multiple packets`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val packetCollector = FakeLxstPacketCollector()
        val engine = AudioEngine(
            recorder = recorder,
            player = player,
            codec = AudioCodec.CODEC2_700C,
            packetCollector = packetCollector
        )

        engine.startRecording()

        // Simulate 3 audio buffers
        recorder.simulateAudioBuffer(ByteArray(100) { 1.toByte() })
        recorder.simulateAudioBuffer(ByteArray(100) { 2.toByte() })
        recorder.simulateAudioBuffer(ByteArray(100) { 3.toByte() })

        assertEquals(3, packetCollector.packets.size)
    }

    @Test
    fun `recording is not started when already recording`() = runTest {
        val recorder = FakeAudioRecorder(hasPermission = true)
        val player = FakeAudioPlayer()
        val engine = AudioEngine(recorder, player)

        engine.startRecording()
        engine.startRecording() // second call should be no-op

        assertEquals(1, recorder.startCount, "startRecording should only be called once")
    }
}
