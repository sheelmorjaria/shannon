package com.shannon.domain.model

/**
 * Voice call state machine:
 *   IDLE -> RINGING (incoming) -> CONNECTED -> IDLE (hangup)
 *                              \-> IDLE (reject)
 *   IDLE -> OUTGOING -> CONNECTED -> IDLE (hangup)
 *                     \-> IDLE (remote reject / busy)
 */
enum class CallState {
    IDLE,
    RINGING,
    OUTGOING,
    CONNECTED
}

/**
 * Represents an active or pending voice call (LXST).
 * Manages call state transitions.
 */
class Call {
    var state: CallState = CallState.IDLE
        private set

    var peerHash: String? = null
        private set

    /** Incoming call arrived - transition IDLE -> RINGING */
    fun incomingRing(fromHash: String) {
        check(state == CallState.IDLE) { "Cannot ring when in state $state" }
        state = CallState.RINGING
        peerHash = fromHash
    }

    /** Outgoing call initiated - transition IDLE -> OUTGOING */
    fun initiate(toHash: String) {
        check(state == CallState.IDLE) { "Cannot initiate call when in state $state" }
        state = CallState.OUTGOING
        peerHash = toHash
    }

    /** Accept incoming call - transition RINGING -> CONNECTED */
    fun accept() {
        check(state == CallState.RINGING) { "Cannot accept when in state $state" }
        state = CallState.CONNECTED
    }

    /** Reject incoming call - transition RINGING -> IDLE */
    fun reject() {
        check(state == CallState.RINGING) { "Cannot reject when in state $state" }
        state = CallState.IDLE
    }

    /** Remote party accepted outgoing call - transition OUTGOING -> CONNECTED */
    fun remoteAccept() {
        check(state == CallState.OUTGOING) { "Cannot remote accept when in state $state" }
        state = CallState.CONNECTED
    }

    /** Hang up active call - transition CONNECTED -> IDLE */
    fun hangup() {
        check(state == CallState.CONNECTED) { "Cannot hangup when in state $state" }
        state = CallState.IDLE
    }
}
