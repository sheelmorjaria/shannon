# Delta: Live Translation STT/TTS Specification

## ADDED Requirements

### Requirement: [FR] The system SHALL perform on-device STT for at least 50 languages with no server or cloud API

The system SHALL perform Speech-to-Text transcription on-device using a Whisper-family (or Zipformer) model covering at least 50 languages, with no dependence on any remote server or cloud API.

#### Scenario: Transcribe speech without contacting a server
- **Given** the device has a downloaded STT model and is in an active voice call
- **When** the user speaks into the microphone
- **Then** the local SpeechEngine produces a transcript without sending audio or requests to any server

### Requirement: [FR] The system SHALL auto-detect the spoken language when none is pinned

The system SHALL automatically detect the spoken language when the user has not pinned a source language.

#### Scenario: Detect language when none is pinned
- **Given** the user has not selected a source language
- **When** speech is detected and transcribed
- **Then** the detected language is included in the emitted transcript

### Requirement: [FR] The system SHALL source STT from local microphone PCM (AudioRecorder.onBuffer / AudioEngine.onMicBuffer)

The system SHALL source STT input from the local microphone PCM already produced by the recorder, transcribing each device's own speech locally.

#### Scenario: Transcribe the local speaker's own voice
- **Given** the user's microphone capture is feeding the audio pipeline
- **When** a PCM buffer arrives from AudioRecorder.onBuffer
- **Then** the buffer is also fed to SpeechEngine.feedPcm for local transcription

### Requirement: [FR] The system SHALL use Silero VAD to segment PCM into utterances before STT

The system SHALL use Voice Activity Detection (Silero VAD) to segment PCM into complete utterances before running STT inference.

#### Scenario: Only run STT on speech segments
- **Given** continuous PCM is being captured
- **When** VAD detects no speech
- **Then** no STT inference is run until speech resumes

### Requirement: [FR] The system SHALL transport live captions as text via a new LxstPacketType.TRANSCRIPT

The system SHALL transport live captions to the peer as text via a new LxstPacketType.TRANSCRIPT sent through ReticulumClient.sendLxstPacket and decoded by ReticulumClient.observeIncomingLxstPackets.

#### Scenario: Peer receives a live caption
- **Given** the local device finalizes a transcript during a call
- **When** the transcript is sent as an LXST TRANSCRIPT packet
- **Then** the peer decodes it via observeIncomingLxstPackets and renders the caption

### Requirement: [FR] Caption payloads SHALL be a JSON object (kotlinx-serialization-json) with text, lang, final, seq

The caption packet payload SHALL be a JSON object (serialized with kotlinx-serialization-json) containing at minimum text, lang, final, and seq, and optionally translated and speakerId.

#### Scenario: Round-trip a caption payload
- **Given** a finalized caption with text, language, and sequence number
- **When** the payload is serialized and deserialized
- **Then** all fields (text, lang, final, seq, and any translated/speakerId) are preserved

### Requirement: [FR] The system SHALL render received captions in a live-captions overlay during a call

The system SHALL render received captions on the peer device in a live-captions overlay during an active call.

#### Scenario: Display incoming caption
- **Given** a TRANSCRIPT packet is received during an active call
- **When** the caption is parsed
- **Then** it is displayed in the captions overlay on the listener's device

### Requirement: [FR] The system SHALL optionally synthesize listener-side TTS via AudioPlayer.playBuffer, sending only text

The system SHALL optionally synthesize spoken audio from received (optionally translated) text via on-device TTS and play it through AudioPlayer.playBuffer, so that only text — never synthesized audio — traverses the network.

#### Scenario: Speak a received translation locally
- **Given** "Speak Translations" is enabled and a translated caption is received
- **When** the listener's device synthesizes audio from the text
- **Then** the synthesized PCM plays via AudioPlayer.playBuffer and no synthesized audio is sent over the network

### Requirement: [FR] The system SHALL let users select languages and toggle captions and speak-translations

The system SHALL let the user select source and target languages and toggle both "Live Captions" and "Speak Translations" from the UI.

#### Scenario: Enable captions and pick a target language
- **Given** the user is on the call surface
- **When** they toggle "Live Captions" on and select a target language
- **Then** captions are rendered in the selected target language for received speech

### Requirement: [FR] The system SHALL attach transcripts to async voice messages via LxmfPacket.content

The system SHALL attach transcripts to asynchronous voice messages via the existing LxmfPacket.content.

#### Scenario: Receive a voice message with its transcript
- **Given** a peer records and sends a voice message with transcription enabled
- **When** the message arrives as an LXMF packet
- **Then** the transcript text is available in LxmfPacket.content alongside the audio

### Requirement: [NFR] The system MUST introduce no server, SFU, relay, or cloud inference endpoint

All STT/VAD/TTS inference MUST run locally within the app process on Android and Desktop; no server, SFU, relay, or cloud inference endpoint SHALL be introduced.

#### Scenario: Fully offline operation
- **Given** the device has its models downloaded and no internet path to a server
- **When** STT, VAD, and TTS run during a call
- **Then** all inference executes locally in the app process

### Requirement: [NFR] Raw microphone audio MUST never be transmitted; only caption text leaves the device

Raw microphone audio SHALL never be transmitted over the network; only derived caption/translation text SHALL leave the device, over the existing E2EE Reticulum links.

#### Scenario: Only text is sent to the peer
- **Given** the user speaks during a call with captions enabled
- **When** transcription completes
- **Then** only transcript text is transmitted; the raw PCM is not

### Requirement: [NFR] STT and TTS inference MUST run on background threads and MUST NOT block the audio path

STT and TTS inference SHALL execute on background coroutines/threads and SHALL NOT block the AudioRecorder capture callback or the AudioPlayer playback path; dropped or delayed real-time audio due to ML processing SHALL be treated as a defect.

#### Scenario: Audio capture is unaffected by STT load
- **Given** STT inference is running during a call
- **When** microphone buffers arrive
- **Then** the capture callback returns promptly and real-time audio is not delayed or dropped

### Requirement: [NFR] Local STT SHALL finalize within 1.5s (desktop) / 2.5s (mobile) of the VAD boundary

Local STT SHALL emit a finalized transcript for an utterance within 1.5 s on desktop and within 2.5 s on mid-range mobile, measured from the VAD utterance boundary, for the selected model tier (target; to be confirmed by per-device profiling).

#### Scenario: Finalize within the device-class budget
- **Given** a speech utterance ends and VAD marks the boundary
- **When** STT finalizes the transcript
- **Then** the transcript is produced within the device-class budget for the chosen model tier

### Requirement: [NFR] The system SHALL emit partial (final=false) captions within 800ms of speech-segment start

The system SHALL emit partial (interim, final=false) captions within 800 ms of speech-segment start to support early rendering over high-latency Reticulum links.

#### Scenario: Stream an interim caption
- **Given** a speech segment has started
- **When** partial results are available
- **Then** an interim caption (final=false) is emitted within 800 ms

### Requirement: [NFR] Caption delivery time SHALL be reported as bounded by the Reticulum link, not STT compute

Caption delivery time to the peer SHALL be reported as bounded by the Reticulum transport, independent of STT compute time.

#### Scenario: Separate compute time from delivery time
- **Given** a transcript is ready locally
- **When** it is delivered to the peer
- **Then** the delivery time is attributable to the Reticulum link, not to STT compute

### Requirement: [NFR] Models SHALL be downloadable on demand per language in selectable tiers, not bundled

The system SHALL support multiple model quality tiers and download models on demand per language; models SHALL NOT be bundled into the application package.

#### Scenario: Download a model on demand
- **Given** the app is installed without bundled models
- **When** the user enables captions for a language
- **Then** the selected model tier for that language is downloaded on demand

### Requirement: [NFR] The system SHALL degrade gracefully when no model or insufficient compute (isAvailable == false)

When no model is available or device compute is insufficient (SpeechEngine.isAvailable == false), captions/TTS SHALL be disabled with a clear UI indication rather than crashing or degrading the call.

#### Scenario: Disable captions cleanly on a low-power device
- **Given** the device has no model or insufficient compute
- **When** the user attempts to enable captions
- **Then** captions are disabled with a clear UI indication and the call is unaffected

### Requirement: [NFR] ML failures MUST NOT crash the voice pipeline; they MUST fail gracefully and log

ML initialization, model download, or inference failures SHALL NOT crash the voice-call pipeline; they SHALL fail gracefully and log the error.

#### Scenario: Survive a model load failure
- **Given** the STT model fails to load
- **When** a call is in progress
- **Then** the error is logged, captions are disabled, and the voice call continues

### Requirement: [NFR] The system SHALL apply client-side echo handling so TTS is not re-transcribed by the mic

The system SHALL apply client-side echo handling (platform AEC in the recorder chain, and/or local STT suppression while TTS is playing on the same device) to prevent synthesized TTS audio from being re-transcribed by the listener's microphone.

#### Scenario: Listener's TTS is not re-transcribed
- **Given** the listener's device is playing synthesized TTS
- **When** the listener's microphone captures audio
- **Then** the TTS output is suppressed from local STT via AEC or STT gating
