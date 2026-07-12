package com.shannon.caption

import com.shannon.speech.Transcript
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class CaptionPayloadSerializationTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun roundTrip_preserves_all_fields() {
        val original = CaptionPayload(
            text = "Hola", lang = "es", translated = "Hello",
            speakerId = "s1", isFinal = true, seq = 42,
        )
        val encoded = json.encodeToString(CaptionPayload.serializer(), original)
        val decoded = json.decodeFromString(CaptionPayload.serializer(), encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun from_transcript_maps_fields() {
        val t = Transcript("Hi", "en", translated = null, speakerId = null, isFinal = false, seq = 7)
        val p = CaptionPayload.from(t)
        assertEquals("Hi", p.text)
        assertEquals("en", p.lang)
        assertEquals(false, p.isFinal)
        assertEquals(7, p.seq)
    }

    @Test
    fun toCaption_carries_source_hash() {
        val p = CaptionPayload("yo", "en", isFinal = true, seq = 1)
        val c = p.toCaption("ABCDEF")
        assertEquals("ABCDEF", c.sourceHash)
        assertEquals("yo", c.text)
    }
}
