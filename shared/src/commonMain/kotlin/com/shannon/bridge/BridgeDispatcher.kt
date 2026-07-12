package com.shannon.bridge

/**
 * Maps a decoded [BridgeCommand] to [BridgeBackend] operations. Pure Kotlin, no transport —
 * the ktor WebSocket layer feeds it commands and the desktop app supplies the backend.
 * Task 2.3.
 */
class BridgeDispatcher(private val backend: BridgeBackend) {
    suspend fun dispatch(command: BridgeCommand) {
        when (command) {
            is BridgeCommand.SendMessage -> backend.sendMessage(command.destinationHash, command.content)
            is BridgeCommand.StartCall -> backend.startCall(command.remoteHash)
            is BridgeCommand.EndCall -> backend.endCall()
            is BridgeCommand.AcceptCall -> backend.acceptCall(command.remoteHash)
            is BridgeCommand.Hangup -> backend.hangup()
            is BridgeCommand.SetCaptionsEnabled -> backend.setCaptionsEnabled(command.enabled)
            is BridgeCommand.SetSpeakTranslations -> backend.setSpeakTranslations(command.enabled)
            is BridgeCommand.SetSourceLang -> backend.setSourceLang(command.lang)
            is BridgeCommand.SetTargetLang -> backend.setTargetLang(command.lang)
            is BridgeCommand.SetModelTier -> backend.setModelTier(command.tier)
            is BridgeCommand.Connect -> backend.connect(command.host, command.port)
            is BridgeCommand.Disconnect -> backend.disconnect()
            is BridgeCommand.Announce -> backend.announce()
        }
    }
}
