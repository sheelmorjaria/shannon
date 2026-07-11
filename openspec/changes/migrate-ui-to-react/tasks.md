# Tasks: Migrate UI to React (Web UI + Local Bridge)

## 1. Contract (shared schema) — do first
- [x] 1.1 Define the JSON-RPC command set (`sendMessage`, `startCall`, `endCall`, `acceptCall`,
      `hangup`, `setCaptionsEnabled`, `setSpeakTranslations`, `setSourceLang`, `setTargetLang`,
      `connect`, `disconnect`, `announce`) mapped to existing Kotlin operations.
- [x] 1.2 Define the subscription/stream set (`messages`, `contacts`, `connectionStatus`,
      `captions`, `callState`, `engineAvailable`) backed by existing Kotlin Flows.
- [x] 1.3 Define JSON DTOs mirroring `Message`, `Contact`, `Caption`/`CaptionPayload`, `CallState`,
      `ConnectionStatus`; version the schema.
- [x] 1.4 TypeScript types: `UI/src/bridge/types.ts` hand-mirrors the JVM DTOs/commands/events
      (codegen from the JVM schema is a future hardening step; for now kept in sync manually).

## 2. Bridge server (Kotlin/JVM, desktopApp)
- [ ] 2.1 Add ktor-server-websockets (or equivalent) to `desktopApp`; bind to **localhost only**.
- [ ] 2.2 Implement JSON-RPC 2.0 framing over the WebSocket.
- [ ] 2.3 Wire commands to repositories / `VoiceCallManager` / `CaptionViewModel` / `CaptionTransport`.
- [ ] 2.4 Stream Kotlin Flows as JSON-RPC notifications (subscribe/unsubscribe).
- [ ] 2.5 Bridge tests: command dispatch + flow streaming with fakes (reuse `FakeReticulumClient`,
      `InMemoryNetwork`) and a small WS test client.

## 3. React client (`UI/`)
- [ ] 3.1 Add a typed JSON-RPC client (WebSocket) in `UI/src`.
- [ ] 3.2 Replace the `UI/src/data` mock layer with the socket client; keep a mock adapter for dev/tests.
- [ ] 3.3 Map React `callState`/props to the contract; wire Sidebar/ConversationList/ChatArea/
      NetworkPanel/SettingsPanel.
- [ ] 3.4 Add the live-captions overlay to the React CallView (model already present in `callState`).
- [ ] 3.5 Wire `LanguageSelector` to `setSourceLang`/`setTargetLang` (auto-detect = null source).

## 4. WebView shell
- [ ] 4.1 Build `UI/` to `dist/` and bundle as a `desktopApp` resource.
- [ ] 4.2 Embed an OS WebView in the Compose Desktop window; serve the bundle from the JVM.
- [ ] 4.3 Verify the React UI runs against the local bridge on each target OS.

## 5. Media bridge (phased)
- [ ] 5.1 Phase 1: call control via bridge; capture/playback stay in the JVM `AudioEngine`.
- [ ] 5.2 (Optional) Phase 2: browser WebAudio capture/playback; stream PCM over the socket; preserve
      echo cancellation and the "no remote audio" invariant.

## 6. Migration, parity, cleanup
- [ ] 6.1 Feature-flag the UI choice; migrate screen-by-screen (messages → settings → network →
      calls → captions).
- [ ] 6.2 Parity checklist vs the Compose UI; verify each screen.
- [ ] 6.3 Retire the Compose UI only after parity; keep the Kotlin core + bridge.
- [ ] 6.4 Docs: update README/PRODUCTION_CHECKLIST with the web-UI architecture + local-bridge note.
