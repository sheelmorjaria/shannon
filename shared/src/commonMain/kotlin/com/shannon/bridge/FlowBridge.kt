package com.shannon.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Adapts the backend's Kotlin [kotlinx.coroutines.flow.Flow]s into JSON-RPC notification streams
 * the React UI can subscribe to. Task 2.4.
 */
class FlowBridge(private val backend: BridgeBackend) {
    fun messages(): Flow<RpcNotification> =
        backend.observeMessages().map { BridgeEventCodec.toNotification(BridgeEvent.MessagesUpdated(it)) }

    fun captions(): Flow<RpcNotification> =
        backend.observeCaptions().map { BridgeEventCodec.toNotification(BridgeEvent.CaptionsUpdated(it)) }

    fun connectionStatus(): Flow<RpcNotification> =
        backend.observeConnectionStatus().map { BridgeEventCodec.toNotification(BridgeEvent.ConnectionStatusChanged(it)) }

    fun callState(): Flow<RpcNotification> =
        backend.observeCallState().map { BridgeEventCodec.toNotification(BridgeEvent.CallStateChanged(it)) }

    fun engineAvailable(): Flow<RpcNotification> =
        backend.observeEngineAvailable().map { BridgeEventCodec.toNotification(BridgeEvent.EngineAvailabilityChanged(it)) }
}
