package com.shannon.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeDispatcherTest {

    @Test
    fun sendMessage_dispatches_to_backend() = runTest {
        val backend = FakeBridgeBackend()
        BridgeDispatcher(backend).dispatch(BridgeCommand.SendMessage("dst", "hi"))
        assertEquals(listOf("dst" to "hi"), backend.sentMessages)
    }

    @Test
    fun call_and_caption_commands_dispatch() = runTest {
        val backend = FakeBridgeBackend()
        val dispatcher = BridgeDispatcher(backend)
        dispatcher.dispatch(BridgeCommand.SetCaptionsEnabled(true))
        dispatcher.dispatch(BridgeCommand.SetSourceLang(null))
        dispatcher.dispatch(BridgeCommand.StartCall("peer"))
        dispatcher.dispatch(BridgeCommand.EndCall)

        assertEquals(true, backend.captionsEnabled)
        assertEquals(null, backend.sourceLang)
        assertEquals("peer", backend.lastStartedCall)
        assertEquals(1, backend.endCallCount)
    }
}
