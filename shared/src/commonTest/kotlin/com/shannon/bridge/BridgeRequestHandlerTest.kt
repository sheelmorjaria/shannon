package com.shannon.bridge

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class BridgeRequestHandlerTest {

    private fun handler() = BridgeRequestHandler(BridgeDispatcher(FakeBridgeBackend()))

    @Test
    fun valid_request_returns_success_response() = runTest {
        val request = BridgeCommandCodec.toRequest(BridgeCommand.SendMessage("dst", "hi"), id = 7)
        val response = handler().handle(request)
        assertEquals(7L, response.id)
        assertNull(response.error)
        assertNotNull(response.result)
    }

    @Test
    fun unknown_method_returns_method_not_found() = runTest {
        val response = handler().handle(RpcRequest(id = 3, method = "nope", params = JsonObject(emptyMap())))
        assertEquals(3L, response.id)
        assertNotNull(response.error)
        assertEquals(BridgeRequestHandler.METHOD_NOT_FOUND, response.error!!.code)
    }

    @Test
    fun request_without_id_is_invalid() = runTest {
        val response = handler().handle(RpcRequest(id = null, method = BridgeContract.METHOD_ANNOUNCE))
        assertNotNull(response.error)
        assertEquals(BridgeRequestHandler.INVALID_REQUEST, response.error!!.code)
    }
}
