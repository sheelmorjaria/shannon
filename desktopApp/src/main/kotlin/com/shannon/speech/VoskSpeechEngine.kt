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
 * Replaces the design's primary Sherpa-ONNX with Vosk as the design-allowed fallback,
 * because Sherpa-ONNX has no standard Maven distribution (GitHub Releases only).
 */
class VoskSpeechEngine(
    private val modelPath: String,
) : SpeechEngine {
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var callback: ((Transcript) -> Unit)? = null
    private var seq = 0L

    override val isAvailable: Boolean get() = recognizer != null

    override fun startStt(onTranscript: (Transcript) -> Unit) {
        callback = onTranscript
        seq = 0
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            model = Model(modelPath)
            recognizer = Recognizer(model, 16000f)
        } catch (e: Exception) {
            println("Vosk init failed (model at '$modelPath'): ${e.message}")
        }
    }

    override fun feedPcm(samples: ShortArray) {
        val rec = recognizer ?: return
        val bytes = Pcm.toByteArray(samples)
        if (rec.acceptWaveForm(bytes, bytes.size)) {
            val text = extractField(rec.result, "text")
            if (text.isNotBlank()) {
                callback?.invoke(Transcript(text, "en", isFinal = true, seq = seq++))
            }
        } else {
            val partial = extractField(rec.partialResult, "partial")
            if (partial.isNotBlank()) {
                callback?.invoke(Transcript(partial, "en", isFinal = false, seq = seq))
            }
        }
    }

    override fun stopStt() {
        recognizer?.close()
        model?.close()
        recognizer = null
        model = null
        callback = null
    }

    override fun synthesize(text: String, lang: String): ShortArray = ShortArray(0)

    private fun extractField(json: String, field: String): String {
        val regex = """"$field"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
    }
}
