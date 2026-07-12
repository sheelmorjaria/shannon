package com.shannon.audio

/**
 * Platform-independent audio recorder interface.
 * Implemented per-platform (Android: AudioRecord / Oboe, Desktop: PortAudio / Java Sound).
 */
interface AudioRecorder {
    /** Check if the app has permission to record audio. */
    fun hasPermission(): Boolean

    /** Start recording audio. Buffers are delivered via the callback. */
    fun startRecording(onBuffer: (ByteArray) -> Unit)

    /** Stop recording audio. */
    fun stopRecording()
}

/**
 * Platform-independent audio player interface.
 * Implemented per-platform (Android: AudioTrack / Oboe, Desktop: PortAudio / Java Sound).
 */
interface AudioPlayer {
    /** Start the audio player (open device). */
    fun startPlaying()

    /** Stop the audio player (release device). */
    fun stopPlaying()

    /** Play a decoded audio buffer to the speaker. */
    fun playBuffer(data: ByteArray)
}

/**
 * Collects outgoing audio packets produced by the engine.
 * In production, this sends via ReticulumClient.
 */
interface AudioPacketCollector {
    fun collect(packet: com.shannon.network.LxstPacket)
}
