package com.shannon.bridge

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Records command calls and exposes configurable flows for bridge-logic tests. */
class FakeBridgeBackend : BridgeBackend {
    val sentMessages = mutableListOf<Pair<String, String>>()
    var lastStartedCall: String? = null; private set
    var endCallCount = 0; private set
    var acceptCallHash: String? = null; private set
    var hangupCount = 0; private set
    var captionsEnabled: Boolean? = null; private set
    var speakTranslations: Boolean? = null; private set
    var sourceLang: String? = UNSET; private set
    var modelTier: String? = null; private set
    var targetLang: String? = UNSET; private set
    var connected: Pair<String, Int>? = null; private set
    var disconnectCount = 0; private set
    var announceCount = 0; private set
    val fedAudioSamples = mutableListOf<Short>()

    private val _messages = MutableStateFlow<List<MessageDto>>(emptyList())
    private val _captions = MutableStateFlow<List<CaptionDto>>(emptyList())
    private val _status = MutableStateFlow(ConnectionStatusDto.DISCONNECTED)
    private val _call = MutableStateFlow(CallStateSnapshotDto(CallStateDto.IDLE))
    private val _engine = MutableStateFlow(false)

    fun setMessages(list: List<MessageDto>) { _messages.value = list }
    fun setStatus(s: ConnectionStatusDto) { _status.value = s }
    fun setEngine(b: Boolean) { _engine.value = b }

    override suspend fun sendMessage(destinationHash: String, content: String) { sentMessages.add(destinationHash to content) }
    override suspend fun startCall(remoteHash: String) { lastStartedCall = remoteHash }
    override suspend fun endCall() { endCallCount++ }
    override suspend fun acceptCall(remoteHash: String) { acceptCallHash = remoteHash }
    override suspend fun hangup() { hangupCount++ }
    override fun setCaptionsEnabled(enabled: Boolean) { captionsEnabled = enabled }
    override fun setSpeakTranslations(enabled: Boolean) { speakTranslations = enabled }
    override fun setSourceLang(lang: String?) { sourceLang = lang }
    override fun setTargetLang(lang: String?) { targetLang = lang }
    override fun setModelTier(tier: String) { modelTier = tier }
    override suspend fun connect(host: String, port: Int) { connected = host to port }
    override suspend fun disconnect() { disconnectCount++ }
    override suspend fun announce() { announceCount++ }
    override fun feedAudioPcm(samples: ShortArray) { fedAudioSamples.addAll(samples.toList()) }

    override fun observeMessages(): Flow<List<MessageDto>> = _messages.asStateFlow()
    override fun observeCaptions(): Flow<List<CaptionDto>> = _captions.asStateFlow()
    override fun observeConnectionStatus(): Flow<ConnectionStatusDto> = _status.asStateFlow()
    override fun observeCallState(): Flow<CallStateSnapshotDto> = _call.asStateFlow()
    override fun observeEngineAvailable(): Flow<Boolean> = _engine.asStateFlow()

    companion object { private const val UNSET = "__unset__" }
}
