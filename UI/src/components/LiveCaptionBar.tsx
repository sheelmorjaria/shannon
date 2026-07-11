import type { Language } from '../data'

export type SttState = 'idle' | 'listening' | 'processing' | 'showing'

type Props = {
  sttState: SttState
  speakerName: string
  speakerLanguage: Language
  detectedLanguage: Language | null
  captionText: string
  translatedText: string
  translationEnabled: boolean
  myLanguage: Language
}

export default function LiveCaptionBar({
  sttState,
  speakerName,
  speakerLanguage,
  detectedLanguage,
  captionText,
  translatedText,
  translationEnabled,
  myLanguage,
}: Props) {
  const displayLanguage = detectedLanguage ?? speakerLanguage
  const isAnimatingFlag = detectedLanguage !== null && detectedLanguage.code !== speakerLanguage.code

  return (
    <div style={{
      width: '80%',
      maxWidth: '720px',
      background: 'rgba(0, 0, 0, 0.75)',
      backdropFilter: 'blur(12px)',
      WebkitBackdropFilter: 'blur(12px)',
      borderRadius: '12px',
      border: '1px solid rgba(255,255,255,0.08)',
      padding: '14px 18px',
      display: 'flex',
      flexDirection: 'column',
      gap: '6px',
      boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
    }}>
      {/* Row 1: Speaker name + flag + STT status */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
      }}>
        {/* Pulsing dot */}
        <div style={{
          width: '8px',
          height: '8px',
          borderRadius: '50%',
          background: sttState === 'idle' ? '#4B5563' : '#10B981',
          flexShrink: 0,
          animation: sttState === 'listening' || sttState === 'showing'
            ? 'stt-blink 1.2s ease-in-out infinite'
            : 'none',
          boxShadow: sttState !== 'idle' ? '0 0 6px #10B981' : 'none',
          transition: 'background 0.3s, box-shadow 0.3s',
        }} />

        <span style={{
          fontSize: '14px',
          fontWeight: 700,
          color: '#10B981',
          lineHeight: 1,
        }}>
          {speakerName}
        </span>

        {/* Language flag — animates when auto-detecting */}
        <span style={{
          fontSize: '16px',
          display: 'inline-block',
          animation: isAnimatingFlag ? 'flag-flip 0.5s ease' : 'none',
          transition: 'opacity 0.3s',
        }}>
          {displayLanguage.flag}
        </span>

        {sttState !== 'idle' && (
          <span style={{
            fontSize: '12px',
            color: '#6B7280',
            fontFamily: 'var(--font-mono)',
            letterSpacing: '0.04em',
          }}>
            {sttState === 'listening' ? 'listening…'
              : sttState === 'processing' ? 'processing…'
              : 'transcribing'}
          </span>
        )}
      </div>

      {/* Row 2: Caption text */}
      <div style={{ minHeight: '27px' }}>
        {sttState === 'listening' && captionText === '' ? (
          <ListeningDots />
        ) : (
          <span style={{
            fontSize: '18px',
            fontWeight: 600,
            color: '#FFFFFF',
            lineHeight: 1.5,
            display: 'block',
            animation: captionText ? 'caption-in 0.2s ease' : 'none',
          }}>
            {captionText || <span style={{ color: '#4B5563', fontStyle: 'italic', fontWeight: 400, fontSize: '16px' }}>Waiting for speech…</span>}
          </span>
        )}
      </div>

      {/* Row 3: Translation */}
      {translationEnabled && translatedText && (
        <div style={{
          animation: 'caption-in 0.25s ease 0.1s both',
        }}>
          <span style={{
            fontSize: '16px',
            fontWeight: 400,
            color: '#9CA3AF',
            fontStyle: 'italic',
            lineHeight: 1.5,
          }}>
            {myLanguage.flag} {translatedText}
          </span>
        </div>
      )}
    </div>
  )
}

function ListeningDots() {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '5px', padding: '4px 0' }}>
      {[0, 1, 2].map(i => (
        <div key={i} style={{
          width: '7px',
          height: '7px',
          borderRadius: '50%',
          background: '#9CA3AF',
          animation: 'dot-typing 1.2s ease-in-out infinite',
          animationDelay: `${i * 0.2}s`,
        }} />
      ))}
    </div>
  )
}
