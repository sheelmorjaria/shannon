package com.shannon.audio

import com.shannon.network.LxstPacket

/**
 * Fake AudioRecorder for testing.
 */
class FakeAudioRecorder(
    private val hasPermission: Boolean = true
) : AudioRecorder {

    var isRecording = false
        private set

    var startCount = 0
        private set

    private val _buffers = mutableListOf<ByteArray>()
    private var onBufferCallback: ((ByteArray) -> Unit)? = null

    override fun hasPermission(): Boolean = hasPermission

    override fun startRecording(onBuffer: (ByteArray) -> Unit) {
        startCount++
        isRecording = true
        onBufferCallback = onBuffer
    }

    override fun stopRecording() {
        isRecording = false
        onBufferCallback = null
    }

    /** Simulate the microphone producing an audio buffer. */
    fun simulateAudioBuffer(data: ByteArray) {
        onBufferCallback?.invoke(data)
    }
}

/**
 * Fake AudioPlayer for testing.
 */
class FakeAudioPlayer : AudioPlayer {

    var isPlaying = false
        private set

    val playedBuffers = mutableListOf<ByteArray>()

    override fun startPlaying() {
        isPlaying = true
    }

    override fun stopPlaying() {
        isPlaying = false
        playedBuffers.clear()
    }

    override fun playBuffer(data: ByteArray) {
        if (isPlaying) {
            playedBuffers.add(data)
        }
    }
}

/**
 * Collects LXST packets produced by the audio engine.
 */
class FakeLxstPacketCollector : AudioPacketCollector {
    val packets = mutableListOf<LxstPacket>()

    override fun collect(packet: LxstPacket) {
        packets.add(packet)
    }
}
