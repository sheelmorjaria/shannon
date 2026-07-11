package com.shannon.speech

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipInputStream

/** Metadata for a downloadable Vosk model. */
data class VoskModelInfo(
    val lang: String,       // BCP-47-ish code: "en", "es", "fr"
    val name: String,       // Display name: "English", "Spanish"
    val url: String,        // Download URL (alphacephei.com)
    val dirName: String,    // Extracted directory name
    val sizeMb: Int,        // Approximate size (for UI display)
)

/**
 * Per-language Vosk model catalog + download manager (§2.3). Downloads + extracts small models
 * (~40 MB each) from alphacephei.com on first use, cached locally. Call [ensureModel] before
 * constructing or switching a [VoskSpeechEngine].
 */
object VoskModelManager {
    const val DEFAULT_LANG = "en"

    val CATALOG: Map<String, VoskModelInfo> = linkedMapOf(
        "en" to VoskModelInfo("en", "English",    "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",  "vosk-model-small-en-us-0.15", 40),
        "es" to VoskModelInfo("es", "Spanish",    "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip",     "vosk-model-small-es-0.42",   39),
        "fr" to VoskModelInfo("fr", "French",     "https://alphacephei.com/vosk/models/vosk-model-small-fr-0.22.zip",     "vosk-model-small-fr-0.22",   40),
        "de" to VoskModelInfo("de", "German",     "https://alphacephei.com/vosk/models/vosk-model-small-de-0.15.zip",     "vosk-model-small-de-0.15",   41),
        "zh" to VoskModelInfo("zh", "Chinese",    "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip",     "vosk-model-small-cn-0.22",   42),
        "ja" to VoskModelInfo("ja", "Japanese",   "https://alphacephei.com/vosk/models/vosk-model-small-ja-0.22.zip",     "vosk-model-small-ja-0.22",   49),
        "pt" to VoskModelInfo("pt", "Portuguese", "https://alphacephei.com/vosk/models/vosk-model-small-pt-0.3.zip",      "vosk-model-small-pt-0.3",    38),
        "fa" to VoskModelInfo("fa", "Persian",    "https://alphacephei.com/vosk/models/vosk-model-small-fa-0.4.zip",      "vosk-model-small-fa-0.4",    41),
    )

    /** Languages available in the catalog. */
    val supportedLanguages: List<String> get() = CATALOG.keys.toList()

    /** Languages whose model is already cached locally. */
    fun cachedLanguages(cacheDir: File): List<String> =
        CATALOG.keys.filter { lang -> isCached(lang, cacheDir) }

    /** Check if a language's model is already downloaded. */
    fun isCached(lang: String, cacheDir: File): Boolean {
        val info = CATALOG[lang] ?: return false
        return File(cacheDir, info.dirName).resolve("conf/mfcc.conf").exists()
    }

    /**
     * Ensures the model for [lang] exists at [cacheDir]. Downloads + extracts on first call.
     * @return the model directory path, or null if the language is unknown or download fails.
     */
    fun ensureModel(lang: String, cacheDir: File): String? {
        val info = CATALOG[lang] ?: run {
            println("Vosk: unknown language '$lang', falling back to $DEFAULT_LANG")
            return ensureModel(DEFAULT_LANG, cacheDir)
        }
        val modelDir = File(cacheDir, info.dirName)
        if (modelDir.resolve("conf/mfcc.conf").exists()) return modelDir.absolutePath
        return try {
            cacheDir.mkdirs()
            val zipFile = File(cacheDir, "${info.dirName}.zip")
            println("Downloading Vosk ${info.name} model (${info.sizeMb} MB) from ${info.url}…")
            URI(info.url).toURL().openStream().use { input ->
                Files.copy(input, zipFile.toPath())
            }
            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val target = File(cacheDir, entry.name)
                    if (entry.isDirectory) target.mkdirs()
                    else {
                        target.parentFile?.mkdirs()
                        Files.copy(zis, target.toPath())
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            zipFile.delete()
            println("Vosk ${info.name} model ready at ${modelDir.absolutePath}")
            modelDir.absolutePath
        } catch (e: Exception) {
            println("Vosk ${info.name} model download failed: ${e.message}")
            null
        }
    }
}
