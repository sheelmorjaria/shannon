import { useState, useEffect } from 'react'
import { NETWORK_NODES } from '../data'

const INTERFACE_TYPES: Record<string, { color: string; label: string }> = {
  local: { color: 'var(--primary)', label: 'Local' },
  lora: { color: '#f6ad55', label: 'LoRa' },
  tcp: { color: '#63b3ed', label: 'TCP/IP' },
  i2p: { color: '#9f7aea', label: 'I2P' },
  serial: { color: '#68d391', label: 'Serial' },
}

const MY_ADDR = 'f4a7b2c9e103d845f7a4b2c9e103d845'

export default function NetworkPanel() {
  const [nodes, setNodes] = useState(NETWORK_NODES)
  const [announcements, setAnnouncements] = useState<string[]>([
    '14:32:01 · Destination a3f8b2c1… announced via LoRa Gateway',
    '14:31:48 · Path established to b7d4a1e8… (2 hops)',
    '14:31:22 · Beacon received from TCP Bridge EU-West',
    '14:30:55 · New path to e5c3b8f0… (5 hops, LoRa)',
    '14:30:11 · Interface Serial ttyUSB0 connected at 9600 baud',
  ])

  useEffect(() => {
    const t = setInterval(() => {
      const now = new Date()
      const hh = now.getHours().toString().padStart(2, '0')
      const mm = now.getMinutes().toString().padStart(2, '0')
      const ss = now.getSeconds().toString().padStart(2, '0')
      const events = [
        `${hh}:${mm}:${ss} · Beacon received via LoRa Gateway`,
        `${hh}:${mm}:${ss} · Path table updated (${Math.floor(Math.random() * 12) + 3} destinations)`,
        `${hh}:${mm}:${ss} · Announce from ${Math.random().toString(16).slice(2, 10)}…`,
      ]
      setAnnouncements(prev => [events[Math.floor(Math.random() * events.length)], ...prev.slice(0, 19)])
    }, 4000)
    return () => clearInterval(t)
  }, [])

  function toggleNode(id: string) {
    setNodes(prev => prev.map(n => n.id === id ? { ...n, active: !n.active } : n))
  }

  const activeCount = nodes.filter(n => n.active).length
  const totalSignal = Math.round(nodes.filter(n => n.active).reduce((acc, n) => acc + n.signal, 0) / Math.max(activeCount, 1))

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '28px 32px', display: 'flex', flexDirection: 'column', gap: '28px' }}>
      {/* Header */}
      <div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: 'var(--muted-foreground)', letterSpacing: '0.1em', marginBottom: '6px' }}>
          RETICULUM NETWORK STATUS
        </div>
        <h2 style={{ fontSize: '22px', fontWeight: 600, letterSpacing: '-0.02em', marginBottom: '16px' }}>
          Network Interfaces
        </h2>

        {/* Stats row */}
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap' }}>
          {[
            { label: 'INTERFACES', value: `${activeCount}/${nodes.length}`, color: 'var(--primary)' },
            { label: 'AVG SIGNAL', value: `${totalSignal}%`, color: 'var(--primary)' },
            { label: 'MY ADDRESS', value: `${MY_ADDR.slice(0, 8)}…`, color: 'var(--accent)', mono: true },
            { label: 'DESTINATIONS', value: '34', color: 'var(--foreground)' },
            { label: 'PROTOCOL', value: 'LXMF + LXST', color: 'var(--foreground)' },
          ].map(stat => (
            <div key={stat.label} style={{
              background: 'var(--card)',
              border: '1px solid var(--border)',
              borderRadius: 'var(--radius)',
              padding: '12px 16px',
              minWidth: '120px',
            }}>
              <div style={{ fontSize: '10px', fontFamily: 'var(--font-mono)', color: 'var(--muted-foreground)', letterSpacing: '0.08em', marginBottom: '4px' }}>
                {stat.label}
              </div>
              <div style={{
                fontSize: stat.mono ? '12px' : '18px',
                fontWeight: 700,
                color: stat.color,
                fontFamily: stat.mono ? 'var(--font-mono)' : 'var(--font-sans)',
              }}>
                {stat.value}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Interface list */}
      <div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '10px', color: 'var(--muted-foreground)', letterSpacing: '0.1em', marginBottom: '12px' }}>
          INTERFACES
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {nodes.map(node => {
            const typeInfo = INTERFACE_TYPES[node.type] ?? { color: 'var(--foreground)', label: node.type }
            return (
              <div key={node.id} style={{
                background: 'var(--card)',
                border: `1px solid ${node.active ? 'var(--border)' : 'var(--border)'}`,
                borderLeft: `3px solid ${node.active ? typeInfo.color : 'var(--border)'}`,
                borderRadius: 'var(--radius)',
                padding: '14px 16px',
                display: 'flex',
                alignItems: 'center',
                gap: '16px',
                opacity: node.active ? 1 : 0.45,
                transition: 'opacity 0.2s',
              }}>
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '2px' }}>{node.name}</div>
                  <div style={{ display: 'flex', gap: '10px', fontSize: '11px', fontFamily: 'var(--font-mono)', color: 'var(--muted-foreground)' }}>
                    <span style={{ color: typeInfo.color }}>{typeInfo.label}</span>
                    <span>·</span>
                    <span>{node.hops} {node.hops === 1 ? 'hop' : 'hops'}</span>
                    {node.active && (
                      <>
                        <span>·</span>
                        <span>{node.signal}% signal</span>
                      </>
                    )}
                  </div>
                </div>

                {/* Signal bar */}
                {node.active && (
                  <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '4px' }}>
                    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: '16px' }}>
                      {[25, 50, 75, 100].map(threshold => (
                        <div key={threshold} style={{
                          width: '4px',
                          height: `${(threshold / 100) * 16}px`,
                          borderRadius: '1px',
                          background: node.signal >= threshold ? typeInfo.color : 'var(--border)',
                          opacity: node.signal >= threshold ? 1 : 0.3,
                        }} />
                      ))}
                    </div>
                  </div>
                )}

                {/* Toggle */}
                <button
                  onClick={() => toggleNode(node.id)}
                  style={{
                    padding: '5px 12px',
                    borderRadius: '6px',
                    border: '1px solid var(--border)',
                    background: node.active ? 'rgba(0,212,168,0.1)' : 'transparent',
                    color: node.active ? 'var(--primary)' : 'var(--muted-foreground)',
                    cursor: 'pointer',
                    fontSize: '12px',
                    fontFamily: 'var(--font-mono)',
                    fontWeight: 500,
                    letterSpacing: '0.05em',
                    whiteSpace: 'nowrap',
                  }}
                >
                  {node.active ? 'UP' : 'DOWN'}
                </button>
              </div>
            )
          })}
        </div>
      </div>

      {/* Announcement log */}
      <div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '10px', color: 'var(--muted-foreground)', letterSpacing: '0.1em', marginBottom: '12px' }}>
          ANNOUNCEMENT LOG
        </div>
        <div style={{
          background: 'var(--card)',
          border: '1px solid var(--border)',
          borderRadius: 'var(--radius)',
          padding: '16px',
          maxHeight: '220px',
          overflowY: 'auto',
          display: 'flex',
          flexDirection: 'column',
          gap: '6px',
        }}>
          {announcements.map((line, i) => (
            <div key={i} style={{
              fontFamily: 'var(--font-mono)',
              fontSize: '11px',
              color: i === 0 ? 'var(--foreground)' : 'var(--muted-foreground)',
              opacity: 1 - i * 0.04,
              animation: i === 0 ? 'caption-in 0.3s ease' : 'none',
            }}>
              {line}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
