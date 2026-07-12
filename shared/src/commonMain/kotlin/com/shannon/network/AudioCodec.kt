package com.shannon.network

/**
 * Supported audio codecs for LXST voice calls.
 * Ordered by preference (highest quality first).
 */
enum class AudioCodec(val bitrate: Int, val description: String) {
    OPUS(64000, "Opus - high quality, higher bandwidth"),
    AMR_WB(23850, "AMR-WB - medium quality"),
    CODEC2_3200(3200, "CODEC2 3200 - low bandwidth"),
    CODEC2_1200(1200, "CODEC2 1200 - very low bandwidth"),
    CODEC2_700C(700, "CODEC2 700C - minimal bandwidth")
}

/** Preference order for codec negotiation. */
private val CODEC_PREFERENCE = listOf(
    AudioCodec.OPUS,
    AudioCodec.AMR_WB,
    AudioCodec.CODEC2_3200,
    AudioCodec.CODEC2_1200,
    AudioCodec.CODEC2_700C
)

/**
 * Negotiate the best audio codec between local and remote capabilities.
 * Returns the highest-quality codec supported by both sides, or null if no overlap.
 */
fun negotiateCodec(
    localCodecs: Set<AudioCodec>,
    remoteCodecs: Set<AudioCodec>
): AudioCodec? {
    val shared = localCodecs.intersect(remoteCodecs)
    return CODEC_PREFERENCE.firstOrNull { it in shared }
}
