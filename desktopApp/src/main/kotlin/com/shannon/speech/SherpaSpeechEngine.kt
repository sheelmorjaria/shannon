package com.shannon.speech

import com.k2fsa.sherpa.onnx.GeneratedAudio
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig
import java.io.File

/**
 * Full on-device STT + TTS + VAD using Sherpa-ONNX ([com.litongjava:sherpa-onnx-java-api]).
 *
 * - STT: streaming Zipformer via [OnlineRecognizer] for low-latency captions.
 * - TTS: Piper VITS via [OfflineTts] for "Speak Translations" (returns real audio, not empty).
 * - VAD: built-in endpoint detection in OnlineRecognizer (utterance segmentation).
 *
 * Falls back gracefully ([isAvailable] = false) if native libs or models aren't available.
 * Thread-safe via synchronized lock around all JNI calls.
 *
 * Replaces VoskSpeechEngine (STT-only) with full STT + TTS per the original design.
 */
class SherpaSpeechEngine(
    private val cacheDir: File,
) : SpeechEngine {
    private val lock = Any()
    private var recognizer: OnlineRecognizer? = null
    private var stream: OnlineStream? = null
    private var tts: OfflineTts? = null
    private var callback: ((Transcript) -> Unit)? = null
    private var seq = 0L
    private var currentLang = SherpaModelManager.DEFAULT_LANG
    private var models: SherpaModelManager.ResolvedModels? = null

    override val isAvailable: Boolean get() = synchronized(lock) { recognizer != null }

    override fun startStt(onTranscript: (Transcript) -> Unit) {
        callback = onTranscript
        seq = 0
        synchronized(lock) {
            try {
                SherpaNativeLoader.ensureLoaded(cacheDir)
                models = SherpaModelManager.ensureModels(currentLang, cacheDir)
                val resolved = models ?: run {
                    println("Sherpa: models not available for '$currentLang'")
                    return@synchronized
                }
                initRecognizer(resolved)
                initTts(resolved)
            } catch (e: Exception) {
                println("Sherpa init failed: ${e.message}")
            }
        }
    }

    private fun initRecognizer(resolved: SherpaModelManager.ResolvedModels) {
        val sttDir = File(resolved.sttDir)
        val transducer = OnlineTransducerModelConfig.builder()
            .setEncoder(File(sttDir, "encoder-epoch-99-avg-1.onnx").absolutePath)
            .setDecoder(File(sttDir, "decoder-epoch-99-avg-1.onnx").absolutePath)
            .setJoiner(File(sttDir, "joiner-epoch-99-avg-1.onnx").absolutePath)
            .build()
        val modelConfig = OnlineModelConfig.builder()
            .setTransducer(transducer)
            .setTokens(File(sttDir, "tokens.txt").absolutePath)
            .setNumThreads(1)
            .build()
        val config = OnlineRecognizerConfig.builder()
            .setOnlineModelConfig(modelConfig)
            .setDecodingMethod("greedy_search")
            .build()
        recognizer = OnlineRecognizer(config)
        stream = recognizer!!.createStream()
        println("Sherpa STT initialized for language '$currentLang'")
    }

    private fun initTts(resolved: SherpaModelManager.ResolvedModels) {
        val ttsDir = resolved.ttsDir ?: return
        val vits = OfflineTtsVitsModelConfig.builder()
            .setModel(File(ttsDir, "model.onnx").absolutePath)
            .setTokens(File(ttsDir, "tokens.txt").absolutePath)
            .setDataDir(File(ttsDir, "espeak-ng-data").absolutePath)
            .build()
        val config = OfflineTtsConfig.builder()
            .setModel(OfflineTtsModelConfig.builder().setVits(vits).build())
            .build()
        tts = OfflineTts(config)
        println("Sherpa TTS initialized for language '$currentLang'")
    }

    override fun feedPcm(samples: ShortArray) {
        synchronized(lock) {
            val rec = recognizer ?: return
            val st = stream ?: return
            // Convert Int16 → Float32 [-1, 1]
            val floats = FloatArray(samples.size) { samples[it] / 32768.0f }
            st.acceptWaveform(floats, 16000)
            while (rec.isReady(st)) {
                rec.decode(st)
            }
            val result = rec.getResult(st)
            val text = result?.text ?: ""
            if (text.isNotBlank()) {
                callback?.invoke(Transcript(text, currentLang, isFinal = true, seq = seq++))
            }
        }
    }

    override fun stopStt() {
        synchronized(lock) {
            stream?.release()
            recognizer?.release()
            tts?.release()
            stream = null
            recognizer = null
            tts = null
            callback = null
        }
    }

    /** Switch to a different language at runtime (downloads model if needed). */
    fun switchLanguage(lang: String, resolved: SherpaModelManager.ResolvedModels) {
        synchronized(lock) {
            stream?.release()
            recognizer?.release()
            tts?.release()
            stream = null
            recognizer = null
            tts = null
            currentLang = resolved.lang
            models = resolved
            try {
                initRecognizer(resolved)
                initTts(resolved)
            } catch (e: Exception) {
                println("Sherpa language switch to '$lang' failed: ${e.message}")
            }
        }
    }

    override fun synthesize(text: String, lang: String): ShortArray {
        synchronized(lock) {
            val engine = tts ?: return ShortArray(0)
            val audio: GeneratedAudio = engine.generate(text, 0, 1.0f)
            // Convert Float32 [-1, 1] → Int16 PCM
            return ShortArray(audio.samples.size) { i ->
                (audio.samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }
}
