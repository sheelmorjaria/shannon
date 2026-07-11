package com.shannon.speech

/**
 * A Speech-to-Text result produced on-device from the local microphone.
 *
 * @property text       Recognized text.
 * @property lang       Detected or pinned spoken language code (e.g. "en").
 * @property translated Optional translation of [text] into the target language.
 * @property speakerId  Optional speaker/diarization label.
 * @property isFinal    false for partial/interim results (early render), true once finalized.
 * @property seq        Monotonic sequence number for ordering and de-duplication across packets.
 */
data class Transcript(
    val text: String,
    val lang: String,
    val translated: String? = null,
    val speakerId: String? = null,
    val isFinal: Boolean,
    val seq: Long,
)
