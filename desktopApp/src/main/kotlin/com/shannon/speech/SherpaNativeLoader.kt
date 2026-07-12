package com.shannon.speech

import java.io.File
import java.net.URI
import java.nio.file.Files

/**
 * Downloads + loads the Sherpa-ONNX native JNI library at runtime (§2.2).
 * The Java API is on Maven Central ([com.litongjava:sherpa-onnx-java-api]) but the native
 * `.so`/`.dll`/`.dylib` must be downloaded from GitHub Releases.
 *
 * Call [ensureLoaded] once before instantiating any `com.k2fsa.sherpa.onnx.*` class.
 */
object SherpaNativeLoader {
    private const val SHERPA_VERSION = "1.10.36"
    private var loaded = false

    fun ensureLoaded(cacheDir: File = File(System.getProperty("user.home"), ".shannon/sherpa")) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val libDir = File(cacheDir, "lib")
            val (jniLib, ortLib) = detectExpectedLibs(libDir)
            if (!jniLib.exists()) {
                downloadAndExtract(cacheDir, libDir)
            }
            // Load ONNX Runtime first (sherpa-jni depends on it).
            if (ortLib.exists()) {
                runCatching { System.load(ortLib.absolutePath) }
                    .onFailure { println("Sherpa: failed to load ONNX Runtime: ${it.message}") }
            }
            if (jniLib.exists()) {
                System.load(jniLib.absolutePath)
                println("Sherpa native lib loaded from ${jniLib.absolutePath}")
                loaded = true
            } else {
                println("Sherpa: native lib not found at ${jniLib.absolutePath} — STT/TTS will be unavailable")
            }
        }
    }

    private fun detectExpectedLibs(libDir: File): Pair<File, File> {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("linux") -> File(libDir, "libsherpa-onnx-jni.so") to File(libDir, "libonnxruntime.so")
            os.contains("windows") -> File(libDir, "sherpa-onnx-jni.dll") to File(libDir, "onnxruntime.dll")
            os.contains("mac") -> File(libDir, "libsherpa-onnx-jni.dylib") to File(libDir, "libonnxruntime.dylib")
            else -> File(libDir, "libsherpa-onnx-jni.so") to File(libDir, "libonnxruntime.so")
        }
    }

    private fun downloadAndExtract(cacheDir: File, libDir: File) {
        val (url, archiveName) = platformArchive()
        val archiveFile = File(cacheDir, archiveName)
        try {
            cacheDir.mkdirs()
            println("Downloading Sherpa-ONNX native package ($archiveName, ~50 MB)…")
            URI(url).toURL().openStream().use { Files.copy(it, archiveFile.toPath()) }
            libDir.mkdirs()
            // Extract using tar (Linux/macOS) — the archive is .tar.bz2
            val process = ProcessBuilder("tar", "xjf", archiveFile.absolutePath, "-C", cacheDir.absolutePath)
                .redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            if (process.exitValue() != 0) {
                println("Sherpa: tar extraction failed: $output")
                return
            }
            // Move libs from sherpa-onnx-*/lib/ to our lib dir
            val extractedDirs = cacheDir.listFiles { f -> f.isDirectory && f.name.startsWith("sherpa-onnx") }
            extractedDirs?.firstOrNull()?.let { dir ->
                val srcLib = File(dir, "lib")
                if (srcLib.exists()) srcLib.listFiles()?.forEach { f ->
                    f.copyTo(File(libDir, f.name), overwrite = true)
                }
            }
            archiveFile.delete()
            println("Sherpa native libs extracted to ${libDir.absolutePath}")
        } catch (e: Exception) {
            println("Sherpa native download failed: ${e.message}")
        }
    }

    private fun platformArchive(): Pair<String, String> {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val v = SHERPA_VERSION
        return when {
            os.contains("linux") && arch.contains("aarch64") ->
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$v/sherpa-onnx-v$v-linux-aarch64.tar.bz2" to "sherpa-onnx-linux-aarch64.tar.bz2"
            os.contains("linux") ->
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$v/sherpa-onnx-v$v-linux-x64.tar.bz2" to "sherpa-onnx-linux-x64.tar.bz2"
            os.contains("mac") ->
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$v/sherpa-onnx-v$v-osx-universal.tar.bz2" to "sherpa-onnx-osx.tar.bz2"
            os.contains("windows") ->
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$v/sherpa-onnx-v$v-win64.tar.bz2" to "sherpa-onnx-win64.tar.bz2"
            else ->
                "https://github.com/k2-fsa/sherpa-onnx/releases/download/v$v/sherpa-onnx-v$v-linux-x64.tar.bz2" to "sherpa-onnx-linux-x64.tar.bz2"
        }
    }
}
