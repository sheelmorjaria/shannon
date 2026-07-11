package com.shannon.bridge

import com.shannon.domain.model.Message
import com.shannon.domain.repository.MessageRepository
import com.shannon.network.ReticulumClient
import com.shannon.network.VoiceCallManagerIntegrated
import com.shannon.speech.SpeechEngine
import com.shannon.viewmodel.CaptionViewModel
import kotlinx.datetime.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Production [BridgeBackend]: wires bridge commands/subscriptions to the real Shannon services —
 * [MessageRepository], [ReticulumClient], [VoiceCallManagerIntegrated], [CaptionViewModel],
 * [SpeechEngine]. Task 2.3 (concrete wiring). The desktop app constructs this with its Koin-provided
 * services and hands it to [BridgeServer].
 *
 * `observeMessages` emits the currently-selected conversation's thread; select it via
 * [selectContact] (a "select contact" command lands with the React ConversationList, §3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultBridgeBackend(
    private val messages: MessageRepository,
    private val client: ReticulumClient,
    private val calls: VoiceCallManagerIntegrated,
    private val captions: CaptionViewModel,
    private val speech: SpeechEngine,
    private val localHash: String,
) : BridgeBackend {

    private val selectedContact = MutableStateFlow<String?>(null)

    /** Select the conversation whose message thread [observeMessages] emits. */
    fun selectContact(hash: String?) {
        selectedContact.value = hash
    }

    // --- commands ---
    override suspend fun sendMessage(destinationHash: String, content: String) {
        messages.send(
            Message(destinationHash = destinationHash, content = content, timestamp = Clock.System.now().toEpochMilliseconds())
        )
    }

    override suspend fun startCall(remoteHash: String) = calls.initiateCall(remoteHash)
    override suspend fun endCall() = calls.hangup()
    override suspend fun acceptCall(remoteHash: String) = calls.acceptCall()
    override suspend fun hangup() = calls.hangup()

    override fun setCaptionsEnabled(enabled: Boolean) = captions.setCaptionsEnabled(enabled)
    override fun setSpeakTranslations(enabled: Boolean) = captions.setSpeakTranslations(enabled)
    override fun setSourceLang(lang: String?) = captions.setSourceLang(lang)
    override fun setTargetLang(lang: String?) = captions.setTargetLang(lang)

    override suspend fun connect(host: String, port: Int) = client.connect(host, port)
    override suspend fun disconnect() = client.disconnect()
    override suspend fun announce() = client.announce()

    // --- subscriptions ---
    override fun observeMessages(): Flow<List<MessageDto>> =
        selectedContact.flatMapLatest { hash ->
            if (hash == null) flowOf(emptyList())
            else messages.observeMessages(hash).map { list -> list.map { it.toDto() } }
        }

    override fun observeCaptions(): Flow<List<CaptionDto>> =
        captions.captions.map { list -> list.map { it.toDto() } }

    override fun observeConnectionStatus(): Flow<ConnectionStatusDto> =
        client.observeStatus().map { it.toDto() }

    /** peerHash is not exposed by the call manager, so it is reported as null here. */
    override fun observeCallState(): Flow<CallStateSnapshotDto> =
        calls.callState.map { CallStateSnapshotDto(it.toDto()) }

    override fun observeEngineAvailable(): Flow<Boolean> = flowOf(speech.isAvailable)
}
