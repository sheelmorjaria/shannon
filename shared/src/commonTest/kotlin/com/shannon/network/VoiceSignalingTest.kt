package com.shannon.network

import com.shannon.domain.model.CallState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class VoiceSignalingTest {

    private val localHash = "1234567890abcdef1234567890abcdef"
    private val remoteHash = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4"

    private fun createManager(client: FakeReticulumClient = FakeReticulumClient()): Pair<FakeReticulumClient, VoiceCallManager> {
        return client to VoiceCallManager(client, localHash)
    }

    @Test
    fun `initiating call sends SETUP packet to destination`() = runTest {
        val (client, manager) = createManager()

        manager.initiateCall(remoteHash)

        // Verify a SETUP packet was sent
        assertEquals(1, client.sentLxstPackets.size)
        val packet = client.sentLxstPackets[0]
        assertEquals(LxstPacketType.SETUP, packet.type)
        assertEquals(remoteHash, packet.destinationHash)
        assertEquals(localHash, packet.sourceHash)

        // Verify call state is OUTGOING
        assertEquals(CallState.OUTGOING, manager.callState.value)
    }

    @Test
    fun `receiving REJECT response reverts call to IDLE`() = runTest {
        val (client, manager) = createManager()

        // Initiate call
        manager.initiateCall(remoteHash)
        assertEquals(CallState.OUTGOING, manager.callState.value)

        // Simulate rejection
        manager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.REJECT
            )
        )
        manager.processIncomingLxst()

        assertEquals(CallState.IDLE, manager.callState.value)
    }

    @Test
    fun `receiving BUSY response reverts call to IDLE`() = runTest {
        val (client, manager) = createManager()

        manager.initiateCall(remoteHash)

        // Simulate busy
        manager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.BUSY
            )
        )
        manager.processIncomingLxst()

        assertEquals(CallState.IDLE, manager.callState.value)
    }

    @Test
    fun `receiving ACCEPT transitions call to CONNECTED`() = runTest {
        val (client, manager) = createManager()

        manager.initiateCall(remoteHash)

        // Simulate remote accepting
        manager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.ACCEPT
            )
        )
        manager.processIncomingLxst()

        assertEquals(CallState.CONNECTED, manager.callState.value)
    }

    @Test
    fun `hangup sends HANGUP packet and resets state`() = runTest {
        val (client, manager) = createManager()

        manager.initiateCall(remoteHash)
        manager.onIncomingLxst(
            LxstPacket(remoteHash, localHash, LxstPacketType.ACCEPT)
        )
        manager.processIncomingLxst()
        assertEquals(CallState.CONNECTED, manager.callState.value)

        manager.hangup()

        assertEquals(CallState.IDLE, manager.callState.value)
        // Should have sent HANGUP (index 0 is SETUP, index 1 is HANGUP)
        val hangupPacket = client.sentLxstPackets.last()
        assertEquals(LxstPacketType.HANGUP, hangupPacket.type)
    }

    @Test
    fun `incoming SETUP from remote triggers RINGING state`() {
        val (client, manager) = createManager()

        manager.onIncomingLxst(
            LxstPacket(
                destinationHash = localHash,
                sourceHash = remoteHash,
                type = LxstPacketType.SETUP
            )
        )
        manager.processIncomingLxst()

        assertEquals(CallState.RINGING, manager.callState.value)
        assertEquals(remoteHash, manager.peerHash)
    }

    @Test
    fun `codec negotiation selects best shared codec`() {
        val localCodecs = setOf(AudioCodec.CODEC2_700C, AudioCodec.OPUS)
        val remoteCodecs = setOf(AudioCodec.OPUS, AudioCodec.AMR_WB)

        val selected = negotiateCodec(localCodecs, remoteCodecs)

        assertEquals(AudioCodec.OPUS, selected, "Should select OPUS when both support it")
    }

    @Test
    fun `codec negotiation falls back to CODEC2 when OPUS unavailable`() {
        val localCodecs = setOf(AudioCodec.CODEC2_700C, AudioCodec.CODEC2_1200)
        val remoteCodecs = setOf(AudioCodec.CODEC2_1200, AudioCodec.AMR_WB)

        val selected = negotiateCodec(localCodecs, remoteCodecs)

        assertEquals(AudioCodec.CODEC2_1200, selected, "Should select shared CODEC2_1200")
    }

    @Test
    fun `codec negotiation returns null when no shared codecs`() {
        val localCodecs = setOf(AudioCodec.CODEC2_700C)
        val remoteCodecs = setOf(AudioCodec.OPUS)

        val selected = negotiateCodec(localCodecs, remoteCodecs)

        assertEquals(null, selected, "Should return null when no codecs overlap")
    }
}
