import { useState } from 'react'
import { LANGUAGES, type Language } from '../data'

type Props = {
  myLanguage: Language
  speakingLanguage: Language
  translateTo: Language
  ttsEnabled: boolean
  onSave: (speaking: Language, translateTo: Language, ttsEnabled: boolean) => void
  onClose: () => void
}

export default function LanguageSelector({ myLanguage: _myLanguage, speakingLanguage, translateTo, ttsEnabled, onSave, onClose }: Props) {
  const [speaking, setSpeaking] = useState(speakingLanguage)
  const [transTo, setTransTo] = useState(translateTo)
  const [tts, setTts] = useState(ttsEnabled)

  function handleSave() {
    onSave(speaking, transTo, tts)
    onClose()
  }

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 200,
      background: 'rgba(0,0,0,0.5)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      animation: 'fade-in 0.2s ease',
    }}
    onClick={onClose}
    >
      <div
        onClick={e => e.stopPropagation()}
        style={{
          background: '#1E1E1E',
          border: '1px solid rgba(255,255,255,0.12)',
          borderRadius: '16px',
          padding: '28px',
          width: '420px',
          maxWidth: '90vw',
          boxShadow: '0 24px 64px rgba(0,0,0,0.8)',
          display: 'flex',
          flexDirection: 'column',
          gap: '24px',
          animation: 'slide-up 0.25s ease',
        }}
      >
        {/* Header */}
        <div>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '6px' }}>
            <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0 }}>
              Live Translation & Captions
            </h2>
            <button onClick={onClose} style={{
              width: '28px', height: '28px', borderRadius: '50%',
              border: '1px solid rgba(255,255,255,0.1)',
              background: 'transparent',
              color: '#9CA3AF',
              cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontSize: '16px',
            }}>×</button>
          </div>
          <p style={{ fontSize: '13px', color: '#6B7280', margin: 0, display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ color: '#10B981' }}>●</span>
            Powered by on-device Whisper AI
          </p>
        </div>

        {/* I am speaking */}
        <div>
          <label style={{ fontSize: '12px', fontWeight: 600, color: '#9CA3AF', letterSpacing: '0.08em', display: 'block', marginBottom: '10px' }}>
            I AM SPEAKING
          </label>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {LANGUAGES.map(lang => (
              <LanguageChip
                key={lang.code}
                lang={lang}
                selected={speaking.code === lang.code}
                onClick={() => setSpeaking(lang)}
              />
            ))}
          </div>
        </div>

        {/* Translate to */}
        <div>
          <label style={{ fontSize: '12px', fontWeight: 600, color: '#9CA3AF', letterSpacing: '0.08em', display: 'block', marginBottom: '10px' }}>
            TRANSLATE TO
          </label>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {LANGUAGES.filter(l => l.code !== 'auto').map(lang => (
              <LanguageChip
                key={lang.code}
                lang={lang}
                selected={transTo.code === lang.code}
                onClick={() => setTransTo(lang)}
              />
            ))}
            <LanguageChip
              lang={{ code: 'none', name: 'None', flag: '🚫' }}
              selected={transTo.code === 'none'}
              onClick={() => setTransTo({ code: 'none', name: 'None', flag: '🚫' })}
            />
          </div>
        </div>

        {/* TTS toggle */}
        <div style={{
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: '16px',
          padding: '14px 16px',
          background: 'rgba(255,255,255,0.04)',
          border: '1px solid rgba(255,255,255,0.08)',
          borderRadius: '10px',
        }}>
          <div>
            <div style={{ fontSize: '14px', fontWeight: 600, marginBottom: '4px' }}>
              Speak translations aloud
            </div>
            <div style={{ fontSize: '12px', color: '#6B7280', lineHeight: 1.4 }}>
              TTS engine voices the translated text so callers hear it in their language
            </div>
          </div>
          <button
            onClick={() => setTts(t => !t)}
            style={{
              width: '48px', height: '26px', borderRadius: '13px', border: 'none',
              background: tts ? '#10B981' : '#374151',
              cursor: 'pointer', position: 'relative', flexShrink: 0,
              transition: 'background 0.2s',
            }}
          >
            <div style={{
              position: 'absolute', top: '3px',
              left: tts ? '25px' : '3px',
              width: '20px', height: '20px', borderRadius: '50%',
              background: '#fff', transition: 'left 0.2s',
              boxShadow: '0 1px 4px rgba(0,0,0,0.4)',
            }} />
          </button>
        </div>

        {/* Save */}
        <button
          onClick={handleSave}
          style={{
            padding: '12px',
            background: '#2563EB',
            border: 'none',
            borderRadius: '10px',
            color: '#fff',
            fontSize: '15px',
            fontWeight: 600,
            cursor: 'pointer',
            fontFamily: 'var(--font-sans)',
            transition: 'background 0.15s',
          }}
          onMouseEnter={e => (e.currentTarget as HTMLButtonElement).style.background = '#1D4ED8'}
          onMouseLeave={e => (e.currentTarget as HTMLButtonElement).style.background = '#2563EB'}
        >
          Save & Close
        </button>
      </div>
    </div>
  )
}

function LanguageChip({ lang, selected, onClick }: { lang: Language; selected: boolean; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{
        padding: '6px 12px',
        borderRadius: '20px',
        border: `1.5px solid ${selected ? '#2563EB' : 'rgba(255,255,255,0.1)'}`,
        background: selected ? 'rgba(37,99,235,0.2)' : 'transparent',
        color: selected ? '#60A5FA' : '#9CA3AF',
        cursor: 'pointer',
        fontSize: '13px',
        fontWeight: 500,
        display: 'flex',
        alignItems: 'center',
        gap: '5px',
        transition: 'all 0.15s',
        whiteSpace: 'nowrap',
      }}
    >
      <span>{lang.flag}</span>
      <span>{lang.name}</span>
    </button>
  )
}
