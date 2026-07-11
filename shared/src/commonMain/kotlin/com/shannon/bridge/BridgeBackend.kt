package com.shannon.bridge

import kotlinx.coroutines.flow.Flow

/**
 * The Kotlin-core operations the bridge exposes to the React UI. Implemented by the desktop app
 * (wired to repositories / VoiceCallManager / CaptionViewModel / ReticulumClient) and faked in
 * tests. DTO-typed so the bridge logic stays free of domain mappings.
 */
interface BridgeBackend {
    // --- commands ---
    suspend fun sendMessage(destinationHash: String, content: String)
    suspend fun startCall(remoteHash: String)
    suspend fun endCall()
    suspend fun acceptCall(remoteHash: String)
    suspend fun hangup()
    fun setCaptionsEnabled(enabled: Boolean)
    fun setSpeakTranslations(enabled: Boolean)
    fun setSourceLang(lang: String?)
    fun setTargetLang(lang: String?)
    suspend fun connect(host: String, port: Int)
    suspend fun disconnect()
    suspend fun announce()

    // --- subscriptions (server → client streams) ---
    fun observeMessages(): Flow<List<MessageDto>>
    fun observeCaptions(): Flow<List<CaptionDto>>
    fun observeConnectionStatus(): Flow<ConnectionStatusDto>
    fun observeCallState(): Flow<CallStateSnapshotDto>
    fun observeEngineAvailable(): Flow<Boolean>
}
