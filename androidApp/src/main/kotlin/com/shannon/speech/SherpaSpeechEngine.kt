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
 * Android on-device STT + TTS via Sherpa-ONNX (§2.1). Mirrors the desktop SherpaSpeechEngine but
 * differs in the two places the spec calls out:
 *
 *  - **Native libs** are loaded with [System.loadLibrary] from the APK's `jniLibs`
 *    (`arm64-v8a` / `x86_64`), NOT downloaded at runtime. Android cannot `System.load` an arbitrary
 *    path, nor shell out to `tar` like the desktop loader does.
 *  - **Model cache** is rooted at the app's internal files dir (`Context.getFilesDir()`), passed in
 *    as [cacheDir] by [AndroidSpeechEngineProvider].
 *
 * Graceful degradation: if the native libs aren't packaged or the models aren't present,
 * [isAvailable] stays `false` and the voice-call path is unaffected (per the STT NFRs).
 *
 * Runtime packaging TODOs (do not affect compilation — the compile-time symbols come from the
 * `com.litongjava:sherpa-onnx-java-api` jar cataloged in §6.1):
 *  - `jniLibs`: place `libsherpa-onnx-jni.so` + `libonnxruntime.so` (from the
 *    `sherpa-onnx-v*-android` GitHub release) under `androidApp/src/main/jniLibs/{arm64-v8a,x86_64}/`.
 *  - models: stream them into `getFilesDir()/sherpa/models/` — an Android model downloader (using
 *    `ZipInputStream`, not `tar`) is a §2.3 follow-up; the desktop uses SherpaModelManager.
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
    private var currentLang = DEFAULT_LANG
    private var nativeLoaded = false

    override val isAvailable: Boolean get() = synchronized(lock) { recognizer != null }

    override fun startStt(onTranscript: (Transcript) -> Unit) {
        callback = onTranscript
        seq = 0
        synchronized(lock) {
            try {
                ensureNativeLoaded()
                if (!nativeLoaded) {
                    println("Sherpa: native libs unavailable on Android; STT/TTS disabled")
                    return@synchronized
                }
                val resolved = resolveModels(currentLang) ?: run {
                    println("Sherpa: no STT model for '$currentLang' under ${cacheDir.absolutePath}")
                    return@synchronized
                }
                initRecognizer(resolved)
                initTts(resolved)
            } catch (e: Exception) {
                println("Sherpa init failed: ${e.message}")
            }
        }
    }

    private fun ensureNativeLoaded() {
        if (nativeLoaded) return
        runCatching {
            // Order matters: ONNX Runtime first, then the Sherpa JNI binding that depends on it.
            System.loadLibrary("onnxruntime")
            System.loadLibrary("sherpa-onnx-jni")
        }.onSuccess {
            nativeLoaded = true
            println("Sherpa native libs loaded (Android)")
        }.onFailure { println("Sherpa: native load failed: ${it.message}") }
    }

    private data class ResolvedModels(val sttDir: File, val ttsDir: File?)

    private fun resolveModels(lang: String): ResolvedModels? {
        val modelsDir = File(cacheDir, "models")
        // STT: streaming-zipformer directory (same file layout as desktop: encoder/decoder/joiner + tokens.txt).
        val sttDir = modelsDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.startsWith("sherpa-onnx-streaming-zipformer-$lang") }
            ?.takeIf { File(it, "tokens.txt").exists() } ?: return null
        // TTS (optional): Piper VITS directory containing model.onnx (+ tokens.txt, espeak-ng-data).
        val ttsDir = modelsDir.listFiles()
            ?.firstOrNull { it.isDirectory && it.name.startsWith("piper-") && File(it, "model.onnx").exists() }
        return ResolvedModels(sttDir, ttsDir)
    }

    private fun initRecognizer(resolved: ResolvedModels) {
        val sttDir = resolved.sttDir
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
        println("Sherpa STT initialized for language '$currentLang' (Android)")
    }

    private fun initTts(resolved: ResolvedModels) {
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
        println("Sherpa TTS initialized for language '$currentLang' (Android)")
    }

    override fun feedPcm(samples: ShortArray) {
        synchronized(lock) {
            val rec = recognizer ?: return
            val st = stream ?: return
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

    override fun synthesize(text: String, lang: String): ShortArray {
        synchronized(lock) {
            val engine = tts ?: return ShortArray(0)
            val audio: GeneratedAudio = engine.generate(text, 0, 1.0f)
            return ShortArray(audio.samples.size) { i ->
                (audio.samples[i] * 32767f).toInt().coerceIn(-32768, 32767).toShort()
            }
        }
    }

    companion object {
        const val DEFAULT_LANG = "en"
    }
}
