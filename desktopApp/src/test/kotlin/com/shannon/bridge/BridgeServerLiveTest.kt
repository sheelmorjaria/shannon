package com.shannon.bridge

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * End-to-end test of the bridge over a real localhost WebSocket (task 2.5): the ktor [BridgeServer]
 * transports JSON-RPC requests/responses and flow notifications. Verifies the actual transport,
 * not just the dispatcher logic (which has its own unit tests in :shared).
 */
class BridgeServerLiveTest {

    @Test
    fun request_round_trips_over_websocket() = runBlocking {
        val server = BridgeServer(EchoBackend(), port = PORT)
        server.start()
        try {
            val json = Json { ignoreUnknownKeys = true }
            HttpClient(CIO) { install(WebSockets) }.use { client ->
                client.webSocket("ws://127.0.0.1:$PORT/bridge") {
                    send(Frame.Text("""{"jsonrpc":"2.0","id":42,"method":"network.announce","params":{}}"""))
                    // The server also streams flow notifications (no "id"); read frames until the
                    // response with id == 42 arrives.
                    var response: RpcResponse? = null
                    while (response == null) {
                        val frame = incoming.receive() as? Frame.Text ?: continue
                        runCatching { json.decodeFromString(RpcResponse.serializer(), frame.readText()) }
                            .getOrNull()?.takeIf { it.id == 42L }?.let { response = it }
                    }
                    assertEquals(42L, response.id)
                    assertNull(response.error)
                }
            }
        } finally {
            server.stop()
        }
    }

    /** Minimal [BridgeBackend]: commands are no-ops; flows emit a single value then complete. */
    private class EchoBackend : BridgeBackend {
        override suspend fun sendMessage(destinationHash: String, content: String) {}
        override suspend fun startCall(remoteHash: String) {}
        override suspend fun endCall() {}
        override suspend fun acceptCall(remoteHash: String) {}
        override suspend fun hangup() {}
        override fun setCaptionsEnabled(enabled: Boolean) {}
        override fun setSpeakTranslations(enabled: Boolean) {}
        override fun setSourceLang(lang: String?) {}
        override fun setTargetLang(lang: String?) {}
        override fun setModelTier(tier: String) {}
        override suspend fun connect(host: String, port: Int) {}
        override suspend fun disconnect() {}
        override suspend fun announce() {}
        override fun feedAudioPcm(samples: ShortArray) {}
        override fun observeMessages() = flowOf(emptyList<MessageDto>())
        override fun observeCaptions() = flowOf(emptyList<CaptionDto>())
        override fun observeConnectionStatus() = flowOf(ConnectionStatusDto.DISCONNECTED)
        override fun observeCallState() = flowOf(CallStateSnapshotDto(CallStateDto.IDLE))
        override fun observeEngineAvailable() = flowOf(false)
    }

    companion object {
        // Fixed localhost port for the live test (BridgeServer binds 127.0.0.1 only).
        private const val PORT = 47813
    }
}
