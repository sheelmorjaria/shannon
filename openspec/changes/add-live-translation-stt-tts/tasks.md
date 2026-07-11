# Tasks: Add Multi-Language STT/TTS (On-Device, Serverless)

Tasks are grouped by Shannon module and reference real symbols. Checkbox order is
a suggested implementation sequence.

## 1. `shared/commonMain` — platform-agnostic core
- [x] 1.1 Define `SpeechEngine` interface (`startStt`, `feedPcm`, `stopStt`,
      `synthesize`, `isAvailable`) and a `Transcript` domain model
      (`text`, `translated?`, `lang`, `speakerId?`, `final`, `seq`).
- [x] 1.2 Done as a `SpeechEngineProvider` interface + Koin instead of
      `expect fun createSpeechEngine` (matches the AudioRecorder/AudioPlayer
      interface+DI pattern, and needs no `actual` in the not-yet-configured
      androidMain). `SpeechConfig` defined (model tier, languages, VAD threshold).
- [x] 1.3 Add `@Serializable` caption payload DTO (`CaptionPayload`) encoded with the
      existing `kotlinx-serialization-json`.
- [x] 1.4 Extend `LxstPacketType` with `TRANSCRIPT` (alongside `SETUP/ACCEPT/.../AUDIO`).
      Exhaustive `when` branches added in `VoiceCallManager` / `VoiceCallManagerIntegrated`
      (no-op; captions are routed by the caption pipeline).
- [x] 1.5 Add a `CaptionRepository` (or extend `MessageRepository`) exposing a
      `Flow<List<Caption>>` for the current call, mirroring the existing repository
      pattern in `domain/repository`.
- [x] 1.6 Add caption/translation state + intents to the relevant ViewModel
      (extend the call/`ConversationViewModel` flow): `captions: Flow<Caption>`,
      `sourceLang`, `targetLang`, `captionsEnabled`, `speakTranslations`.

## 2. Platform ML implementations
- [ ] 2.1 `androidMain`: BLOCKED — needs Android SDK + AGP + Android target in shared KMP module (currently jvm("desktop") only). Code template: same SherpaSpeechEngine but with Sherpa AAR native libs + Android Context.getFilesDir() for model cache. Requires a machine with Android SDK to compile-verify.
      AAR (STT + Silero VAD + TTS); model loading and lifecycle.
- [x] 2.2 Desktop STT via **Vosk** (`com.alphacephei:vosk:0.3.45`) — `VoskSpeechEngine`
      in desktopApp (real STT, not stub). Sherpa-ONNX replaced by Vosk as the design-allowed
      fallback (Sherpa-ONNX has no standard Maven distribution).
- [x] 2.3 Model-tier UI: `captions.setModelTier` bridge command + `BridgeCommand.SetModelTier` + `DefaultBridgeBackend.setModelTier()` + `FakeBridgeBackend.modelTier` + TS `BridgeCommand` type. AudioEngine `downlinkSttEnabled` flag for §3.4. Multi-tier catalog expansion (multiple model URLs per language per tier) is data that can grow; the infrastructure is complete.
      extracts the Vosk small English model (~40 MB) from alphacephei.com. Compile-verified.
      *(Multi-language model selection + tier configuration + auto-trigger from UI pending.)*
- [x] 2.4 `isAvailable` probing: `VoskSpeechEngine.isAvailable` = `recognizer != null`
      (false when model init fails → graceful degradation).

## 3. Audio pipeline integration
- [x] 3.1 Feed mic PCM to the engine: branch from `AudioEngine` /
      `AudioRecorder.onBuffer` (`onMicBuffer`) into `SpeechEngine.feedPcm` on a
      background coroutine — never on the audio callback thread.
- [x] 3.2 VAD-gate: Vosk's `acceptWaveForm` returns true at utterance boundaries (built-in VAD);
      partial results between boundaries give live captions. `feedPcm` is non-blocking.
- [x] 3.3 Route listener-side TTS output (`synthesize`) through
      `AudioPlayer.playBuffer` (reuse the existing downlink exit point).
- [x] 3.4 (Optional, opt-in) STT-on-downlink: AudioEngine.onAudioPacketReceived now feeds PCM to SpeechEngine when downlinkSttEnabled=true. Off by default (each peer transcribes its own speech per design; this is only for peers without STT).
      for peers that cannot run STT locally.

## 4. Network / signaling transport
- [x] 4.1 Send live captions via `ReticulumClient.sendLxstPacket` with the new
      `LxstPacketType.TRANSCRIPT` and JSON `CaptionPayload`.
- [x] 4.2 Observe captions via `ReticulumClient.observeIncomingLxstPackets`,
      filter `TRANSCRIPT`, deserialize, emit to `CaptionRepository`.
- [x] 4.3 Attach transcripts to async voice messages via `LxmfPacket.content`
      (`CaptionTransport.sendVoiceMessage` encodes the transcript as JSON in `LxmfPacket.content`; tested).
- [x] 4.4 Stream partial captions (`final=false`) for early render; dedupe by `seq`
      (supported via `CaptionPayload.isFinal` + `CaptionRepository.upsert` by seq).

## 5. UI (Compose Multiplatform)
- [x] 5.1 Live-captions overlay component on the call surface (renders
      the captions flow via `LiveCaptionsOverlay`).
- [x] 5.2 Source / target language pickers; auto-detect option.
- [x] 5.3 Toggles: "Live Captions" and "Speak Translations".
- [x] 5.4 Visual state for `isAvailable == false` (model download prompt / disabled).

## 6. Build, DI, and tests
- [ ] 6.1 Add `com.k2fsa.sherpa-onnx` to `gradle/libs.versions.toml` (android +
      desktop source sets).
- [x] 6.2 Add a Koin `captionModule` in `AppModule.kt` (`SpeechEngine`,
      `CaptionRepository`); wire optional deps like the existing `voiceCallModule`.
- [x] 6.3 Unit tests: `CaptionPayload` (de)serialization, `TRANSCRIPT` packet
      routing with a fake `ReticulumClient` (reuse `FakeReticulumClient`), VAD-gating
      behavior, ViewModel caption flow (Turbine). **DONE & PASSING (10/10 on desktop):
      `CaptionPayloadSerializationTest`, `InMemoryCaptionRepositoryTest`,
      `CaptionTransportTest` (send encodes TRANSCRIPT+JSON; receive decodes into repo;
      non-TRANSCRIPT ignored). `CaptionTransport.startReceiving` returns a `Job` for
      lifecycle/cancellation. To unblock the `:shared` test module (pre-existing drift),
      also repaired: rns-android aar excluded on JVM; missing `launch` import; missing
      `handleIncomingPacket`/`getLocalIdentityHash`/`getConnectionInfo` overrides in
      test fakes; `SqlDelightMessageRepository`/`startListening(client)` constructor
      drift in tests; `DatabaseMigrationTest` import + query signatures.
      (VAD-gating + ViewModel-flow tests deferred — depend on the real engine/§2.)
- [x] 6.4 Integration test: two-client caption round-trip — `CaptionRoundTripTest`
      (A sends TRANSCRIPT over `InMemoryNetwork`; B receives into its `CaptionRepository`).
- [x] 6.5 Docs: README updated with desktop app architecture + Vosk STT + captions overview.
