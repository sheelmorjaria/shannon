package com.shannon.domain.model

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * Message state machine:
 *   DRAFT -> QUEUED -> SENDING -> SENT
 *                      \-> FAILED -> QUEUED (retry)
 */
enum class MessageState {
    DRAFT,
    QUEUED,
    SENDING,
    SENT,
    FAILED
}

/** Valid state transitions for the message state machine. */
private val VALID_TRANSITIONS = mapOf(
    MessageState.DRAFT to setOf(MessageState.QUEUED),
    MessageState.QUEUED to setOf(MessageState.SENDING),
    MessageState.SENDING to setOf(MessageState.SENT, MessageState.FAILED),
    MessageState.SENT to emptySet(),
    MessageState.FAILED to setOf(MessageState.QUEUED)
)

/**
 * An LXMF message entity with state tracking.
 */
@OptIn(ExperimentalUuidApi::class)
data class Message(
    val id: String = Uuid.random().toString(),
    val destinationHash: String,
    val content: String,
    val timestamp: Long,
    val state: MessageState = MessageState.DRAFT,
    val isOutgoing: Boolean = true
) {
    /**
     * Transition to a new message state.
     * @throws IllegalStateException if the transition is not valid from the current state.
     */
    fun transitionTo(newState: MessageState): Message {
        val allowed = VALID_TRANSITIONS[state]
            ?: error("No transitions defined for state $state")
        check(newState in allowed) {
            "Cannot transition from $state to $newState. Allowed: $allowed"
        }
        return copy(state = newState)
    }
}
