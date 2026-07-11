package com.shannon.speech

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Model URLs for a language (STT + TTS + VAD). */
data class SherpaModelSet(
    val lang: String,
    val name: String,
    val sttUrl: String,
    val sttDirName: String,
    val ttsUrl: String?,
    val ttsDirName: String?,
)

/**
 * Downloads + caches Sherpa-ONNX models per language (§2.3). STT uses streaming Zipformer;
 * TTS uses Piper VITS; VAD uses Silero. Models are cached at ~/.shannon/sherpa/models/.
 */
object SherpaModelManager {
    private const val VAD_URL = "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/silero_vad.onnx"
    private const val VAD_FILE = "silero_vad.onnx"

    val CATALOG: Map<String, SherpaModelSet> = linkedMapOf(
        "en" to SherpaModelSet("en", "English",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-en-2023-06-26.tar.bz2",
            "sherpa-onnx-streaming-zipformer-en-2023-06-26",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/en/en_US/lessac/medium/en_US-lessac-medium.onnx",
            "piper-en_US-lessac-medium",
        ),
        "zh" to SherpaModelSet("zh", "Chinese",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23.tar.bz2",
            "sherpa-onnx-streaming-zipformer-zh-14M-2023-02-23",
            null, null,
        ),
        "de" to SherpaModelSet("de", "German",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-de-2024-03-06.tar.bz2",
            "sherpa-onnx-streaming-zipformer-de-2024-03-06",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/de/de_DE/thorsten/medium/de_DE-thorsten-medium.onnx",
            "piper-de_DE-thorsten-medium",
        ),
        "es" to SherpaModelSet("es", "Spanish",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-es-2024-04-01.tar.bz2",
            "sherpa-onnx-streaming-zipformer-es-2024-04-01",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/es/es_MX/clara/medium/es_MX-clara-medium.onnx",
            "piper-es_MX-clara-medium",
        ),
        "fr" to SherpaModelSet("fr", "French",
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-streaming-zipformer-fr-2024-04-01.tar.bz2",
            "sherpa-onnx-streaming-zipformer-fr-2024-04-01",
            "https://huggingface.co/rhasspy/piper-voices/resolve/main/fr/fr_FR/siwis/medium/fr_FR-siwis-medium.onnx",
            "piper-fr_FR-siwis-medium",
        ),
    )

    const val DEFAULT_LANG = "en"

    fun supportedLanguages() = CATALOG.keys.toList()

    data class ResolvedModels(val lang: String, val sttDir: String, val ttsDir: String?, val vadFile: String)

    fun ensureModels(lang: String, cacheDir: File): ResolvedModels? {
        val info = CATALOG[lang] ?: CATALOG[DEFAULT_LANG] ?: return null
        val modelsDir = File(cacheDir, "models")
        modelsDir.mkdirs()

        // STT model
        val sttDir = File(modelsDir, info.sttDirName)
        if (!sttDir.exists() || sttDir.listFiles()?.isNotEmpty() != true) {
            downloadAndExtract(info.sttUrl, sttDir, modelsDir, isZip = false, expectedDir = info.sttDirName)
        }
        if (!sttDir.exists()) return null

        // VAD model
        val vadFile = File(modelsDir, VAD_FILE)
        if (!vadFile.exists()) {
            runCatching {
                println("Downloading Silero VAD model…")
                URI(VAD_URL).toURL().openStream().use { Files.copy(it, vadFile.toPath()) }
            }.onFailure { println("VAD download failed: ${it.message}") }
        }

        // TTS model (optional)
        var ttsDir: String? = null
        if (info.ttsUrl != null && info.ttsDirName != null) {
            val ttsModelDir = File(modelsDir, info.ttsDirName)
            if (!ttsModelDir.exists()) {
                ttsModelDir.mkdirs()
                val ttsFile = File(ttsModelDir, "model.onnx")
                runCatching {
                    println("Downloading Piper TTS model for ${info.name}…")
                    URI(info.ttsUrl).toURL().openStream().use { Files.copy(it, ttsFile.toPath()) }
                }.onFailure { println("TTS download failed: ${it.message}") }
            }
            if (ttsModelDir.resolve("model.onnx").exists()) ttsDir = ttsModelDir.absolutePath
        }

        return ResolvedModels(info.lang, sttDir.absolutePath, ttsDir, vadFile.absolutePath)
    }

    private fun downloadAndExtract(url: String, targetDir: File, cacheDir: File, isZip: Boolean, expectedDir: String) {
        try {
            val archiveName = url.substringAfterLast("/")
            val archiveFile = File(cacheDir, archiveName)
            println("Downloading $archiveName…")
            URI(url).toURL().openStream().use { Files.copy(it, archiveFile.toPath()) }
            // Extract tar.bz2 via system tar
            val process = ProcessBuilder("tar", "xjf", archiveFile.absolutePath, "-C", cacheDir.absolutePath)
                .redirectErrorStream(true).start()
            process.inputStream.bufferedReader().readText()
            process.waitFor()
            archiveFile.delete()
            // The extracted dir name might differ slightly; find it
            val extracted = cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith(expectedDir.substring(0, 20)) }
            extracted?.firstOrNull()?.let { dir ->
                if (dir != targetDir) dir.copyRecursively(targetDir, overwrite = true)
            }
        } catch (e: Exception) {
            println("Download/extract failed for $url: ${e.message}")
        }
    }
}
