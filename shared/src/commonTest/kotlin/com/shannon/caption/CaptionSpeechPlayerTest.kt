package com.shannon.caption

import com.shannon.audio.FakeAudioPlayer
import com.shannon.speech.FakeSpeechEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CaptionSpeechPlayerTest {

    @Test
    fun `speak synthesizes and plays the resulting PCM`() = runTest {
        val engine = FakeSpeechEngine(isAvailable = true, synthesized = shortArrayOf(0x01, 0x02, 0x03, 0x04))
        val player = FakeAudioPlayer().also { it.startPlaying() }

        CaptionSpeechPlayer(engine, player).speak("Hello", "en")

        assertEquals(1, engine.synthesizedTexts.size)
        assertEquals("en", engine.synthesizedTexts[0].second)
        assertEquals(1, player.playedBuffers.size)
        assertEquals(8, player.playedBuffers[0].size) // 4 shorts -> 8 bytes
    }

    @Test
    fun `speakCaption prefers the translated text`() = runTest {
        val engine = FakeSpeechEngine(isAvailable = true)
        val player = FakeAudioPlayer().also { it.startPlaying() }

        CaptionSpeechPlayer(engine, player).speakCaption(
            Caption("Hola", "es", translated = "Hello", isFinal = true, seq = 1, sourceHash = "X")
        )

        assertEquals("Hello", engine.synthesizedTexts[0].first)
    }

    @Test
    fun `speak is a no-op when engine unavailable`() = runTest {
        val engine = FakeSpeechEngine(isAvailable = false)
        val player = FakeAudioPlayer().also { it.startPlaying() }

        CaptionSpeechPlayer(engine, player).speak("Hi", "en")

        assertTrue(engine.synthesizedTexts.isEmpty())
        assertTrue(player.playedBuffers.isEmpty())
    }
}
