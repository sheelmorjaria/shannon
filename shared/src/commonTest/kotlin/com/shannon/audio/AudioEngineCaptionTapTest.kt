package com.shannon.audio

import com.shannon.speech.FakeSpeechEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEngineCaptionTapTest {

    @Test
    fun `mic PCM is fed to the speech engine when available`() = runTest {
        val recorder = FakeAudioRecorder()
        val speech = FakeSpeechEngine(isAvailable = true)
        val engine = AudioEngine(recorder, FakeAudioPlayer(), speechEngine = speech)

        engine.startRecording()
        recorder.simulateAudioBuffer(ByteArray(160) { it.toByte() }) // 160 bytes -> 80 samples

        assertEquals(80, speech.fedSamples.size)
    }

    @Test
    fun `mic PCM is not fed when engine unavailable`() = runTest {
        val recorder = FakeAudioRecorder()
        val speech = FakeSpeechEngine(isAvailable = false)
        val engine = AudioEngine(recorder, FakeAudioPlayer(), speechEngine = speech)

        engine.startRecording()
        recorder.simulateAudioBuffer(ByteArray(160))

        assertTrue(speech.fedSamples.isEmpty())
    }
}
