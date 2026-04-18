package com.shannon.network

import com.shannon.domain.model.CallState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages voice call signaling via LXST packets.
 * Coordinates call state transitions and communicates through the ReticulumClient.
 */
class VoiceCallManager(
    private val client: ReticulumClient,
    private val localHash: String
) {
    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState = _callState.asStateFlow()

    var peerHash: String? = null
        private set

    /** The last received LXST packet (for signaling test access). */
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
                }
            }
            LxstPacketType.REJECT, LxstPacketType.BUSY -> {
                if (_callState.value == CallState.OUTGOING || _callState.value == CallState.RINGING) {
                    _callState.value = CallState.IDLE
                }
            }
            LxstPacketType.HANGUP -> {
                if (_callState.value == CallState.CONNECTED) {
                    _callState.value = CallState.IDLE
                }
            }
            LxstPacketType.AUDIO -> { /* handled by audio pipeline */ }
        }
    }

    /**
     * Accept an incoming ringing call.
     */
    suspend fun acceptCall() {
        check(_callState.value == CallState.RINGING) { "Cannot accept in state ${_callState.value}" }
        _callState.value = CallState.CONNECTED
        client.sendLxstPacket(
            LxstPacket(
                destinationHash = peerHash!!,
                sourceHash = localHash,
                type = LxstPacketType.ACCEPT
            )
        )
    }

    /**
     * Reject an incoming ringing call.
     */
    suspend fun rejectCall() {
        check(_callState.value == CallState.RINGING) { "Cannot reject in state ${_callState.value}" }
        client.sendLxstPacket(
            LxstPacket(
                destinationHash = peerHash!!,
                sourceHash = localHash,
                type = LxstPacketType.REJECT
            )
        )
        _callState.value = CallState.IDLE
        peerHash = null
    }

    /**
     * Hang up the active call.
     */
    suspend fun hangup() {
        check(_callState.value == CallState.CONNECTED) { "Cannot hangup in state ${_callState.value}" }
        client.sendLxstPacket(
            LxstPacket(
                destinationHash = peerHash!!,
                sourceHash = localHash,
                type = LxstPacketType.HANGUP
            )
        )
        _callState.value = CallState.IDLE
        peerHash = null
    }

    /**
     * Store an incoming LXST packet for processing.
     * Called by the coroutine collecting from observeIncomingLxstPackets().
     */
    fun onIncomingLxst(packet: LxstPacket) {
        lastIncomingLxst = packet
    }
}
