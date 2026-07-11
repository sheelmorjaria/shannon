package com.shannon.bridge

import com.shannon.caption.Caption
import com.shannon.domain.model.CallState
import com.shannon.domain.model.Contact
import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import com.shannon.network.ConnectionStatus
import kotlinx.serialization.Serializable

@Serializable enum class MessageStateDto { DRAFT, QUEUED, SENDING, SENT, FAILED }
@Serializable enum class ConnectionStatusDto { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }
@Serializable enum class CallStateDto { IDLE, RINGING, OUTGOING, CONNECTED }

@Serializable
data class MessageDto(
    val id: String,
    val destinationHash: String,
    val content: String,
    val timestamp: Long,
    val state: MessageStateDto,
    val isOutgoing: Boolean,
)

@Serializable
data class ContactDto(
    val destinationHash: String,
    val displayName: String,
)

@Serializable
data class CaptionDto(
    val text: String,
    val lang: String,
    val translated: String? = null,
    val speakerId: String? = null,
    val isFinal: Boolean,
    val seq: Long,
    val sourceHash: String,
)

@Serializable
data class CallStateSnapshotDto(
    val state: CallStateDto,
    val peerHash: String? = null,
)

// --- domain → DTO mappers ---
fun Message.toDto(): MessageDto =
    MessageDto(id, destinationHash, content, timestamp, state.toDto(), isOutgoing)

fun Contact.toDto(): ContactDto = ContactDto(destinationHash, effectiveDisplayName)

fun Caption.toDto(): CaptionDto =
    CaptionDto(text, lang, translated, speakerId, isFinal, seq, sourceHash)

fun ConnectionStatus.toDto(): ConnectionStatusDto = when (this) {
    ConnectionStatus.DISCONNECTED -> ConnectionStatusDto.DISCONNECTED
    ConnectionStatus.CONNECTING -> ConnectionStatusDto.CONNECTING
    ConnectionStatus.CONNECTED -> ConnectionStatusDto.CONNECTED
    ConnectionStatus.RECONNECTING -> ConnectionStatusDto.RECONNECTING
}

fun MessageState.toDto(): MessageStateDto = when (this) {
    MessageState.DRAFT -> MessageStateDto.DRAFT
    MessageState.QUEUED -> MessageStateDto.QUEUED
    MessageState.SENDING -> MessageStateDto.SENDING
    MessageState.SENT -> MessageStateDto.SENT
    MessageState.FAILED -> MessageStateDto.FAILED
}

fun CallState.toDto(): CallStateDto = when (this) {
    CallState.IDLE -> CallStateDto.IDLE
    CallState.RINGING -> CallStateDto.RINGING
    CallState.OUTGOING -> CallStateDto.OUTGOING
    CallState.CONNECTED -> CallStateDto.CONNECTED
}
