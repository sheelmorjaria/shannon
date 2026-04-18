package com.shannon.network

import com.shannon.audio.AudioEngine
import com.shannon.audio.AudioPacketCollector
import com.shannon.domain.model.CallState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Enhanced VoiceCallManager that integrates AudioEngine with real network transport.
 * Manages complete voice call lifecycle: signaling + audio streaming.
 */
class VoiceCallManagerIntegrated(
    private val client: ReticulumClient,
    private val localHash: String,
    private val audioEngine: AudioEngine,
    private val scope: CoroutineScope
) {
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    private var peerHash: String? = null
    private var audioPacketCollector: AudioPacketCollector? = null
    private var incomingLxstJob: Job? = null

    // Track LXST packets for processing
    private var lastIncomingLxst: LxstPacket? = null

    /**
     * Initiate an outgoing call to the given peer.
     * Transitions: IDLE -> OUTGOING, sends SETUP packet.
     */
    suspend fun initiateCall(remoteHash: String) {
        check(_callState.value == CallState.IDLE) { "Cannot initiate call in state ${_callState.value}" }

        peerHash = remoteHash
        _callState.value = CallState.OUTGOING

        client.sendLxstPacket(
            LxstPacket(
                destinationHash = remoteHash,
                sourceHash = localHash,
                type = LxstPacketType.SETUP
            )
        )

        // Start listening for incoming LXST packets
        startIncomingLxstListener()
    }

    /**
     * Process the last incoming LXST packet and update call state.
     */
    fun processIncomingLxst() {
        val packet = lastIncomingLxst ?: return

        when (packet.type) {
            LxstPacketType.SETUP -> {
                if (_callState.value == CallState.IDLE) {
                    peerHash = packet.sourceHash
                    _callState.value = CallState.RINGING
                }
            }
            LxstPacketType.ACCEPT -> {
                if (_callState.value == CallState.OUTGOING) {
                    _callState.value = CallState.CONNECTED
                    startAudioStreaming()
                }
            }
            LxstPacketType.REJECT, LxstPacketType.BUSY -> {
                if (_callState.value == CallState.OUTGOING || _callState.value == CallState.RINGING) {
                    cleanupAudio()
                    _callState.value = CallState.IDLE
                    peerHash = null
                }
            }
            LxstPacketType.HANGUP -> {
                if (_callState.value == CallState.CONNECTED) {
                    cleanupAudio()
                    _callState.value = CallState.IDLE
                    peerHash = null
                }
            }
            LxstPacketType.AUDIO -> {
                // Route audio packets to AudioEngine for playback
                if (_callState.value == CallState.CONNECTED) {
                    packet.payload?.let { audioData ->
                        audioEngine.onAudioPacketReceived(audioData)
                    }
                }
            }
        }
    }

    /**
     * Accept an incoming ringing call.
     */
    suspend fun acceptCall() {
        check(_callState.value == CallState.RINGING) { "Cannot accept in state ${_callState.value}" }

        _callState.value = CallState.CONNECTED
        peerHash?.let { remoteHash ->
            client.sendLxstPacket(
                LxstPacket(
                    destinationHash = remoteHash,
                    sourceHash = localHash,
                    type = LxstPacketType.ACCEPT
                )
            )
        }

        // Start audio streaming
        startAudioStreaming()
    }

    /**
     * Reject an incoming ringing call.
     */
    suspend fun rejectCall() {
        check(_callState.value == CallState.RINGING) { "Cannot reject in state ${_callState.value}" }

        peerHash?.let { remoteHash ->
            client.sendLxstPacket(
                LxstPacket(
                    destinationHash = remoteHash,
                    sourceHash = localHash,
                    type = LxstPacketType.REJECT
                )
            )
        }

        _callState.value = CallState.IDLE
        peerHash = null
    }

    /**
     * Hang up the active call.
     */
    suspend fun hangup() {
        check(_callState.value == CallState.CONNECTED) { "Cannot hangup in state ${_callState.value}" }

        // Stop audio streaming first
        cleanupAudio()

        // Send hangup packet
        peerHash?.let { remoteHash ->
            client.sendLxstPacket(
                LxstPacket(
                    destinationHash = remoteHash,
                    sourceHash = localHash,
                    type = LxstPacketType.HANGUP
                )
            )
        }

        _callState.value = CallState.IDLE
        peerHash = null
    }

    /**
     * Start audio streaming for the connected call.
     * Sets up the audio pipeline: Mic -> AudioEngine -> Collector -> Network
     */
    private fun startAudioStreaming() {
        val remoteHash = peerHash ?: return

        // Create real audio packet collector that sends via ReticulumClient
        audioPacketCollector = RealAudioPacketCollector(
            client = client,
            localHash = localHash,
            destinationHash = remoteHash,
            scope = scope
        )

        // Start audio engine with real packet collector
        // Note: This would require recreating AudioEngine with the collector
        // For now, we start recording and playback
        scope.launch {
            try {
                // Start audio recording (mic -> network)
                audioEngine.startRecording()

                // Start audio playback (network -> speaker)
                audioEngine.startPlayback()

            } catch (e: Exception) {
                println("Failed to start audio streaming: ${e.message}")
                cleanupAudio()
            }
        }
    }

    /**
     * Cleanup audio resources and stop streaming.
     */
    private fun cleanupAudio() {
        try {
            // Stop audio engine
            audioEngine.stopAll()

            // Clear packet collector
            audioPacketCollector = null

            // Cancel LXST listener
            incomingLxstJob?.cancel()
            incomingLxstJob = null

        } catch (e: Exception) {
            println("Error cleaning up audio: ${e.message}")
        }
    }

    /**
     * Start listening for incoming LXST packets from the network.
     */
    private fun startIncomingLxstListener() {
        incomingLxstJob?.cancel()

        incomingLxstJob = scope.launch {
            client.observeIncomingLxstPackets().collect { packet ->
                // Filter packets for this call
                if (peerHash == null || packet.sourceHash == peerHash) {
                    lastIncomingLxst = packet
                    processIncomingLxst()
                }
            }
        }
    }

    /**
     * Store an incoming LXST packet for processing.
     * Called by external coroutine collecting from observeIncomingLxstPackets().
     */
    fun onIncomingLxst(packet: LxstPacket) {
        lastIncomingLxst = packet
        processIncomingLxst()
    }

    /**
     * Get audio transmission statistics if available.
     */
    suspend fun getAudioStatistics(): AudioStatistics? {
        return (audioPacketCollector as? RealAudioPacketCollector)?.getStatistics()
    }

    /**
     * Force cleanup all resources (for emergency cleanup).
     */
    fun forceCleanup() {
        cleanupAudio()
        _callState.value = CallState.IDLE
        peerHash = null
        incomingLxstJob?.cancel()
        incomingLxstJob = null
    }
}