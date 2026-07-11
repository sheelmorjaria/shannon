package com.shannon.speech

/**
 * Platform-agnostic on-device speech engine: Speech-to-Text (with VAD) and Text-to-Speech.
 *
 * Implementations live per-platform (Android Sherpa-ONNX AAR, Desktop Sherpa-ONNX JVM) and are
 * constructed via a [SpeechEngineProvider] wired through Koin. This mirrors Shannon's existing
 * [com.shannon.audio.AudioRecorder] / [com.shannon.audio.AudioPlayer] interface+DI pattern, and
 * avoids expect/actual so commonMain compiles independently of platform source sets.
 *
 * All inference MUST run off the audio callback thread (see design.md and the NFRs).
 */
interface SpeechEngine {
    /** True when a model is loaded and the device can run inference; false => degrade gracefully. */
    val isAvailable: Boolean

    /** Begin a streaming STT session; recognized utterances arrive via [onTranscript]. */
    fun startStt(onTranscript: (Transcript) -> Unit)

    /** Feed 16-bit PCM microphone samples; VAD segments them into utterances internally. */
    fun feedPcm(samples: ShortArray)

    /** End the STT session and release inference resources. */
    fun stopStt()

    /** Synthesize [text] in [lang] to 16-bit PCM for local playback. */
    fun synthesize(text: String, lang: String): ShortArray
}

/**
 * Platform-specific factory for [SpeechEngine]; registered in Koin (e.g. the caption module in
 * [com.shannon.di.AppModule]). Replaces the originally specified `expect fun createSpeechEngine`
 * (see the tasks.md note on 1.2): it fits Shannon's interface+DI convention and does not require
 * `actual` impls in a platform source set that is not yet configured (androidMain).
 */
interface SpeechEngineProvider {
    fun create(config: SpeechConfig): SpeechEngine
}
