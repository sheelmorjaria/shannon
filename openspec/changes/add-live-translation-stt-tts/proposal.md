# Proposal: Add Multi-Language STT/TTS (On-Device, Serverless)

## Why
Shannon users communicate across language barriers over a decentralized network.
Today there is no way to read what a peer is saying (live captions) or to hear a
spoken translation. We need Speech-to-Text (STT) to caption spoken audio and
Text-to-Speech (TTS) to synthesize translated speech.

Because Shannon is **serverless and E2EE** (no SFU, no media server), this must be
done **on-device**: each device transcribes its own microphone and sends only the
resulting text. This is also the most privacy-preserving option — raw audio never
leaves either endpoint — and it fits Shannon's low-bandwidth transports, since
text is far cheaper to transmit than a second audio stream.

## What Changes
1. **On-device ML engine (`shared`):** Introduce a platform-agnostic
   `SpeechEngine` interface in `commonMain` (STT + VAD + TTS) backed by
   **Sherpa-ONNX** (`com.k2fsa.sherpa-onnx`) in `androidMain` and `desktopMain`.
   Sherpa-ONNX ships an Android AAR and a JVM JAR and covers STT (Whisper /
   Zipformer), Silero VAD, and TTS (VITS / Piper / Matcha) in one dependency.
2. **Tap the existing audio pipeline:** Feed the local microphone PCM — already
   produced by `AudioRecorder.onBuffer` / `AudioEngine.onMicBuffer` — into the
   STT engine, segmented by VAD into utterances. (For received audio, the listener
   normally relies on the speaker's transcript rather than decoding the downlink.)
3. **Transport captions as text over Reticulum:** Add an `LxstPacketType.TRANSCRIPT`
   for live in-call captions and reuse `LxmfPacket.content` for asynchronous
   (voice-message) transcripts. The payload is small JSON-encoded text
   (transcript + optional translation + language), serialized with the already
   present `kotlinx-serialization-json`.
4. **Listener-side TTS:** On the receiving device, optionally synthesize the
   received (translated) transcript to PCM and play it via
   `AudioEngine` / `AudioPlayer.playBuffer` — so only text crosses the network,
   never synthesized audio.
5. **Client UI updates:** Add a live-captions overlay to the call surface, source
   / target language pickers, and toggles for captions and "speak translations".

## Capabilities
- **Live captions:** real-time transcription of each peer's own speech, displayed
  on the other peer's device.
- **Auto-detect language:** the on-device model (Whisper-family) detects the
  spoken language when the user does not pin one.
- **Optional translation:** translate the transcript (Whisper `task="translate"` to
  English, or an on-device MT model) before display / synthesis.
- **Spoken translation (optional):** listener-side TTS of the translated text.
- **Speaker labeling:** optional speaker embeddings to attribute utterances.

## Impact
- **`shared` module:** new `SpeechEngine` interface + transcription/TTS domain
  models in `commonMain`; Sherpa-ONNX `actual` implementations and model loading
  in `androidMain` / `desktopMain`; new `TRANSCRIPT` LXST packet type; caption /
  translation state in the relevant ViewModel.
- **Build / DI:** add `sherpa-onnx` to `gradle/libs.versions.toml` per source set;
  add a Koin `captionModule` in `AppModule.kt`; model assets are downloaded /
  selected on demand (not bundled).
- **UI:** new captions overlay component and controls on the call surface.
- **Cost / latency:** on-device STT adds compute (and battery) cost locally, and
  caption delivery is bounded by the Reticulum link. Latency targets are therefore
  set per-device-class and stated separately from network delivery (see design).
- **Privacy (improvement):** raw audio stays on-device; only text leaves, over E2EE.

## Non-Goals
- Introducing any server, SFU, relay, or cloud ML API. (This explicitly rejects the
  earlier Rust-SFU + Python-gRPC concept, which does not match Shannon.)
- Transmitting raw or synthesized audio for transcription.
- Video (Shannon's current media is voice + text; captions apply to voice).
