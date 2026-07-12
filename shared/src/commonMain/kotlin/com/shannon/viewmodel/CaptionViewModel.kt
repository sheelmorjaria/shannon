package com.shannon.viewmodel

import com.shannon.caption.Caption
import com.shannon.domain.repository.CaptionRepository
import com.shannon.speech.SpeechEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state for live captions and translation settings during a call (tasks 1.6 / 5.x).
 *
 * @param observeScope Scope for long-lived flow collection (pass `TestScope.backgroundScope` in tests).
 */
class CaptionViewModel(
    private val repository: CaptionRepository,
    private val speechEngine: SpeechEngine,
    private val observeScope: CoroutineScope,
) {
    /** Ordered live captions for the current call. */
    val captions: StateFlow<List<Caption>> =
        repository.observeCaptions().stateIn(observeScope, SharingStarted.Eagerly, emptyList())

    private val _captionsEnabled = MutableStateFlow(false)
    val captionsEnabled: StateFlow<Boolean> = _captionsEnabled.asStateFlow()

    private val _speakTranslations = MutableStateFlow(false)
    val speakTranslations: StateFlow<Boolean> = _speakTranslations.asStateFlow()

    /** Pinned source language, or null to auto-detect. */
    private val _sourceLang = MutableStateFlow<String?>(null)
    val sourceLang: StateFlow<String?> = _sourceLang.asStateFlow()

    private val _targetLang = MutableStateFlow<String?>(null)
    val targetLang: StateFlow<String?> = _targetLang.asStateFlow()

    /** Whether an on-device model is loaded and inference can run. */
    val isEngineAvailable: Boolean get() = speechEngine.isAvailable

    fun setCaptionsEnabled(enabled: Boolean) { _captionsEnabled.value = enabled }
    fun setSpeakTranslations(enabled: Boolean) { _speakTranslations.value = enabled }
    fun setSourceLang(lang: String?) { _sourceLang.value = lang }
    fun setTargetLang(lang: String?) { _targetLang.value = lang }

    /** Clear captions (e.g. when the call ends). */
    fun clear() {
        observeScope.launch { repository.clear() }
    }
}
