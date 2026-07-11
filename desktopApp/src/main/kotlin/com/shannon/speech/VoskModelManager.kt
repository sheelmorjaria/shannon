package com.shannon.speech

import java.io.File
import java.net.URI
import java.nio.file.Files
import java.util.zip.ZipInputStream

/**
 * Downloads + extracts a Vosk model on first use (§2.3). The model is cached locally; subsequent
 * runs skip the download. Call [ensureModel] before constructing [VoskSpeechEngine].
 */
object VoskModelManager {
    private const val MODEL_URL = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
    private const val MODEL_DIR_NAME = "vosk-model-small-en-us-0.15"

    /**
     * Ensures the model exists at [cacheDir]. Downloads + extracts on first call.
     * @return the model directory path, or null if the download fails.
     */
    fun ensureModel(cacheDir: File): String? {
        val modelDir = File(cacheDir, MODEL_DIR_NAME)
        if (modelDir.exists() && modelDir.resolve("conf/mfcc.conf").exists()) {
            return modelDir.absolutePath
        }
        return try {
            cacheDir.mkdirs()
            val zipFile = File(cacheDir, "vosk-model.zip")
            println("Downloading Vosk model from $MODEL_URL...")
            URI(MODEL_URL).toURL().openStream().use { input ->
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
            println("Vosk model extracted to ${modelDir.absolutePath}")
            modelDir.absolutePath
        } catch (e: Exception) {
            println("Vosk model download failed: ${e.message}")
            null
        }
    }
}
