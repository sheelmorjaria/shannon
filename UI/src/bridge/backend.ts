import { BridgeClient, type EventHandler } from './client'
import type { BridgeCommand, BridgeEvent, ConnectionStatusDto, RpcResponse } from './types'

/**
 * The JSON-RPC surface the Shannon UI talks to (task 3.2). [RealBackend] wraps a [BridgeClient]
 * against the localhost bridge; [MockBackend] serves the same surface in-memory so the app runs in
 * dev/tests without the Kotlin core. App components depend on [Backend], not either concrete class.
 */
export interface Backend {
  readonly connected: boolean
  connect(): Promise<void>
  send<C extends BridgeCommand>(command: C): Promise<RpcResponse>
  on<T extends BridgeEvent['method']>(method: T, handler: EventHandler<T>): () => void
  close(): void
}

/** Backend backed by a real [BridgeClient] (the localhost Kotlin bridge). */
export class RealBackend implements Backend {
  private readonly client: BridgeClient

  constructor(url: string) {
    this.client = new BridgeClient(url)
  }

  get connected() { return this.client.connected }
  connect() { return this.client.connect() }
  send<C extends BridgeCommand>(command: C) { return this.client.send(command) }
  on<T extends BridgeEvent['method']>(method: T, handler: EventHandler<T>) {
    return this.client.on(method, handler)
  }
  close() { this.client.close() }
}

/**
 * In-memory Backend for dev/tests: commands resolve to an empty success response and subscribers
 * are tracked (a CONNECTED status is emitted on connect). Richer mock-data wiring lands with the
 * component integration (§3.3+).
 */
export class MockBackend implements Backend {
  private readonly handlers = new Map<BridgeEvent['method'], Set<(params: unknown) => void>>()
  private connectedState = false

  get connected() { return this.connectedState }

  async connect() {
    this.connectedState = true
    this.emit('connectionStatus.changed', { status: 'CONNECTED' as ConnectionStatusDto })
  }

  async send<C extends BridgeCommand>(_command: C): Promise<RpcResponse> {
    return { jsonrpc: '2.0', id: 0, result: {} }
  }

  on<T extends BridgeEvent['method']>(method: T, handler: EventHandler<T>): () => void {
    let set = this.handlers.get(method)
    if (!set) {
      set = new Set()
      this.handlers.set(method, set)
    }
    const wrapped = handler as (params: unknown) => void
    set.add(wrapped)
    return () => { set?.delete(wrapped) }
  }

  /** Push a mock event to current subscribers (test/dev helper). */
  emit(method: BridgeEvent['method'], params: unknown) {
    this.handlers.get(method)?.forEach((h) => h(params))
  }

  close() { this.connectedState = false }
}

/**
 * Choose a backend by env: set VITE_BRIDGE_URL (e.g. "ws://127.0.0.1:47329/bridge") to use the real
 * bridge; otherwise the in-memory [MockBackend] is used so the app runs standalone.
 */
export function createBackend(): Backend {
  const url = (import.meta.env as unknown as { VITE_BRIDGE_URL?: string }).VITE_BRIDGE_URL
  return url ? new RealBackend(url) : new MockBackend()
}
