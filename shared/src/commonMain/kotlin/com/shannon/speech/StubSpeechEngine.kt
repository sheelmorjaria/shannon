package com.shannon.speech

/**
 * No-op [SpeechEngine] used when no on-device ML model is available — graceful degradation.
 * [isAvailable] is false, STT produces nothing, and TTS returns empty PCM. This is the default
 * engine until the real Sherpa-ONNX platform implementations (§2) are wired in.
 */
class StubSpeechEngine : SpeechEngine {
    override val isAvailable: Boolean = false

    override fun startStt(onTranscript: (Transcript) -> Unit) { /* no-op: no model */ }
    override fun feedPcm(samples: ShortArray) { /* no-op: no model */ }
    override fun stopStt() { /* no-op */ }

    override fun synthesize(text: String, lang: String): ShortArray = ShortArray(0)
}

/** Default [SpeechEngineProvider] that always returns a [StubSpeechEngine]. Swap for a real
 *  platform provider once the Sherpa-ONNX bindings (§2) are integrated. */
class StubSpeechEngineProvider : SpeechEngineProvider {
    override fun create(config: SpeechConfig): SpeechEngine = StubSpeechEngine()
}
