import type { View } from '../App'

const NAV_ITEMS: { view: View; icon: React.ReactNode; label: string }[] = [
  {
    view: 'messages',
    label: 'Messages',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
      </svg>
    ),
  },
  {
    view: 'calls',
    label: 'Calls',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 014.69 11.5 19.79 19.79 0 011.61 2.84 2 2 0 013.61 1h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L7.91 8.64a16 16 0 006.29 6.29l.95-.95a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z" />
      </svg>
    ),
  },
  {
    view: 'network',
    label: 'Network',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="5" r="2" />
        <circle cx="5" cy="19" r="2" />
        <circle cx="19" cy="19" r="2" />
        <line x1="12" y1="7" x2="5" y2="17" />
        <line x1="12" y1="7" x2="19" y2="17" />
        <line x1="5" y1="19" x2="19" y2="19" strokeDasharray="2 2" />
      </svg>
    ),
  },
  {
    view: 'settings',
    label: 'Settings',
    icon: (
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <circle cx="12" cy="12" r="3" />
        <path d="M19.4 15a1.65 1.65 0 00.33 1.82l.06.06a2 2 0 010 2.83 2 2 0 01-2.83 0l-.06-.06a1.65 1.65 0 00-1.82-.33 1.65 1.65 0 00-1 1.51V21a2 2 0 01-4 0v-.09A1.65 1.65 0 009 19.4a1.65 1.65 0 00-1.82.33l-.06.06a2 2 0 01-2.83-2.83l.06-.06A1.65 1.65 0 004.68 15a1.65 1.65 0 00-1.51-1H3a2 2 0 010-4h.09A1.65 1.65 0 004.6 9a1.65 1.65 0 00-.33-1.82l-.06-.06a2 2 0 012.83-2.83l.06.06A1.65 1.65 0 009 4.68a1.65 1.65 0 001-1.51V3a2 2 0 014 0v.09a1.65 1.65 0 001 1.51 1.65 1.65 0 001.82-.33l.06-.06a2 2 0 012.83 2.83l-.06.06A1.65 1.65 0 0019.4 9a1.65 1.65 0 001.51 1H21a2 2 0 010 4h-.09a1.65 1.65 0 00-1.51 1z" />
      </svg>
    ),
  },
]

export default function Sidebar({ view, onViewChange }: {
  view: View
  onViewChange: (v: View) => void
}) {
  return (
    <aside style={{
      width: '64px',
      height: '100%',
      background: 'var(--card)',
      borderRight: '1px solid var(--border)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      paddingTop: '16px',
      paddingBottom: '16px',
      gap: '4px',
      flexShrink: 0,
    }}>
      {/* Logo mark */}
      <div style={{ marginBottom: '20px', paddingBottom: '16px', borderBottom: '1px solid var(--border)', width: '100%', display: 'flex', justifyContent: 'center' }}>
        <ShannonMark />
      </div>

      {NAV_ITEMS.map(item => (
        <NavButton
          key={item.view}
          active={view === item.view}
          label={item.label}
          onClick={() => onViewChange(item.view)}
        >
          {item.icon}
        </NavButton>
      ))}

      <div style={{ flex: 1 }} />

      {/* Network status indicator */}
      <div style={{
        width: '8px',
        height: '8px',
        borderRadius: '50%',
        background: 'var(--primary)',
        boxShadow: '0 0 8px var(--primary)',
        animation: 'signal-pulse 3s ease-in-out infinite',
        marginBottom: '8px',
      }} />
    </aside>
  )
}

function NavButton({ active, label, onClick, children }: {
  active: boolean
  label: string
  onClick: () => void
  children: React.ReactNode
}) {
  return (
    <button
      title={label}
      onClick={onClick}
      style={{
        width: '44px',
        height: '44px',
        borderRadius: '10px',
        border: 'none',
        background: active ? 'rgba(0, 212, 168, 0.12)' : 'transparent',
        color: active ? 'var(--primary)' : 'var(--muted-foreground)',
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.15s ease',
        outline: 'none',
      }}
      onMouseEnter={e => {
        if (!active) (e.currentTarget as HTMLButtonElement).style.color = 'var(--foreground)'
        if (!active) (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.05)'
      }}
      onMouseLeave={e => {
        if (!active) (e.currentTarget as HTMLButtonElement).style.color = 'var(--muted-foreground)'
        if (!active) (e.currentTarget as HTMLButtonElement).style.background = 'transparent'
      }}
    >
      {children}
    </button>
  )
}

function ShannonMark() {
  return (
    <svg width="32" height="32" viewBox="0 0 32 32" fill="none">
      <circle cx="16" cy="16" r="15" stroke="var(--border)" strokeWidth="1" />
      <circle cx="16" cy="16" r="2.5" fill="var(--primary)" />
      <circle cx="6" cy="10" r="1.5" fill="var(--primary)" opacity="0.5" />
      <circle cx="26" cy="10" r="1.5" fill="var(--primary)" opacity="0.5" />
      <circle cx="10" cy="26" r="1.5" fill="var(--primary)" opacity="0.5" />
      <circle cx="22" cy="26" r="1.5" fill="var(--primary)" opacity="0.5" />
      <line x1="16" y1="16" x2="6" y2="10" stroke="var(--primary)" strokeWidth="0.75" opacity="0.6" />
      <line x1="16" y1="16" x2="26" y2="10" stroke="var(--primary)" strokeWidth="0.75" opacity="0.6" />
      <line x1="16" y1="16" x2="10" y2="26" stroke="var(--primary)" strokeWidth="0.75" opacity="0.6" />
      <line x1="16" y1="16" x2="22" y2="26" stroke="var(--primary)" strokeWidth="0.75" opacity="0.6" />
    </svg>
  )
}
