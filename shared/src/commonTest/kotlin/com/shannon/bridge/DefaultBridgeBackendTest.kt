package com.shannon.bridge

import com.shannon.audio.AudioEngine
import com.shannon.audio.FakeAudioPlayer
import com.shannon.audio.FakeAudioRecorder
import com.shannon.caption.InMemoryCaptionRepository
import com.shannon.network.ConnectionStatus
import com.shannon.network.FakeReticulumClient
import com.shannon.network.VoiceCallManagerIntegrated
import com.shannon.speech.StubSpeechEngine
import com.shannon.viewmodel.CaptionViewModel
import com.shannon.viewmodel.FakeMessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBridgeBackendTest {

    /**
     * Harness built on its own real CoroutineScope (not the runTest TestScope): CaptionViewModel
     * starts its `captions` stateIn(Eagerly) collector, which never completes and would otherwise
     * trip runTest's UncompletedCoroutinesError. The runTest scope is still used for the suspend
     * assertions in each test.
     */
    private class Harness(
        val backend: DefaultBridgeBackend,
        val messages: FakeMessageRepository,
        val client: FakeReticulumClient,
        val captions: CaptionViewModel,
        val scope: CoroutineScope,
    )

    private fun harness(): Harness {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val client = FakeReticulumClient()
        val messages = FakeMessageRepository()
        val callManager = VoiceCallManagerIntegrated(
            client = client,
            localHash = "me",
            audioEngine = AudioEngine(FakeAudioRecorder(), FakeAudioPlayer()),
            scope = scope,
        )
        val captions = CaptionViewModel(InMemoryCaptionRepository(), StubSpeechEngine(), scope)
        val backend = DefaultBridgeBackend(messages, client, callManager, captions, StubSpeechEngine(), "me")
        return Harness(backend, messages, client, captions, scope)
    }

    @Test
    fun sendMessage_wires_to_repository() = runTest {
        val h = harness()
        h.backend.sendMessage("d".repeat(32), "hi")
        assertEquals("hi", h.messages.lastSentMessage?.content)
    }

    @Test
    fun setCaptionsEnabled_wires_to_view_model() = runTest {
        val h = harness()
        h.backend.setCaptionsEnabled(true)
        assertEquals(true, h.captions.captionsEnabled.value)
    }

    @Test
    fun observeConnectionStatus_maps_client_status() = runTest {
        val h = harness()
        assertEquals(ConnectionStatusDto.DISCONNECTED, h.backend.observeConnectionStatus().first())
        h.client.simulateStatusChange(ConnectionStatus.CONNECTED)
        assertEquals(ConnectionStatusDto.CONNECTED, h.backend.observeConnectionStatus().first())
    }

    @Test
    fun observeCallState_starts_idle() = runTest {
        val h = harness()
        assertEquals(CallStateDto.IDLE, h.backend.observeCallState().first().state)
    }
}
