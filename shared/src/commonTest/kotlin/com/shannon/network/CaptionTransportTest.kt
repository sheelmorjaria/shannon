package com.shannon.network

import com.shannon.caption.CaptionPayload
import com.shannon.caption.CaptionTransport
import com.shannon.caption.InMemoryCaptionRepository
import com.shannon.speech.Transcript
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionTransportTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun send_encodes_transcript_as_TRANSCRIPT_packet() = runTest {
        val client = FakeReticulumClient()
        val transport = CaptionTransport(client, InMemoryCaptionRepository())

        transport.send(
            Transcript("Hello", "en", isFinal = true, seq = 5),
            sourceHash = "ME",
            destinationHash = "YOU",
        )

        assertEquals(1, client.sentLxstPackets.size)
        val pkt = client.sentLxstPackets.single()
        assertEquals(LxstPacketType.TRANSCRIPT, pkt.type)
        assertEquals("YOU", pkt.destinationHash)
        assertEquals("ME", pkt.sourceHash)
        val decoded = json.decodeFromString(CaptionPayload.serializer(), pkt.payload!!.decodeToString())
        assertEquals("Hello", decoded.text)
        assertEquals(5, decoded.seq)
    }

    @Test
    fun sendVoiceMessage_attaches_transcript_to_lxmf_content() = runTest {
        val client = FakeReticulumClient()
        val transport = CaptionTransport(client, InMemoryCaptionRepository())

        transport.sendVoiceMessage(
            Transcript("Note for you", "en", isFinal = true, seq = 1),
            sourceHash = "ME",
            destinationHash = "YOU",
        )

        assertEquals(1, client.sentLxmfPackets.size)
        val pkt = client.sentLxmfPackets.single()
        assertEquals("YOU", pkt.destinationHash)
        val decoded = json.decodeFromString(CaptionPayload.serializer(), pkt.content)
        assertEquals("Note for you", decoded.text)
        assertEquals("en", decoded.lang)
    }

    @Test
    fun receive_decodes_incoming_TRANSCRIPT_into_repo() = runTest {
        val client = FakeReticulumClient()
        val repo = InMemoryCaptionRepository()
        val receiveJob = CaptionTransport(client, repo).startReceiving(this)

        val payload = CaptionPayload("Bonjour", "fr", translated = "Hello", isFinal = true, seq = 9)
        client.simulateIncomingLxst(
            LxstPacket(
                destinationHash = "ME",
                sourceHash = "THEM",
                type = LxstPacketType.TRANSCRIPT,
                payload = json.encodeToString(CaptionPayload.serializer(), payload).encodeToByteArray(),
            )
        )
        testScheduler.advanceUntilIdle()

        val caps = repo.observeCaptions().first()
        assertEquals(1, caps.size)
        assertEquals("THEM", caps[0].sourceHash)
        assertEquals("Hello", caps[0].translated)
        receiveJob.cancel()
    }

    @Test
    fun receive_ignores_non_TRANSCRIPT_packets() = runTest {
        val client = FakeReticulumClient()
        val repo = InMemoryCaptionRepository()
        val receiveJob = CaptionTransport(client, repo).startReceiving(this)

        client.simulateIncomingLxst(LxstPacket("d", "s", LxstPacketType.AUDIO, byteArrayOf(1, 2, 3)))
        testScheduler.advanceUntilIdle()

        assertEquals(0, repo.observeCaptions().first().size)
        receiveJob.cancel()
    }
}
