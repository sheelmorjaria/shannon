package com.shannon.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement

/** Subscription notifications the Kotlin core pushes to the React UI (server → client). */
sealed class BridgeEvent {
    @Serializable data class MessagesUpdated(val messages: List<MessageDto>) : BridgeEvent()
    @Serializable data class CaptionsUpdated(val captions: List<CaptionDto>) : BridgeEvent()
    @Serializable data class ConnectionStatusChanged(val status: ConnectionStatusDto) : BridgeEvent()
    @Serializable data class CallStateChanged(val snapshot: CallStateSnapshotDto) : BridgeEvent()
    @Serializable data class EngineAvailabilityChanged(val available: Boolean) : BridgeEvent()
}

/** Encode a [BridgeEvent] as a JSON-RPC notification. */
object BridgeEventCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun toNotification(event: BridgeEvent): RpcNotification {
        val (method, params) = when (event) {
            is BridgeEvent.MessagesUpdated -> BridgeContract.EVENT_MESSAGES to
                json.encodeToJsonElement(BridgeEvent.MessagesUpdated.serializer(), event)
            is BridgeEvent.CaptionsUpdated -> BridgeContract.EVENT_CAPTIONS to
                json.encodeToJsonElement(BridgeEvent.CaptionsUpdated.serializer(), event)
            is BridgeEvent.ConnectionStatusChanged -> BridgeContract.EVENT_CONNECTION_STATUS to
                json.encodeToJsonElement(BridgeEvent.ConnectionStatusChanged.serializer(), event)
            is BridgeEvent.CallStateChanged -> BridgeContract.EVENT_CALL_STATE to
                json.encodeToJsonElement(BridgeEvent.CallStateChanged.serializer(), event)
            is BridgeEvent.EngineAvailabilityChanged -> BridgeContract.EVENT_ENGINE_AVAILABLE to
                json.encodeToJsonElement(BridgeEvent.EngineAvailabilityChanged.serializer(), event)
        }
        return RpcNotification(method = method, params = params)
    }
}
