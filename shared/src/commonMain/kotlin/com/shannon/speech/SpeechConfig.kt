package com.shannon.speech

/**
 * Configuration for the on-device speech engine (STT + VAD + TTS).
 *
 * @property modelTier     Quality/size tier for the STT model; smaller tiers run on weaker devices.
 * @property sourceLang    Pinned source language, or null to auto-detect.
 * @property targetLang    Target language for translation/TTS, or null for no translation.
 * @property vadThreshold  Silero VAD speech-probability threshold (0..1); higher = less sensitive.
 */
data class SpeechConfig(
    val modelTier: ModelTier = ModelTier.BASE,
    val sourceLang: String? = null,
    val targetLang: String? = null,
    val vadThreshold: Float = 0.5f,
) {
    enum class ModelTier { TINY, BASE, MEDIUM, ZIPFORMER }
}
