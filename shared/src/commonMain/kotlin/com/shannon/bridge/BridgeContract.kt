package com.shannon.bridge

/** Protocol constants for the localhost Shannon bridge (JSON-RPC 2.0). */
object BridgeContract {
    /** Bumped whenever the wire contract changes incompatibly; the UI must refuse mismatches. */
    const val SCHEMA_VERSION = 1
    const val JSONRPC = "2.0"

    // --- Commands (client → server) ---
    const val METHOD_SEND_MESSAGE = "message.send"
    const val METHOD_START_CALL = "call.start"
    const val METHOD_END_CALL = "call.end"
    const val METHOD_ACCEPT_CALL = "call.accept"
    const val METHOD_HANGUP = "call.hangup"
    const val METHOD_SET_CAPTIONS_ENABLED = "captions.setEnabled"
    const val METHOD_SET_SPEAK_TRANSLATIONS = "captions.setSpeakTranslations"
    const val METHOD_SET_SOURCE_LANG = "captions.setSourceLang"
    const val METHOD_SET_TARGET_LANG = "captions.setTargetLang"
    const val METHOD_CONNECT = "network.connect"
    const val METHOD_DISCONNECT = "network.disconnect"
    const val METHOD_ANNOUNCE = "network.announce"

    // --- Subscriptions / notifications (server → client) ---
    const val EVENT_MESSAGES = "messages.updated"
    const val EVENT_CAPTIONS = "captions.updated"
    const val EVENT_CONNECTION_STATUS = "connectionStatus.changed"
    const val EVENT_CALL_STATE = "callState.changed"
    const val EVENT_ENGINE_AVAILABLE = "engineAvailability.changed"
}
