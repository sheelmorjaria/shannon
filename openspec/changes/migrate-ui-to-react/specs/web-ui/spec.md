# Delta: Web UI + Local Bridge Specification

## ADDED Requirements

### Requirement: [FR] The system SHALL expose the Kotlin core over a localhost-only WebSocket

The system SHALL expose the Kotlin core (networking, persistence, audio, captions) to the React UI via a WebSocket bound to localhost, using JSON-RPC 2.0, and SHALL NOT bind the bridge to any non-local interface.

#### Scenario: UI connects to the local bridge
- **Given** the desktop app is running
- **When** the React UI opens
- **Then** it connects to the bridge on localhost and no socket is reachable from a remote host

### Requirement: [FR] The system SHALL provide a versioned command and subscription contract

The bridge SHALL define a versioned set of JSON-RPC commands and subscriptions mapping the existing repositories/ViewModels (messages, contacts, connection status, call state, captions, settings) and SHALL share the schema as TypeScript types generated from the JVM definitions.

#### Scenario: TypeScript types match the JVM schema
- **Given** the contract schema is defined on the JVM
- **When** TypeScript types are generated
- **Then** the React app compiles against types matching the current JVM schema version

### Requirement: [FR] The React UI SHALL replace its mock data with a typed socket client

The React app under UI/ SHALL replace the src/data mock layer with a typed JSON-RPC client and SHALL drive the same Kotlin operations the Compose UI used.

#### Scenario: Sending a message reaches the Kotlin core
- **Given** the React UI is connected to the bridge
- **When** the user sends a message
- **Then** the JSON-RPC command reaches MessageRepository and is sent over LXMF

### Requirement: [FR] The desktop app SHALL host the React UI in a WebView

The desktop app SHALL serve the built React bundle and render it in an OS WebView within the app window, with no separate browser required.

#### Scenario: App launches into the React UI
- **Given** the desktop app is installed
- **When** it starts
- **Then** the React UI is shown inside the app window

### Requirement: [FR] The bridge SHALL stream Kotlin Flows to the UI as notifications

The bridge SHALL expose Kotlin Flow/StateFlow values (messages, captions, connection status, call state) to the UI as JSON-RPC notifications so the React state stays live.

#### Scenario: Incoming caption appears in React
- **Given** a TRANSCRIPT caption is received into CaptionRepository
- **When** the UI is subscribed to captions
- **Then** the React caption state updates

### Requirement: [NFR] The bridge MUST NOT introduce any remote or cloud server

The bridge MUST bind only to localhost; no Shannon traffic (audio, messages, captions) SHALL be sent to any remote server via the bridge, preserving the decentralized, E2EE Reticulum model.

#### Scenario: No remote exposure
- **Given** the bridge is running
- **When** a remote host attempts to connect
- **Then** the connection is refused

### Requirement: [NFR] The migration SHALL preserve feature parity before retiring Compose

The React UI SHALL reach feature parity with the Compose UI (messages, calls, network, settings, captions) before the Compose UI is removed; the Compose UI MUST remain buildable until parity is verified.

#### Scenario: Compose stays until parity
- **Given** the React UI covers messages and calls only
- **When** parity is evaluated
- **Then** the Compose UI is still present and buildable for the remaining screens

### Requirement: [NFR] The bridge MUST NOT send raw microphone audio to any remote endpoint

Raw microphone audio MUST NOT leave the device to any remote endpoint; the on-device audio invariant from the STT/TTS design is preserved across the bridge.

#### Scenario: Audio stays local
- **Given** a call is active through the bridge
- **When** audio is captured
- **Then** it is processed locally and only Reticulum LXST audio/text traverses the network
