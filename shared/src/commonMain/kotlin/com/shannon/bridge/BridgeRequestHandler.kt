package com.shannon.bridge

import kotlinx.serialization.json.JsonObject

/**
 * JSON-RPC framing over the bridge: decode an [RpcRequest] into a [BridgeCommand], dispatch it,
 * and return an [RpcResponse] (success or JSON-RPC error). Task 2.2.
 */
class BridgeRequestHandler(private val dispatcher: BridgeDispatcher) {

    suspend fun handle(request: RpcRequest): RpcResponse {
        val id = request.id ?: return errorResponse(null, INVALID_REQUEST, "request missing id")
        return try {
            val command = BridgeCommandCodec.decode(request.method, request.params)
            dispatcher.dispatch(command)
            RpcResponse(id = id, result = JsonObject(emptyMap()))
        } catch (e: IllegalArgumentException) {
            errorResponse(id, METHOD_NOT_FOUND, e.message ?: "unknown method")
        } catch (e: Exception) {
            errorResponse(id, INTERNAL_ERROR, e.message ?: "internal error")
        }
    }

    private fun errorResponse(id: Long?, code: Int, message: String): RpcResponse =
        RpcResponse(id = id ?: 0, error = RpcError(code, message))

    companion object {
        // JSON-RPC 2.0 error codes.
        const val INVALID_REQUEST = -32600
        const val METHOD_NOT_FOUND = -32601
        const val INTERNAL_ERROR = -32603
    }
}
