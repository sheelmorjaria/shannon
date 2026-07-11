import type { Contact } from '../data'

export type VideoTileProps = {
  contact: Contact | null
  isLocal?: boolean
  isSpeaking?: boolean
  isMuted?: boolean
  audioLevel?: number     // 0–100
  isTtsSpeaking?: boolean // local user is TTS-playing translation
  cameraOff?: boolean
  size?: 'main' | 'pip' | 'grid'
}

export default function VideoTile({
  contact,
  isLocal = false,
  isSpeaking = false,
  isMuted = false,
  isTtsSpeaking = false,
  cameraOff = false,
  size = 'main',
}: VideoTileProps) {
  const isPip = size === 'pip'
  const isGrid = size === 'grid'

  const name = isLocal ? 'You' : contact?.name ?? 'Unknown'
  const photo = isLocal
    ? 'https://images.unsplash.com/photo-1590086782957-93c06ef21604?w=400&h=400&fit=crop&auto=format'
    : contact?.photo

  return (
    <div style={{
      position: 'relative',
      width: '100%',
      height: '100%',
      borderRadius: isPip ? '10px' : '12px',
      overflow: 'hidden',
      background: '#111',
      border: isSpeaking
        ? '2px solid #2563EB'
        : '2px solid rgba(255,255,255,0.06)',
      animation: isSpeaking ? 'speaking-pulse 1.4s ease-in-out infinite' : 'none',
      transform: isLocal ? 'scaleX(-1)' : 'none',
      flexShrink: 0,
    }}>
      {/* Video feed / photo placeholder */}
      {!cameraOff && photo ? (
        <img
          src={photo}
          alt={name}
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'cover',
            display: 'block',
            filter: isLocal ? 'brightness(0.95)' : 'none',
          }}
        />
      ) : (
        <div style={{
          width: '100%',
          height: '100%',
          background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
        }}>
          <div style={{
            width: isPip ? 40 : isGrid ? 56 : 80,
            height: isPip ? 40 : isGrid ? 56 : 80,
            borderRadius: '50%',
            background: 'rgba(255,255,255,0.08)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontSize: isPip ? 16 : isGrid ? 22 : 32,
            color: '#9CA3AF',
            fontWeight: 600,
            fontFamily: 'var(--font-sans)',
          }}>
            {name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()}
          </div>
        </div>
      )}

      {/* Speaking indicator overlay (subtle vignette) */}
      {isSpeaking && (
        <div style={{
          position: 'absolute',
          inset: 0,
          borderRadius: 'inherit',
          boxShadow: 'inset 0 0 0 2px rgba(37,99,235,0.6)',
          pointerEvents: 'none',
        }} />
      )}

      {/* Bottom name tag */}
      <div style={{
        position: 'absolute',
        bottom: 0,
        left: 0,
        right: 0,
        padding: isPip ? '16px 8px 6px' : '32px 12px 10px',
        background: 'linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 100%)',
        display: 'flex',
        alignItems: 'center',
        gap: '6px',
        transform: isLocal ? 'scaleX(-1)' : 'none',
      }}>
        {isMuted && !isLocal && (
          <div style={{
            width: '20px', height: '20px',
            borderRadius: '50%',
            background: 'rgba(239,68,68,0.9)',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0,
          }}>
            <MicOffIcon size={10} />
          </div>
        )}
        <span style={{
          fontSize: isPip ? '11px' : isGrid ? '13px' : '14px',
          fontWeight: 600,
          color: '#fff',
          textShadow: '0 1px 4px rgba(0,0,0,0.8)',
          lineHeight: 1,
        }}>
          {name}
        </span>

        {/* TTS soundwave on local tile */}
        {isTtsSpeaking && isLocal && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '2px', marginLeft: '4px' }}>
            {[0, 1, 2, 3, 4].map(i => (
              <div key={i} style={{
                width: '2px',
                height: '10px',
                borderRadius: '1px',
                background: '#10B981',
                transformOrigin: 'center',
                animation: `wave-bar 0.6s ease-in-out infinite`,
                animationDelay: `${i * 0.1}s`,
              }} />
            ))}
          </div>
        )}
      </div>

      {/* Camera-off badge */}
      {cameraOff && (
        <div style={{
          position: 'absolute',
          top: '8px',
          right: '8px',
          background: 'rgba(0,0,0,0.6)',
          borderRadius: '6px',
          padding: '3px 6px',
          fontSize: '11px',
          color: '#9CA3AF',
          transform: isLocal ? 'scaleX(-1)' : 'none',
        }}>
          📷 Off
        </div>
      )}
    </div>
  )
}

function MicOffIcon({ size = 12 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round">
      <line x1="1" y1="1" x2="23" y2="23" />
      <path d="M9 9v3a3 3 0 005.12 2.12M15 9.34V4a3 3 0 00-5.94-.6" />
      <path d="M17 16.95A7 7 0 015 12v-2m14 0v2c0 .38-.03.76-.1 1.12" />
    </svg>
  )
}
