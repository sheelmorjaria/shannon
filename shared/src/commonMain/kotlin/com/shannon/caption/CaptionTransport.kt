package com.shannon.caption

import com.shannon.domain.repository.CaptionRepository
import com.shannon.network.LxmfPacket
import com.shannon.network.LxstPacket
import com.shannon.network.LxstPacketType
import com.shannon.network.ReticulumClient
import com.shannon.speech.Transcript
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json

/**
 * Moves captions across the Reticulum network as small JSON text payloads — never audio.
 *
 * - [send]: encode a local [Transcript] as a [CaptionPayload] and ship it in an LXST TRANSCRIPT packet.
 * - [startReceiving]: observe incoming TRANSCRIPT packets, decode them, and push them into the
 *   [CaptionRepository] (de-duplicated by seq via [CaptionRepository.upsert]).
 *
 * Task 4.1 / 4.2 / 4.4.
 */
class CaptionTransport(
    private val client: ReticulumClient,
    private val repository: CaptionRepository,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    /** Serialize [transcript] and send it as an LXST TRANSCRIPT packet to [destinationHash]. */
    suspend fun send(transcript: Transcript, sourceHash: String, destinationHash: String) {
        val encoded = json.encodeToString(CaptionPayload.serializer(), CaptionPayload.from(transcript))
            .encodeToByteArray()
        client.sendLxstPacket(
            LxstPacket(
                destinationHash = destinationHash,
                sourceHash = sourceHash,
                type = LxstPacketType.TRANSCRIPT,
                payload = encoded,
            )
        )
    }

    /** Attach a transcript to an asynchronous voice message as JSON in the [LxmfPacket] content (task 4.3). */
    suspend fun sendVoiceMessage(transcript: Transcript, sourceHash: String, destinationHash: String) {
        val content = json.encodeToString(CaptionPayload.serializer(), CaptionPayload.from(transcript))
        client.sendLxmfPacket(
            LxmfPacket(
                destinationHash = destinationHash,
                sourceHash = sourceHash,
                content = content,
            )
        )
    }

    /** Begin collecting incoming TRANSCRIPT packets into the [repository]. Runs in [scope].
     *  Returns the collecting [Job]; cancel it (e.g. when the call ends) to stop receiving. */
    fun startReceiving(scope: CoroutineScope): Job =
        client.observeIncomingLxstPackets().onEach { packet ->
            if (packet.type != LxstPacketType.TRANSCRIPT) return@onEach
            val bytes = packet.payload ?: return@onEach
            runCatching {
                json.decodeFromString(CaptionPayload.serializer(), bytes.decodeToString())
            }.getOrNull()?.let { payload ->
                repository.upsert(payload.toCaption(packet.sourceHash))
            }
        }.launchIn(scope)
}
