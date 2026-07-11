package com.shannon.audio

/**
 * Desktop no-op [AudioPlayer]. The real speaker playback arrives with the media bridge (§5);
 * for now this keeps the voice-call DI graph resolvable so the bridge can start.
 */
class StubAudioPlayer : AudioPlayer {
    override fun startPlaying() { /* no-op */ }
    override fun stopPlaying() { /* no-op */ }
    override fun playBuffer(data: ByteArray) { /* no-op */ }
}
