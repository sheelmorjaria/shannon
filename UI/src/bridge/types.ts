// Shannon bridge contract — TypeScript mirror of the Kotlin `com.shannon.bridge` DTOs.
// Schema version 1. Keep in sync with openspec/changes/migrate-ui-to-react and the Kotlin
// definitions in shared/src/commonMain/kotlin/com/shannon/bridge/. (Hand-mirrored for now;
// codegen from the JVM schema is a future hardening step — task 1.4.)

export const SCHEMA_VERSION = 1;

export type MessageStateDto = 'DRAFT' | 'QUEUED' | 'SENDING' | 'SENT' | 'FAILED';
export type ConnectionStatusDto = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'RECONNECTING';
export type CallStateDto = 'IDLE' | 'RINGING' | 'OUTGOING' | 'CONNECTED';

export interface MessageDto {
  id: string;
  destinationHash: string;
  content: string;
  timestamp: number;
  state: MessageStateDto;
  isOutgoing: boolean;
}

export interface ContactDto {
  destinationHash: string;
  displayName: string;
}

export interface CaptionDto {
  text: string;
  lang: string;
  translated?: string | null;
  speakerId?: string | null;
  isFinal: boolean;
  seq: number;
  sourceHash: string;
}

export interface CallStateSnapshotDto {
  state: CallStateDto;
  peerHash?: string | null;
}

/** Commands the UI sends to the Kotlin core (client → server), discriminated by `method`. */
export type BridgeCommand =
  | { method: 'message.send'; params: { destinationHash: string; content: string } }
  | { method: 'call.start'; params: { remoteHash: string } }
  | { method: 'call.end'; params: Record<string, never> }
  | { method: 'call.accept'; params: { remoteHash: string } }
  | { method: 'call.hangup'; params: Record<string, never> }
  | { method: 'captions.setEnabled'; params: { enabled: boolean } }
  | { method: 'captions.setSpeakTranslations'; params: { enabled: boolean } }
  | { method: 'captions.setSourceLang'; params: { lang: string | null } }
  | { method: 'captions.setTargetLang'; params: { lang: string | null } }
  | { method: 'captions.setModelTier'; params: { tier: string } }
  | { method: 'network.connect'; params: { host: string; port: number } }
  | { method: 'network.disconnect'; params: Record<string, never> }
  | { method: 'network.announce'; params: Record<string, never> };

/** Notifications the Kotlin core pushes to the UI (server → client). */
export type BridgeEvent =
  | { method: 'messages.updated'; params: { messages: MessageDto[] } }
  | { method: 'captions.updated'; params: { captions: CaptionDto[] } }
  | { method: 'connectionStatus.changed'; params: { status: ConnectionStatusDto } }
  | { method: 'callState.changed'; params: { snapshot: CallStateSnapshotDto } }
  | { method: 'engineAvailability.changed'; params: { available: boolean } };

export interface RpcRequest {
  jsonrpc: '2.0';
  id?: number | null;
  method: string;
  params?: unknown;
}

export interface RpcError {
  code: number;
  message: string;
}

export interface RpcResponse {
  jsonrpc: '2.0';
  id: number;
  result?: unknown;
  error?: RpcError | null;
}

export interface RpcNotification {
  jsonrpc: '2.0';
  method: string;
  params: unknown;
}
