package com.shannon.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Commands the React UI sends to the Kotlin core over the bridge (client → server). */
sealed class BridgeCommand {
    @Serializable data class SendMessage(val destinationHash: String, val content: String) : BridgeCommand()
    @Serializable data class StartCall(val remoteHash: String) : BridgeCommand()
    @Serializable object EndCall : BridgeCommand()
    @Serializable data class AcceptCall(val remoteHash: String) : BridgeCommand()
    @Serializable object Hangup : BridgeCommand()
    @Serializable data class SetCaptionsEnabled(val enabled: Boolean) : BridgeCommand()
    @Serializable data class SetSpeakTranslations(val enabled: Boolean) : BridgeCommand()
    @Serializable data class SetSourceLang(val lang: String?) : BridgeCommand()
    @Serializable data class SetTargetLang(val lang: String?) : BridgeCommand()
    @Serializable data class Connect(val host: String, val port: Int) : BridgeCommand()
    @Serializable object Disconnect : BridgeCommand()
    @Serializable object Announce : BridgeCommand()
}

/** Encode/decode [BridgeCommand]s to/from JSON-RPC method + params. */
object BridgeCommandCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(command: BridgeCommand): Pair<String, JsonElement> = when (command) {
        is BridgeCommand.SendMessage -> BridgeContract.METHOD_SEND_MESSAGE to
            json.encodeToJsonElement(BridgeCommand.SendMessage.serializer(), command)
        is BridgeCommand.StartCall -> BridgeContract.METHOD_START_CALL to
            json.encodeToJsonElement(BridgeCommand.StartCall.serializer(), command)
        is BridgeCommand.EndCall -> BridgeContract.METHOD_END_CALL to JsonObject(emptyMap())
        is BridgeCommand.AcceptCall -> BridgeContract.METHOD_ACCEPT_CALL to
            json.encodeToJsonElement(BridgeCommand.AcceptCall.serializer(), command)
        is BridgeCommand.Hangup -> BridgeContract.METHOD_HANGUP to JsonObject(emptyMap())
        is BridgeCommand.SetCaptionsEnabled -> BridgeContract.METHOD_SET_CAPTIONS_ENABLED to
            json.encodeToJsonElement(BridgeCommand.SetCaptionsEnabled.serializer(), command)
        is BridgeCommand.SetSpeakTranslations -> BridgeContract.METHOD_SET_SPEAK_TRANSLATIONS to
            json.encodeToJsonElement(BridgeCommand.SetSpeakTranslations.serializer(), command)
        is BridgeCommand.SetSourceLang -> BridgeContract.METHOD_SET_SOURCE_LANG to
            json.encodeToJsonElement(BridgeCommand.SetSourceLang.serializer(), command)
        is BridgeCommand.SetTargetLang -> BridgeContract.METHOD_SET_TARGET_LANG to
            json.encodeToJsonElement(BridgeCommand.SetTargetLang.serializer(), command)
        is BridgeCommand.Connect -> BridgeContract.METHOD_CONNECT to
            json.encodeToJsonElement(BridgeCommand.Connect.serializer(), command)
        is BridgeCommand.Disconnect -> BridgeContract.METHOD_DISCONNECT to JsonObject(emptyMap())
        is BridgeCommand.Announce -> BridgeContract.METHOD_ANNOUNCE to JsonObject(emptyMap())
    }

    fun decode(method: String, params: JsonElement): BridgeCommand = when (method) {
        BridgeContract.METHOD_SEND_MESSAGE -> json.decodeFromJsonElement(BridgeCommand.SendMessage.serializer(), params)
        BridgeContract.METHOD_START_CALL -> json.decodeFromJsonElement(BridgeCommand.StartCall.serializer(), params)
        BridgeContract.METHOD_END_CALL -> BridgeCommand.EndCall
        BridgeContract.METHOD_ACCEPT_CALL -> json.decodeFromJsonElement(BridgeCommand.AcceptCall.serializer(), params)
        BridgeContract.METHOD_HANGUP -> BridgeCommand.Hangup
        BridgeContract.METHOD_SET_CAPTIONS_ENABLED -> json.decodeFromJsonElement(BridgeCommand.SetCaptionsEnabled.serializer(), params)
        BridgeContract.METHOD_SET_SPEAK_TRANSLATIONS -> json.decodeFromJsonElement(BridgeCommand.SetSpeakTranslations.serializer(), params)
        BridgeContract.METHOD_SET_SOURCE_LANG -> json.decodeFromJsonElement(BridgeCommand.SetSourceLang.serializer(), params)
        BridgeContract.METHOD_SET_TARGET_LANG -> json.decodeFromJsonElement(BridgeCommand.SetTargetLang.serializer(), params)
        BridgeContract.METHOD_CONNECT -> json.decodeFromJsonElement(BridgeCommand.Connect.serializer(), params)
        BridgeContract.METHOD_DISCONNECT -> BridgeCommand.Disconnect
        BridgeContract.METHOD_ANNOUNCE -> BridgeCommand.Announce
        else -> throw IllegalArgumentException("Unknown bridge method: $method")
    }

    /** Wrap a command as a JSON-RPC request with the given [id]. */
    fun toRequest(command: BridgeCommand, id: Long): RpcRequest {
        val (method, params) = encode(command)
        return RpcRequest(id = id, method = method, params = params)
    }
}
