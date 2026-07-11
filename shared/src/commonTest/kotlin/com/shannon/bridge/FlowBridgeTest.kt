package com.shannon.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBridgeTest {

    @Test
    fun messages_flow_emits_notification() = runTest {
        val backend = FakeBridgeBackend()
        backend.setMessages(listOf(MessageDto("m", "d".repeat(32), "x", 1, MessageStateDto.SENT, true)))
        val notification = FlowBridge(backend).messages().first()
        assertEquals(BridgeContract.EVENT_MESSAGES, notification.method)
    }

    @Test
    fun connectionStatus_flow_emits_notification() = runTest {
        val backend = FakeBridgeBackend()
        backend.setStatus(ConnectionStatusDto.CONNECTED)
        val notification = FlowBridge(backend).connectionStatus().first()
        assertEquals(BridgeContract.EVENT_CONNECTION_STATUS, notification.method)
    }
}
