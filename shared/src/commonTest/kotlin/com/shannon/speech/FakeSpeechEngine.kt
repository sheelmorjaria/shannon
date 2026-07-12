package com.shannon.speech

/**
 * Test [SpeechEngine]: records fed PCM samples and the synthesize() calls, and returns a
 * configurable synthesis result.
 */
class FakeSpeechEngine(
    override val isAvailable: Boolean = true,
    private val synthesized: ShortArray = shortArrayOf(1, 2, 3, 4),
) : SpeechEngine {
    val fedSamples = mutableListOf<Short>()
    val synthesizedTexts = mutableListOf<Pair<String, String>>()

    override fun startStt(onTranscript: (Transcript) -> Unit) {}
    override fun feedPcm(samples: ShortArray) {
        fedSamples.addAll(samples.toList())
    }
    override fun stopStt() {}
    override fun synthesize(text: String, lang: String): ShortArray {
        synthesizedTexts.add(text to lang)
        return synthesized
    }
}
