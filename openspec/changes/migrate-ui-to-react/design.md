# Design: Migrate UI to React (Web UI + Local Bridge)

## Context
- Current: Compose Multiplatform UI in `shared/commonMain` (`com.shannon.ui.ChatScreen`,
  `com.shannon.viewmodel.*`) runs in-process with the Kotlin core (`ReticulumClient`, `AudioEngine`,
  repositories, `CaptionTransport`). The desktop app (`desktopApp`) is Compose Desktop (JVM).
- `UI/`: React 19 + Vite + Tailwind (Figma Make export), mock-data only, no backend. Already models
  call/captions/translation state and a mobile+desktop layout.
- Constraint: a browser/webview cannot call `reticulum-kt`, the JVM `AudioEngine`, or SQLDelight
  directly, and `shared` cannot compile to JS (native deps). React must talk to the JVM core over a
  local transport.

## Goals
- One web-based UI (React) for Shannon desktop, feature-equivalent to the current Compose UI.
- Bridge is localhost-only — no remote server; decentralization and E2EE preserved.
- Reuse the Kotlin core unchanged behind a stable API.
- Keep Compose runnable until parity, then retire it.

## Decisions

### D1. Transport: localhost WebSocket + JSON-RPC 2.0
Bidirectional (server pushes state/flows; client sends commands), localhost-only. JSON-RPC gives a
small typed request/response + notification model. Server runs in the desktop JVM (ktor server
websockets). Rationale: flows (`observeMessages`, `observeCaptions`, connection status) need push;
JSON-RPC is lightweight and language-agnostic for the JS client.

### D2. Shell: WebView desktop app (recommended)
Ship the built React bundle (`UI/dist`) as a resource served by the JVM, rendered in an OS WebView
inside the Compose Desktop window (or a native webview wrapper). Single installable app; the local
Reticulum node runs in the JVM alongside the server. Alternative: a standalone browser app + a local
companion node process — more moving parts; deferred.

### D3. Contract (the first deliverable)
Define as a versioned schema shared by JVM and TS:
- Commands: `sendMessage`, `startCall`, `endCall`, `acceptCall`, `hangup`, `setCaptionsEnabled`,
  `setSpeakTranslations`, `setSourceLang`, `setTargetLang`, `connect`, `disconnect`, `announce` —
  mapped to existing repository / `VoiceCallManager` / `CaptionViewModel` operations.
- Subscriptions (server → client streams): `messages`, `connectionStatus`, `contacts`, `captions`,
  `callState`, `engineAvailable` — backed by existing Kotlin `Flow`s.
- Types: mirror `Message`, `Contact`, `Caption`/`CaptionPayload`, `CallState`, `ConnectionStatus`.

### D4. Media bridge (hardest; phased)
Voice audio must flow between the browser (WebAudio) and the JVM `AudioEngine`/LXST path. Phase 1:
capture/playback in the JVM (existing `AudioRecorder`/`AudioPlayer`); the webview only controls the
call. Phase 2 (optional): capture/playback in the browser, stream PCM over the socket for
encoding/LXST. Echo cancellation and the "no audio leaves the device" invariant are preserved. Video
is out of scope (no video in Shannon today).

### D5. State serialization
Expose Kotlin `StateFlow`/`Flow` as JSON-RPC notifications (`flow.subscribe` + change events). Keep
DTOs stable and versioned.

## Risks & Mitigations
- **Media bridge complexity / latency:** local socket latency is negligible, but PCM streaming adds
  work. *Mitigation:* phase D4; keep audio JVM-side first.
- **Two UIs during migration:** *Mitigation:* feature-flag; migrate screen-by-screen (messages →
  settings → network → calls → captions); retire Compose only at parity.
- **WebView per-OS differences:** *Mitigation:* pick a maintained webview component; CI on targets.
- **Contract drift:** *Mitigation:* generate TS types from the JVM schema.
- **Scope creep into video:** *Mitigation:* explicit non-goal; the React `VideoTile` is a placeholder.

## Architecture Diagram
```
 ┌─────────────────────── Desktop App (JVM) ───────────────────────┐
 │  Compose Desktop shell ──► WebView ──► React UI (UI/dist)       │
 │                                     ▲                            │
 │                                     │ localhost WebSocket+JSONRPC│
 │                                     ▼                            │
 │  Bridge server (ktor) ──► Kotlin core: ReticulumClient,         │
 │                            AudioEngine, repos, VoiceCallManager, │
 │                            CaptionTransport                      │
 │                                              │                   │
 └──────────────────────────────────────────────┼───────────────────┘
                                                ▼
                                  [ Reticulum (E2EE, P2P) ]
```
Key invariant: the WebSocket is **localhost only**. No audio/text leaves to any remote server;
Reticulum traffic is unchanged.

## Phasing
1. Contract schema + TS types + minimal socket client.
2. Bridge server: messages (LXMF), contacts, connection status, settings (pure data) — no media.
3. Webview shell serving `UI/dist`.
4. Calls: control via bridge; audio JVM-side (D4 phase 1).
5. Captions/translation: expose `CaptionTransport`/`CaptionRepository` + `SpeechEngine` status.
6. Parity review → retire Compose UI.
