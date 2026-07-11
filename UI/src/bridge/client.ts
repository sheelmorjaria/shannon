import type { BridgeCommand, BridgeEvent, RpcRequest, RpcResponse } from './types'

/** Handler for a server [BridgeEvent], typed by the event's method. */
export type EventHandler<T extends BridgeEvent['method']> = (
  params: Extract<BridgeEvent, { method: T }>['params'],
) => void

/**
 * Typed JSON-RPC 2.0 client over a WebSocket (task 3.1). Connects to the Shannon localhost bridge,
 * sends typed [BridgeCommand]s as requests and resolves their responses, and dispatches server
 * [BridgeEvent] notifications to subscribers.
 */
export class BridgeClient {
  private socket: WebSocket | null = null
  private nextId = 1
  private readonly pending = new Map<number, { resolve: (r: RpcResponse) => void; reject: (e: Error) => void }>()
  private readonly handlers = new Map<BridgeEvent['method'], Set<(params: unknown) => void>>()
  readonly url: string

  constructor(url: string) {
    this.url = url
  }

  get connected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }

  /** Open the WebSocket. Rejects if the connection cannot be established. */
  connect(): Promise<void> {
    return new Promise((resolve, reject) => {
      if (this.socket) {
        resolve()
        return
      }
      const socket = new WebSocket(this.url)
      this.socket = socket
      socket.onopen = () => resolve()
      socket.onerror = () => reject(new Error(`Failed to connect to bridge at ${this.url}`))
      socket.onclose = () => {
        this.socket = null
        const err = new Error('bridge connection closed')
        this.pending.forEach((p) => p.reject(err))
        this.pending.clear()
      }
      socket.onmessage = (event) => {
        const text = typeof event.data === 'string' ? event.data : ''
        if (text) this.handleMessage(text)
      }
    })
  }

  private handleMessage(text: string): void {
    let msg: { id?: number; method?: string; params?: unknown; result?: unknown; error?: unknown }
    try {
      msg = JSON.parse(text)
    } catch {
      return
    }
    if (msg.id !== undefined && msg.id !== null) {
      const pending = this.pending.get(msg.id)
      if (pending) {
        this.pending.delete(msg.id)
        pending.resolve(msg as unknown as RpcResponse)
      }
    } else if (msg.method) {
      const set = this.handlers.get(msg.method as BridgeEvent['method'])
      if (set) set.forEach((h) => h(msg.params))
    }
  }

  /** Send a typed command and await its JSON-RPC response. */
  send<C extends BridgeCommand>(command: C): Promise<RpcResponse> {
    const socket = this.socket
    if (!socket || socket.readyState !== WebSocket.OPEN) {
      return Promise.reject(new Error('bridge client is not connected'))
    }
    const id = this.nextId++
    const request: RpcRequest = { jsonrpc: '2.0', id, method: command.method, params: command.params }
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      socket.send(JSON.stringify(request))
    })
  }

  /** Subscribe to a server event; returns an unsubscribe function. */
  on<T extends BridgeEvent['method']>(method: T, handler: EventHandler<T>): () => void {
    let set = this.handlers.get(method)
    if (!set) {
      set = new Set()
      this.handlers.set(method, set)
    }
    const wrapped = handler as (params: unknown) => void
    set.add(wrapped)
    return () => {
      set?.delete(wrapped)
    }
  }

  close(): void {
    this.socket?.close()
    this.socket = null
  }
}
