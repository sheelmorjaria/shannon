package com.shannon.caption

/**
 * A caption rendered in the UI for a (peer's) speech.
 *
 * @property sourceHash  Reticulum identity hash of the speaker who produced this caption.
 */
data class Caption(
    val text: String,
    val lang: String,
    val translated: String? = null,
    val speakerId: String? = null,
    val isFinal: Boolean,
    val seq: Long,
    val sourceHash: String,
)
