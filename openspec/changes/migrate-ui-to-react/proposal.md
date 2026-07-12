# Proposal: Migrate UI to React (Web UI + Local Bridge)

## Why
Shannon's UI today is Compose Multiplatform running in-process with the Kotlin core. A polished
React + Vite + Tailwind design already exists under `UI/` (a Figma Make export): it is
caption/translation-aware — its `callState` models `captionsEnabled`, `translationEnabled`,
`ttsEnabled`, `liveCaption`, `detectedLanguage`, and it ships a `LanguageSelector` — and covers
messages, calls, network, and settings in both desktop and mobile frames. Adopting it as the real UI
gives one web-based UI codebase, faster iteration, and a natural fit for the in-progress STT/TTS
feature.

The React app is currently **mock-data only** (no `fetch`/WebSocket/API). To make it the real UI we
must add a frontend ↔ backend boundary between the browser/webview and the Kotlin core (Reticulum
networking, audio, SQLDelight persistence, caption transport).

## What Changes
1. **Local bridge:** expose the existing Kotlin core over a localhost WebSocket + JSON-RPC 2.0 API
   (server in the desktop JVM; client in React). No remote server — this is local IPC.
2. **Webview shell:** ship the built React app inside the desktop app via a WebView, served by the
   same JVM process (single installable app; preserves the local Reticulum node).
3. **Contract:** define the command + subscription surface (messages, connection status, captions,
   call state, contacts, settings) mapped from the existing repositories/ViewModels.
4. **React wiring:** replace `UI/src/data` mock layer with a typed socket client.
5. **Media bridge:** bridge voice audio between WebAudio and the Kotlin `AudioEngine`/LXST path.
   Video is out of scope — Shannon has no video today; the React `VideoTile` is a placeholder.
6. **Phased migration:** bring features online screen-by-screen, keeping Compose working until parity.

## Impact
- **Backend (`shared`/`desktopApp`):** add a WebSocket/JSON-RPC server (e.g. ktor) wrapping the
  existing repositories, `ReticulumClient`, `AudioEngine`, `CaptionTransport`. The domain/transport
  layer built for STT/TTS (`CaptionTransport`, `CaptionRepository`, `SpeechEngine`,
  `LxstPacketType.TRANSCRIPT`, `CaptionPayload`) is reused as-is behind the API.
- **Frontend (`UI/`):** replace mock data with a real socket client; align component props to the
  contract; add the live-captions overlay (the prototype already models the state).
- **Architecture:** a server is introduced *inside the desktop app process* — this does **not**
  violate decentralization: the Reticulum node still runs P2P in the JVM; the "server" is local IPC
  to the webview, never a remote/cloud endpoint.
- **Retired:** the Compose UI (`ChatScreen`, `ConversationViewModel`, `LiveCaptionsOverlay`, Compose
  `CaptionViewModel`) once React reaches parity.

## Non-Goals
- Introduce any remote/cloud server (the bridge is localhost-only).
- Add video calling (Shannon is voice + text today).
- Compile `shared` to JS/WASM — blocked by native deps (`reticulum-kt`, SQLDelight drivers,
  `AudioEngine`). The Kotlin core stays JVM; React talks to it over the local socket.
