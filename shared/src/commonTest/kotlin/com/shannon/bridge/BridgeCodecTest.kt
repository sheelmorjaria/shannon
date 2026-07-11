package com.shannon.bridge

import com.shannon.domain.model.Message
import com.shannon.domain.model.MessageState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.test.Test
import kotlin.test.assertEquals

class BridgeCodecTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun command_roundTrip_sendMessage() {
        val cmd = BridgeCommand.SendMessage(destinationHash = "b".repeat(32), content = "hi")
        val request = BridgeCommandCodec.toRequest(cmd, id = 1)
        val wire = json.encodeToString(RpcRequest.serializer(), request)
        val parsed = json.decodeFromString(RpcRequest.serializer(), wire)

        assertEquals(BridgeContract.METHOD_SEND_MESSAGE, parsed.method)
        assertEquals(cmd, BridgeCommandCodec.decode(parsed.method, parsed.params))
    }

    @Test
    fun command_roundTrip_objectCommand_endCall() {
        val request = BridgeCommandCodec.toRequest(BridgeCommand.EndCall, id = 2)
        assertEquals(BridgeCommand.EndCall, BridgeCommandCodec.decode(request.method, request.params))
    }

    @Test
    fun event_roundTrip_messagesUpdated() {
        val msg = Message(
            id = "m1", destinationHash = "a".repeat(32), content = "x",
            timestamp = 1, state = MessageState.SENT, isOutgoing = true,
        )
        val notification = BridgeEventCodec.toNotification(BridgeEvent.MessagesUpdated(listOf(msg.toDto())))
        val wire = json.encodeToString(RpcNotification.serializer(), notification)
        val parsed = json.decodeFromString(RpcNotification.serializer(), wire)

        assertEquals(BridgeContract.EVENT_MESSAGES, parsed.method)
        val decoded = json.decodeFromJsonElement(BridgeEvent.MessagesUpdated.serializer(), parsed.params)
        assertEquals(1, decoded.messages.size)
        assertEquals("m1", decoded.messages[0].id)
        assertEquals(MessageStateDto.SENT, decoded.messages[0].state)
    }

    @Test
    fun mapper_preserves_message_fields() {
        val m = Message(
            id = "m", destinationHash = "c".repeat(32), content = "hello",
            timestamp = 99, state = MessageState.SENDING, isOutgoing = false,
        )
        val dto = m.toDto()
        assertEquals("m", dto.id)
        assertEquals(MessageStateDto.SENDING, dto.state)
        assertEquals(false, dto.isOutgoing)
    }
}
