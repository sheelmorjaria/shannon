package com.shannon.caption

import com.shannon.speech.Transcript
import kotlinx.serialization.Serializable

/**
 * Wire format carried by an [com.shannon.network.LxstPacketType.TRANSCRIPT] packet's payload,
 * encoded as JSON via kotlinx-serialization-json. Only this text payload ever crosses the
 * network — never raw or synthesized audio (see design.md invariant).
 */
@Serializable
data class CaptionPayload(
    val text: String,
    val lang: String,
    val translated: String? = null,
    val speakerId: String? = null,
    val isFinal: Boolean,
    val seq: Long,
) {
    /** Convert to a UI [Caption] once the speaker's identity hash is known. */
    fun toCaption(sourceHash: String): Caption =
        Caption(text, lang, translated, speakerId, isFinal, seq, sourceHash)

    companion object {
        /** Build the wire payload from a locally-produced STT [Transcript]. */
        fun from(transcript: Transcript): CaptionPayload = CaptionPayload(
            text = transcript.text,
            lang = transcript.lang,
            translated = transcript.translated,
            speakerId = transcript.speakerId,
            isFinal = transcript.isFinal,
            seq = transcript.seq,
        )
    }
}
