export type ControlBarProps = {
  muted: boolean
  cameraOff: boolean
  captionsEnabled: boolean
  translationEnabled: boolean
  chatOpen: boolean
  onMute: () => void
  onCamera: () => void
  onCaptions: () => void
  onTranslation: () => void
  onChat: () => void
  onEnd: () => void
  onLanguageSettings: () => void
  compact?: boolean
}

export default function ControlBar({
  muted,
  cameraOff,
  captionsEnabled,
  translationEnabled,
  chatOpen,
  onMute,
  onCamera,
  onCaptions,
  onTranslation,
  onChat,
  onEnd,
  onLanguageSettings,
  compact = false,
}: ControlBarProps) {
  const btnSize = compact ? 44 : 52
  const iconSize = compact ? 18 : 20

  return (
    <div style={{
      display: 'flex',
      alignItems: 'center',
      gap: compact ? '8px' : '12px',
      padding: compact ? '10px 16px' : '14px 20px',
      background: 'rgba(0, 0, 0, 0.7)',
      backdropFilter: 'blur(20px)',
      WebkitBackdropFilter: 'blur(20px)',
      borderRadius: '100px',
      border: '1px solid rgba(255,255,255,0.08)',
      boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
    }}>
      {/* Mic */}
      <CtrlBtn
        active={!muted}
        danger={muted}
        onClick={onMute}
        label={muted ? 'Unmute' : 'Mute'}
        size={btnSize}
        iconSize={iconSize}
      >
        {muted ? <MicOffIcon size={iconSize} /> : <MicIcon size={iconSize} />}
      </CtrlBtn>

      {/* Camera */}
      <CtrlBtn
        active={!cameraOff}
        danger={cameraOff}
        onClick={onCamera}
        label={cameraOff ? 'Camera on' : 'Camera off'}
        size={btnSize}
        iconSize={iconSize}
      >
        {cameraOff ? <CamOffIcon size={iconSize} /> : <CamIcon size={iconSize} />}
      </CtrlBtn>

      {/* Translation / Globe — glows green when active */}
      <CtrlBtn
        active={translationEnabled}
        accentColor={translationEnabled ? '#10B981' : undefined}
        onClick={onTranslation}
        label="Translation"
        size={btnSize}
        iconSize={iconSize}
        showDot={translationEnabled}
      >
        <GlobeIcon size={iconSize} />
      </CtrlBtn>

      {/* Captions */}
      <CtrlBtn
        active={captionsEnabled}
        accentColor={captionsEnabled ? '#10B981' : undefined}
        onClick={onCaptions}
        label="Captions"
        size={btnSize}
        iconSize={iconSize}
      >
        <CaptionIcon size={iconSize} />
      </CtrlBtn>

      {/* Language settings */}
      <CtrlBtn
        active={false}
        onClick={onLanguageSettings}
        label="Language settings"
        size={btnSize}
        iconSize={iconSize}
      >
        <LangIcon size={iconSize} />
      </CtrlBtn>

      {/* Chat */}
      <CtrlBtn
        active={chatOpen}
        onClick={onChat}
        label="Chat"
        size={btnSize}
        iconSize={iconSize}
      >
        <ChatIcon size={iconSize} />
      </CtrlBtn>

      <div style={{ width: '1px', height: '28px', background: 'rgba(255,255,255,0.1)', margin: '0 4px' }} />

      {/* End call */}
      <button
        onClick={onEnd}
        title="End call"
        style={{
          width: btnSize,
          height: btnSize,
          borderRadius: '50%',
          border: 'none',
          background: '#EF4444',
          color: '#fff',
          cursor: 'pointer',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          boxShadow: '0 4px 16px rgba(239,68,68,0.5)',
          transition: 'background 0.15s, transform 0.1s',
          flexShrink: 0,
        }}
        onMouseEnter={e => (e.currentTarget as HTMLButtonElement).style.background = '#DC2626'}
        onMouseLeave={e => (e.currentTarget as HTMLButtonElement).style.background = '#EF4444'}
        onMouseDown={e => (e.currentTarget as HTMLButtonElement).style.transform = 'scale(0.94)'}
        onMouseUp={e => (e.currentTarget as HTMLButtonElement).style.transform = 'scale(1)'}
      >
        <EndCallIcon size={iconSize} />
      </button>
    </div>
  )
}

function CtrlBtn({
  active,
  danger,
  onClick,
  label,
  size,
  iconSize: _iconSize,
  children,
  accentColor,
  showDot,
}: {
  active: boolean
  danger?: boolean
  onClick: () => void
  label: string
  size: number
  iconSize: number
  children: React.ReactNode
  accentColor?: string
  showDot?: boolean
}) {
  const activeColor = accentColor ?? '#2563EB'
  const bg = danger
    ? 'rgba(239,68,68,0.15)'
    : active
    ? `${activeColor}22`
    : 'rgba(255,255,255,0.08)'
  const color = danger
    ? '#EF4444'
    : active
    ? accentColor ?? '#fff'
    : '#9CA3AF'

  return (
    <button
      title={label}
      onClick={onClick}
      style={{
        width: size,
        height: size,
        borderRadius: '50%',
        border: `1.5px solid ${active ? (accentColor ?? 'rgba(255,255,255,0.2)') : 'rgba(255,255,255,0.1)'}`,
        background: bg,
        color,
        cursor: 'pointer',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        transition: 'all 0.15s',
        flexShrink: 0,
        position: 'relative',
      }}
      onMouseEnter={e => (e.currentTarget as HTMLButtonElement).style.background = active ? `${activeColor}33` : 'rgba(255,255,255,0.12)'}
      onMouseLeave={e => (e.currentTarget as HTMLButtonElement).style.background = bg}
    >
      {children}
      {showDot && (
        <div style={{
          position: 'absolute',
          top: '2px',
          right: '2px',
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          background: '#10B981',
          border: '2px solid rgba(0,0,0,0.6)',
          animation: 'stt-blink 2s ease-in-out infinite',
        }} />
      )}
    </button>
  )
}

/* ── Icons ── */
function MicIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z" />
      <path d="M19 10v2a7 7 0 01-14 0v-2" />
      <line x1="12" y1="19" x2="12" y2="23" />
      <line x1="8" y1="23" x2="16" y2="23" />
    </svg>
  )
}

function MicOffIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <line x1="1" y1="1" x2="23" y2="23" />
      <path d="M9 9v3a3 3 0 005.12 2.12M15 9.34V4a3 3 0 00-5.94-.6" />
      <path d="M17 16.95A7 7 0 015 12v-2m14 0v2c0 .38-.03.76-.1 1.12" />
    </svg>
  )
}

function CamIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <polygon points="23 7 16 12 23 17 23 7" />
      <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
    </svg>
  )
}

function CamOffIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M16 16v1a2 2 0 01-2 2H3a2 2 0 01-2-2V7a2 2 0 012-2h2m5.66 0H14a2 2 0 012 2v3.34l1 1L23 7v10" />
      <line x1="1" y1="1" x2="23" y2="23" />
    </svg>
  )
}

function GlobeIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <circle cx="12" cy="12" r="10" />
      <line x1="2" y1="12" x2="22" y2="12" />
      <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
    </svg>
  )
}

function CaptionIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="6" width="20" height="14" rx="2" />
      <path d="M7 13h4M7 17h4M15 13h2M15 17h2" />
    </svg>
  )
}

function LangIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M5 8l6 6" />
      <path d="M4 14l6-6 2-3" />
      <path d="M2 5h12" />
      <path d="M7 2h1" />
      <path d="M22 22l-5-10-5 10" />
      <path d="M14 18h6" />
    </svg>
  )
}

function ChatIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
    </svg>
  )
}

function EndCallIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <path d="M10.68 13.31a16 16 0 003.41 2.6l1.27-1.27a2 2 0 012.11-.45c.83.29 1.68.5 2.54.62A2 2 0 0122 18v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.42 19.42 0 01-3.33-2.67m-2.67-3.34A19.79 19.79 0 012 4.18 2 2 0 014 2h3a2 2 0 012 1.72c.12.96.35 1.9.7 2.81a2 2 0 01-.45 2.11L8.09 9.91" />
      <line x1="23" y1="1" x2="1" y2="23" />
    </svg>
  )
}
