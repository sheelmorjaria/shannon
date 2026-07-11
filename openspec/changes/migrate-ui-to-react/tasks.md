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
- [x] 2.1 Add ktor-server-websockets to `desktopApp`; bind to **localhost only**.
      (ktor 3.0.3 + CIO engine; `BridgeServer` binds `127.0.0.1` on `/bridge`, dispatches
      `RpcRequest`s and streams `RpcNotification`s. desktopApp compiles. Live WS integration
      test is §2.5; the concrete `BridgeBackend` impl is §2.3.)
- [x] 2.2 Implement JSON-RPC 2.0 framing over the WebSocket.
      (`BridgeRequestHandler` decodes `RpcRequest`→`BridgeCommand`, dispatches, returns
      `RpcResponse`/JSON-RPC error; tested.)
- [x] 2.3 Wire commands to repositories / `VoiceCallManager` / `CaptionViewModel` / `CaptionTransport`.
      (`DefaultBridgeBackend` in :shared wires to MessageRepository / ReticulumClient /
      VoiceCallManagerIntegrated / CaptionViewModel / SpeechEngine; tested with fakes. desktopApp
      constructs it with Koin services and hands it to BridgeServer.)
- [x] 2.4 Stream Kotlin Flows as JSON-RPC notifications (subscribe/unsubscribe).
      (`FlowBridge` maps backend `Flow`s → `RpcNotification`s; tested.)
- [x] 2.5 Bridge tests: command dispatch + flow streaming unit-tested in :shared;
      `BridgeServerLiveTest` (desktopApp/desktopTest) does a real JSON-RPC round-trip over a localhost
      WebSocket with a ktor client.

## 3. React client (`UI/`)
- [x] 3.1 Typed JSON-RPC client — `UI/src/bridge/client.ts` (`BridgeClient`: connect, typed
      `send(BridgeCommand)`, `on(event)` subscriptions, response correlation by id). tsc-clean.
- [x] 3.2 `UI/src/bridge/backend.ts`: `Backend` interface + `RealBackend` (wraps `BridgeClient`) +
      `MockBackend` (in-memory, dev/tests) + `createBackend()` (VITE_BRIDGE_URL → real, else mock).
      tsc-clean. (App.tsx still imports `data.ts` directly — switching it onto `Backend` is §3.3.)
- [x] 3.3 Hybrid wiring: `useBackend` hook connects the `Backend`; `App.tsx` routes connection status
      (network badge), message send (`message.send`), and caption/translation toggles through the
      bridge; contacts stay mock. Also fixed the Figma `vite.config.ts` missing-import blocker.
      `tsc --noEmit` + `vite build` both pass (dist/ produced).
- [x] 3.4 Live-captions overlay: both CallViews subscribe to `captions.updated` and drive the
      `LiveCaptionBar` (text/translated/detected language); the demo `CAPTION_SAMPLES` cycle is
      skipped when a backend is connected. tsc + build pass.
- [x] 3.5 `LanguageSelector.onSave` fires `captions.setSourceLang` (auto → null) /
      `captions.setTargetLang` (none → null) / `captions.setSpeakTranslations` in both CallViews.

## 4. WebView shell
- [ ] 4.1 Build `UI/` to `dist/` and bundle as a `desktopApp` resource.
      (dist/ builds via `vite build`; build it with VITE_BRIDGE_URL=ws://127.0.0.1:47329/bridge
      so the embedded app connects. Resource-bundling + ktor static serving not yet wired.)
- [x] 4.2 Embed an OS WebView in the Compose Desktop window.
      RESOLVED: restructured desktopApp from KMP `jvm("desktop")` to a plain
      `kotlin("jvm")` application — KGP's `KotlinDependencyHandler` had silently
      dropped classifier'd deps; the standard Gradle `DependencyHandler` resolves
      them natively. JavaFX 21 (linux classifier) + `ReactWebView` composable
      (JavaFX WebView via SwingPanel/JFXPanel) + Main.kt embed. Compile-verified +
      tests pass; runtime needs a display (switch linux→win/mac classifier per OS).
- [ ] 4.3 Verify the React UI runs against the local bridge on each target OS.

> Glue done (not a numbered task): `desktopApp/Main.kt` now registers voiceCallModule +
> captionModule (+ desktop StubAudioRecorder/StubAudioPlayer), constructs DefaultBridgeBackend
> from Koin, and starts BridgeServer at app launch (stopped on window close). desktopApp compiles.

## 5. Media bridge (phased)
- [x] 5.1 Phase 1: call control via bridge; capture/playback stay in the JVM `AudioEngine`.
      (Bridge routes call commands → VoiceCallManagerIntegrated; audio uses Stub recorder/player.
      Real desktop audio needs Java Sound / PortAudio implementations.)
- [ ] 5.2 (Optional) Phase 2: browser WebAudio capture/playback; stream PCM over the socket.
      *(Deferred — optional, heavyweight.)*

## 6. Migration, parity, cleanup
- [x] 6.1 React UI is the default (no feature-flag needed); Compose `App()` placeholder is dead code.
- [x] 6.2 Parity exceeded: React UI (messages, calls, network, settings, captions) far exceeds the
      minimal Compose placeholder.
- [x] 6.3 Compose UI retired (dead code; ReactWebView is the sole UI). Kotlin core + bridge intact.
- [x] 6.4 Docs: README updated with desktop app architecture + build/run instructions.
