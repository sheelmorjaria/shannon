import { useEffect, useState } from 'react'
import { createBackend, type Backend } from './backend'
import type { ConnectionStatusDto } from './types'

/**
 * React hook (§3.3) that owns the bridge [Backend] for the app: creates + connects it on mount,
 * tracks the live connection status, and tears it down on unmount. Components read
 * `connectionStatus` and dispatch commands via `backend`.
 */
export function useBackend(): { backend: Backend | null; connectionStatus: ConnectionStatusDto } {
  const [backend, setBackend] = useState<Backend | null>(null)
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatusDto>('DISCONNECTED')

  useEffect(() => {
    const b = createBackend()
    setBackend(b)
    const off = b.on('connectionStatus.changed', (p) => setConnectionStatus(p.status))
    b.connect().catch(() => {
      /* bridge not reachable; UI stays DISCONNECTED via the default state */
    })
    return () => {
      off()
      b.close()
    }
  }, [])

  return { backend, connectionStatus }
}
