package com.shannon.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** JSON-RPC 2.0 request envelope (client → server). [id] is null for notifications. */
@Serializable
data class RpcRequest(
    val jsonrpc: String = BridgeContract.JSONRPC,
    val id: Long? = null,
    val method: String,
    val params: JsonElement = JsonObject(emptyMap()),
)

@Serializable
data class RpcError(val code: Int, val message: String)

/** JSON-RPC 2.0 response envelope (server → client). */
@Serializable
data class RpcResponse(
    val jsonrpc: String = BridgeContract.JSONRPC,
    val id: Long,
    val result: JsonElement? = null,
    val error: RpcError? = null,
)

/** JSON-RPC 2.0 notification envelope (server → client, one-way push for subscriptions). */
@Serializable
data class RpcNotification(
    val jsonrpc: String = BridgeContract.JSONRPC,
    val method: String,
    val params: JsonElement,
)
