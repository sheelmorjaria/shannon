# Design: Multi-Language STT/TTS (On-Device, Serverless)

## Context
Shannon is a **Kotlin Multiplatform** app (Android + Desktop) built on the
**Reticulum** decentralized network. It is serverless: messaging uses **LXMF**
packets and voice calls use **LXST** signaling packets, all E2EE and P2P. There is
no SFU and no media server.

The voice path already exists in `shared/commonMain`:
- Uplink:  `Mic -> AudioRecorder.onBuffer (raw PCM) -> AudioEngine.onMicBuffer -> LxstPacket(AUDIO) -> ReticulumClient`
- Downlink:`ReticulumClient -> LxstPacket(AUDIO) -> AudioEngine.onAudioPacketReceived -> AudioPlayer.playBuffer -> Speaker`

Audio codecs (`AudioCodec`: Opus / AMR-WB / CODEC2*) are negotiated for
low-bandwidth links and exist only for transmission; STT operates on the raw PCM
the recorder already produces.

This design adds STT/TTS **without adding a server**. The earlier concept (Rust
SFU tapping all audio, routing to Python gRPC services, Docker deployment) is
rejected: none of that infrastructure exists in Shannon, and it contradicts the
serverless/E2EE model.

## Goals
- Live captions available on both Android and Desktop, fully offline-capable.
- Raw audio never leaves the device; only transcript/translation text is sent.
- STT/TTS execution never blocks the real-time audio capture/playback path.
- Graceful degradation on low-power devices (smaller model, or captions disabled).

## Decisions

### D1. Where inference runs: on each endpoint, not in the network
Each device transcribes **its own microphone** locally and sends the resulting
**text** to the peer. The peer renders captions and (optionally) synthesizes TTS
locally. Rationale:
- Matches Shannon's no-server / E2EE model and its privacy story.
- Only cheap text traverses low-bandwidth links — not a second audio stream and not
  synthesized audio.
- The transcript is authoritative (derived from clean local PCM, not from a codec
  round-trip over a lossy link).

Fallback (optional, not default): a device *may* also run STT on received downlink
PCM (tap `AudioEngine.onAudioPacketReceived`) to caption peers whose devices cannot
run STT. This is opt-in; it costs listener compute and decode.

### D2. ML engine: Sherpa-ONNX
Use **Sherpa-ONNX** (`com.k2fsa.sherpa-onnx`) as the single on-device engine:
- STT: Whisper / Zipformer / Paraformer models (90+ languages via Whisper).
- VAD: Silero VAD (bundled).
- TTS: VITS / Piper / Matcha.
- Speaker embeddings for optional diarization.
- Cross-platform: Android AAR + JVM JAR → fits `androidMain` / `desktopMain`.

Expose a platform-agnostic interface in `commonMain`:

```kotlin
interface SpeechEngine {
    fun startStt(onTranscript: (Transcript) -> Unit)
    fun feedPcm(samples: ShortArray)          // from AudioRecorder.onBuffer
    fun stopStt()
    fun synthesize(text: String, lang: String): ShortArray   // -> AudioPlayer.playBuffer
    val isAvailable: Boolean                   // false if no model / insufficient compute
}
```

`expect fun createSpeechEngine(...): SpeechEngine` is provided per source set;
`actual` implementations wrap Sherpa-ONNX. This mirrors Shannon's existing
`AudioRecorder` / `AudioPlayer` interface pattern.

Fallback engines (constrained devices): Vosk (STT) or a smaller Zipformer model;
Piper-native for TTS. The interface keeps these swappable.

### D3. Captions ride the existing signaling layer as text
- **Live in-call captions:** add `LxstPacketType.TRANSCRIPT`. The `LxstPacket`
  `payload` carries a small JSON blob (via the existing `kotlinx-serialization-json`):
  `{ text, translated?, lang, speakerId?, final, seq }`. Sent through the same
  `ReticulumClient.sendLxstPacket` / `observeIncomingLxstPackets` flow as AUDIO.
- **Async (voice message) transcripts:** reuse `LxmfPacket.content` so a recorded
  voice message can arrive with its transcript attached as text.

Text payloads are tiny relative to audio, so this adds negligible load on
Reticulum links — the opposite of streaming a second audio or gRPC channel.

### D4. TTS plays locally on the listener
Synthesized PCM is injected via `AudioPlayer.playBuffer` (the same downlink exit
point as received speech), so TTS reuses the existing playback device and volume
path. Only the triggering text crosses the network.

### D5. Echo handling is client-only (no WebRTC)
The original spec's "WebRTC echo cancellation" does not apply (no WebRTC). The
echo risk is now purely local: a listener's TTS playback being picked up by the
listener's own mic and re-transcribed. Mitigations, on-device:
- Apply platform AEC in the recorder chain (Android `AcousticEchoCanceler`; desktop
  echo cancellation in the audio capture path).
- Optionally suppress local STT capture briefly while TTS is playing on this device
  (duck/mute the STT feed, not the network).

## Risks & Mitigations
- **STT latency / compute on low-end devices** — Whisper is heavy.
  *Mitigation:* offer model quality tiers (tiny/base/zipformer), download-on-demand,
  profile per device, and disable captions if `isAvailable == false`. Targets are
  stated per device class, not as a single optimistic number (see NFRs).
- **Battery / thermal (always-on STT during a call)** —
  *Mitigation:* VAD-gate STT so inference runs only on speech segments; run all ML
  on background coroutines/threads, never on the audio callback thread.
- **Caption delivery bounded by link, not compute** — Reticulum links can be high
  latency (radio/BLE). *Mitigation:* state compute-latency and delivery-latency
  separately in the NFRs; stream partial (`final=false`) captions for early render.
- **Model size / storage** — models are 75–500 MB. *Mitigation:* on-demand download,
  per-language packs, user-selectable, never bundled into the APK.
- **Echo feedback** (listener TTS → listener mic) — see D5.
- **Partial-translation quality** — on-device MT is weaker than cloud.
  *Mitigation:* default to Whisper's native `translate` (to English); make
  translation opt-in and clearly labeled.

## Architecture Diagram (On-Device, Serverless)

```
  SPEAKER'S DEVICE (Android / Desktop)            LISTENER'S DEVICE (Android / Desktop)
  ---------------------------------------         ---------------------------------------
  Mic                                             Speaker
   |  AudioRecorder.onBuffer (PCM)                 ^  AudioPlayer.playBuffer (PCM)
   v                                                |
  VAD (Silero) --utterance--> STT (Whisper)      TTS (VITS/Piper)   [listener-side]
   |   (background thread)         | text           ^  (background thread)
   | (optional) translate          v                | synthesized PCM
   v                            build TRANSCRIPT     |
  local CaptionsOverlay            pkt (JSON)        |
                                     |               |
                                     v               |
                       ReticulumClient.sendLxstPacket|
                                     |               v
                            [ ===== Reticulum (E2EE, P2P) ===== ]
                                     |
                       observeIncomingLxstPackets (TRANSCRIPT)
                                     |
                                     v
                              parse JSON -> CaptionsOverlay (renders text)
```

Key invariant: **the vertical Reticulum arrow carries only text.** No audio and no
synthesized speech cross the network.

## Latency Targets (realistic, on-device)
These replace the earlier single "500ms" / "1s" figures, which assumed a server:

| Stage | Target |
|---|---|
| Local STT: utterance end (VAD) -> transcript ready | < 1.5 s desktop / < 2.5 s mid-range mobile (model-tier dependent) |
| Partial (interim) caption emitted | < 800 ms from speech segment start |
| TTS synthesis for a short utterance | < 500 ms on-device |
| Caption text delivery to peer | bounded by the Reticulum link (not compute) |

Exact numbers will be confirmed by per-device profiling during implementation.

## What Changed vs. the Original SFU Proposal
| Original (rejected) | Rewritten (this design) |
|---|---|
| Rust WebRTC SFU taps all audio | No SFU; each device taps its own mic via `AudioRecorder` |
| Python `faster-whisper` / Piper / XTTS over gRPC | On-device Sherpa-ONNX (STT+VAD+TTS) in Kotlin |
| Docker/K8s Python services | None; inference is in-app, offline |
| Opus -> PCM 16kHz conversion in Rust SFU | Raw PCM already available from `AudioRecorder.onBuffer` |
| Server-side audio suppression for echo | Client-side AEC + optional local STT mute |
| WebSocket signaling | Existing `ReticulumClient` LXST/LXMF; new `TRANSCRIPT` packet type |
