package com.shannon.speech

import com.shannon.audio.Pcm
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/**
 * Real on-device STT using Vosk ([com.alphacephei:vosk]). Falls back gracefully
 * ([isAvailable] = false) if the model isn't found or native libs fail to load.
 * TTS returns empty (Vosk is STT-only; TTS needs a separate engine like Piper/Sherpa).
 *
 * Use [switchLanguage] to swap models at runtime (triggered by per-language download via
 * [VoskModelManager]). Thread-safe via a synchronized lock around [feedPcm] / [switchLanguage].
 */
class VoskSpeechEngine(
    private var modelPath: String,
) : SpeechEngine {
    private val lock = Any()
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var callback: ((Transcript) -> Unit)? = null
    private var seq = 0L
    private var currentLang = VoskModelManager.DEFAULT_LANG

    override val isAvailable: Boolean get() = synchronized(lock) { recognizer != null }

    override fun startStt(onTranscript: (Transcript) -> Unit) {
        callback = onTranscript
        seq = 0
        synchronized(lock) {
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                model = Model(modelPath)
                recognizer = Recognizer(model, 16000f)
            } catch (e: Exception) {
                println("Vosk init failed (model at '$modelPath'): ${e.message}")
            }
        }
    }

    override fun feedPcm(samples: ShortArray) {
        synchronized(lock) {
            val rec = recognizer ?: return
            val bytes = Pcm.toByteArray(samples)
            if (rec.acceptWaveForm(bytes, bytes.size)) {
                val text = extractField(rec.result, "text")
                if (text.isNotBlank()) {
                    callback?.invoke(Transcript(text, currentLang, isFinal = true, seq = seq++))
                }
            } else {
                val partial = extractField(rec.partialResult, "partial")
                if (partial.isNotBlank()) {
                    callback?.invoke(Transcript(partial, currentLang, isFinal = false, seq = seq))
                }
            }
        }
    }

    /** Switch to a different language model at runtime. Downloads the model first via [VoskModelManager]. */
    fun switchLanguage(lang: String, newModelPath: String) {
        synchronized(lock) {
            recognizer?.close()
            model?.close()
            currentLang = lang
            modelPath = newModelPath
            try {
                model = Model(newModelPath)
                recognizer = Recognizer(model, 16000f)
                println("Vosk switched to language '$lang' (model: $newModelPath)")
            } catch (e: Exception) {
                recognizer = null
                model = null
                println("Vosk language switch to '$lang' failed: ${e.message}")
            }
        }
    }

    override fun stopStt() {
        synchronized(lock) {
            recognizer?.close()
            model?.close()
            recognizer = null
            model = null
            callback = null
        }
    }

    override fun synthesize(text: String, lang: String): ShortArray = ShortArray(0)

    private fun extractField(json: String, field: String): String {
        val regex = """"$field"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
    }
}
