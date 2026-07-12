package com.shannon.audio

/**
 * Conversion helpers for 16-bit little-endian PCM, the sample format used by the audio
 * pipeline ([AudioRecorder] produces [ByteArray]; [SpeechEngine] consumes [ShortArray]).
 */
object Pcm {
    /** Decode little-endian 16-bit PCM bytes to signed shorts (2 bytes per sample). */
    fun toShortArray(bytes: ByteArray): ShortArray {
        val shorts = ShortArray(bytes.size / 2)
        for (i in shorts.indices) {
            val lo = bytes[2 * i].toInt() and 0xFF
            val hi = bytes[2 * i + 1].toInt()
            shorts[i] = ((hi shl 8) or lo).toShort()
        }
        return shorts
    }

    /** Encode signed shorts to little-endian 16-bit PCM bytes (2 bytes per sample). */
    fun toByteArray(shorts: ShortArray): ByteArray {
        val bytes = ByteArray(shorts.size * 2)
        for (i in shorts.indices) {
            val v = shorts[i].toInt()
            bytes[2 * i] = (v and 0xFF).toByte()
            bytes[2 * i + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return bytes
    }
}
