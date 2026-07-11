package com.shannon.audio

/**
 * Desktop no-op [AudioRecorder]. The real microphone capture arrives with the media bridge (§5);
 * for now this keeps the voice-call DI graph resolvable so the bridge can start.
 */
class StubAudioRecorder : AudioRecorder {
    override fun hasPermission(): Boolean = true
    override fun startRecording(onBuffer: (ByteArray) -> Unit) { /* no-op: no capture yet */ }
    override fun stopRecording() { /* no-op */ }
}
