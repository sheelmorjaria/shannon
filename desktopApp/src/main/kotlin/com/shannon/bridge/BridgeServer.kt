package com.shannon.bridge

import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/**
 * localhost-only WebSocket bridge server (task 2.1). Hosts the JSON-RPC contract over a single
 * "/bridge" socket: dispatches client [RpcRequest]s via [BridgeRequestHandler] and pushes server
 * [RpcNotification]s from [FlowBridge].
 *
 * Binds 127.0.0.1 only — never a remote interface (NFR: no remote/cloud server). Uses the CIO
 * engine (pure JVM, no native dependencies), suitable for a desktop app.
 */
class BridgeServer(
    backend: BridgeBackend,
    private val port: Int = DEFAULT_PORT,
) {
    private val handler = BridgeRequestHandler(BridgeDispatcher(backend))
    private val flowBridge = FlowBridge(backend)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    /** Start the localhost server (non-blocking). Throws if already started. */
    fun start() {
        check(server == null) { "BridgeServer already started" }
        val engine = embeddedServer(CIO, host = "127.0.0.1", port = port) {
            install(WebSockets)
            routing {
                webSocket("/bridge") {
                    // Outbound: stream merged flow notifications as JSON-RPC notifications.
                    val outgoingJob = launch {
                        merge(
                            flowBridge.messages(),
                            flowBridge.captions(),
                            flowBridge.connectionStatus(),
                            flowBridge.callState(),
                            flowBridge.engineAvailable(),
                        ).collect { notification ->
                            outgoing.send(Frame.Text(json.encodeToString(RpcNotification.serializer(), notification)))
                        }
                    }
                    try {
                        // Inbound: read JSON-RPC requests and reply with responses.
                        for (frame in incoming) {
                            val text = (frame as? Frame.Text)?.readText() ?: continue
                            val response = try {
                                val request = json.decodeFromString(RpcRequest.serializer(), text)
                                handler.handle(request)
                            } catch (e: Exception) {
                                RpcResponse(id = 0, error = RpcError(-32700, "Parse error: ${e.message}"))
                            }
                            outgoing.send(Frame.Text(json.encodeToString(RpcResponse.serializer(), response)))
                        }
                    } finally {
                        outgoingJob.cancel()
                    }
                }
            }
        }
        engine.start(wait = false)
        server = engine
    }

    /** Stop the server, releasing the port. */
    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    companion object {
        const val DEFAULT_PORT = 47329
    }
}
