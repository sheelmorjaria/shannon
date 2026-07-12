package com.shannon.caption

import com.shannon.audio.AudioPlayer
import com.shannon.audio.Pcm
import com.shannon.speech.SpeechEngine

/**
 * Listener-side spoken translation: synthesize a received caption's text on-device and play it
 * through the existing [AudioPlayer]. Only text is ever received over the network — synthesis
 * is local (design D4). Task 3.3.
 */
class CaptionSpeechPlayer(
    private val engine: SpeechEngine,
    private val player: AudioPlayer,
) {
    /** Synthesize [text] in [lang] and play it. No-op when the engine has no model or text is blank. */
    fun speak(text: String, lang: String) {
        if (!engine.isAvailable || text.isBlank()) return
        val pcm = engine.synthesize(text, lang)
        if (pcm.isNotEmpty()) player.playBuffer(Pcm.toByteArray(pcm))
    }

    /** Speak a caption's translation if present, otherwise its original text. */
    fun speakCaption(caption: Caption) {
        speak(caption.translated ?: caption.text, caption.lang)
    }
}
