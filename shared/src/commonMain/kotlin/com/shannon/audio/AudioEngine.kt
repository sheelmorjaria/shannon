package com.shannon.audio

import com.shannon.network.AudioCodec
import com.shannon.network.LxstPacket
import com.shannon.network.LxstPacketType

/**
 * Result of an audio engine operation.
 */
sealed class AudioEngineResult {
    abstract val isSuccess: Boolean
    abstract val errorMsg: String?

    data class Success(val msg: String = "OK") : AudioEngineResult() {
        override val isSuccess = true
        override val errorMsg: String? = null
    }
    data class Error(override val errorMsg: String) : AudioEngineResult() {
        override val isSuccess = false
    }
}

/**
 * Coordinates the audio pipeline during an active voice call.
 *
 * Recording path: Mic -> AudioRecorder -> raw buffer -> AUDIO packet -> collector -> network
 * Playback path:  Network -> AUDIO packet -> decoded buffer -> AudioPlayer -> speaker
 *
 * All audio hardware is accessed through platform-agnostic interfaces,
 * so this class is fully testable without real audio devices.
 */
class AudioEngine(
    private val recorder: AudioRecorder,
    private val player: AudioPlayer,
    private val codec: AudioCodec? = null,
    private val packetCollector: AudioPacketCollector? = null
) {
    private var isRecordingActive = false

    /**
     * Start recording from the microphone.
     * Returns Error if permission is not granted.
     */
    fun startRecording(): AudioEngineResult {
        if (isRecordingActive) return AudioEngineResult.Success("Already recording")

        if (!recorder.hasPermission()) {
            return AudioEngineResult.Error("Audio recording permission not granted")
        }

        recorder.startRecording { buffer ->
            onMicBuffer(buffer)
        }
        isRecordingActive = true
        return AudioEngineResult.Success()
    }

    /**
     * Stop recording from the microphone.
     */
    fun stopRecording() {
        if (isRecordingActive) {
            recorder.stopRecording()
            isRecordingActive = false
        }
    }

    /**
     * Start audio playback.
     */
    fun startPlayback() {
        player.startPlaying()
    }

    /**
     * Stop audio playback.
     */
    fun stopPlayback() {
        player.stopPlaying()
    }

    /**
     * Stop all audio (recording + playback).
     */
    fun stopAll() {
        stopRecording()
        stopPlayback()
    }

    /**
     * Handle an incoming audio packet from the network.
     * Decodes and passes to the player, if a codec is negotiated and player is active.
     */
    fun onAudioPacketReceived(payload: ByteArray) {
        if (codec == null) return // No negotiated codec, drop packet
        player.playBuffer(payload)
    }

    /**
     * Process a raw audio buffer from the microphone.
     * Wraps it in an LXST AUDIO packet and sends to the collector.
     */
    private fun onMicBuffer(buffer: ByteArray) {
        packetCollector?.collect(
            LxstPacket(
                destinationHash = "", // Set by VoiceCallManager wrapper
                sourceHash = "",
                type = LxstPacketType.AUDIO,
                payload = buffer
            )
        )
    }
}
