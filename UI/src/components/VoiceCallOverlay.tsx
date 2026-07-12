import { useState, useEffect, useRef } from 'react'
import type { CallState } from '../data'
import { Avatar } from '../App'

const CAPTION_SAMPLES = [
  "Yes, I can hear you clearly through the mesh.",
  "The signal is routed through three nodes.",
  "On-device transcription is working really well.",
  "No internet connection needed — just Reticulum.",
  "The translation happens locally, zero latency to cloud.",
  "Let me share my screen — wait, do we have that feature?",
  "The audio quality is better than I expected.",
  "I'm on LoRa right now, bandwidth is limited but it works.",
]

const WAVEFORM_COUNTS = 28

export default function VoiceCallOverlay({
  callState,
  onEnd,
  onMute,
  onToggleCaptions,
  onToggleTranslation,
}: {
  callState: CallState
  onEnd: () => void
  onMute: () => void
  onToggleCaptions: () => void
  onToggleTranslation: () => void
}) {
  const [duration, setDuration] = useState(0)
  const [captionText, setCaptionText] = useState('')
  const [waveHeights, setWaveHeights] = useState<number[]>(() =>
    Array.from({ length: WAVEFORM_COUNTS }, () => Math.random() * 16 + 4)
  )
  const captionIdx = useRef(0)
  const captionTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    const t = setInterval(() => setDuration(d => d + 1), 1000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    const t = setInterval(() => {
      setWaveHeights(Array.from({ length: WAVEFORM_COUNTS }, () =>
        callState.muted ? 3 : Math.random() * 24 + 4
      ))
    }, 120)
    return () => clearInterval(t)
  }, [callState.muted])

  useEffect(() => {
    if (!callState.captionsEnabled) return
    function nextCaption() {
      const sample = CAPTION_SAMPLES[captionIdx.current % CAPTION_SAMPLES.length]
      captionIdx.current++
      let i = 0
      setCaptionText('')
      const typing = setInterval(() => {
        setCaptionText(sample.slice(0, i + 1))
        i++
        if (i >= sample.length) {
          clearInterval(typing)
          captionTimer.current = setTimeout(nextCaption, 3500)
        }
      }, 38)
    }
    captionTimer.current = setTimeout(nextCaption, 800)
    return () => {
      if (captionTimer.current) clearTimeout(captionTimer.current)
    }
  }, [callState.captionsEnabled])

  function formatDuration(s: number) {
    const m = Math.floor(s / 60).toString().padStart(2, '0')
    const sec = (s % 60).toString().padStart(2, '0')
    return `${m}:${sec}`
  }

  const contact = callState.contact!

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 100,
      background: 'rgba(8, 9, 14, 0.96)',
      backdropFilter: 'blur(20px)',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      animation: 'slide-up 0.3s ease',
    }}>
      {/* Close / minimize */}
      <button
        onClick={onEnd}
        style={{
          position: 'absolute',
          top: '20px',
          right: '20px',
          width: '32px', height: '32px',
          border: '1px solid var(--border)',
          borderRadius: '50%',
          background: 'transparent',
          color: 'var(--muted-foreground)',
          cursor: 'pointer',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          fontSize: '16px',
        }}
      >×</button>

      {/* LXST badge */}
      <div style={{
        position: 'absolute',
        top: '20px',
        left: '50%',
        transform: 'translateX(-50%)',
        fontFamily: 'var(--font-mono)',
        fontSize: '11px',
        color: 'var(--primary)',
        letterSpacing: '0.12em',
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
      }}>
        <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: 'var(--primary)', boxShadow: '0 0 6px var(--primary)', display: 'inline-block', animation: 'signal-pulse 2s infinite' }} />
        LXST · RETICULUM · E2EE
      </div>

      {/* Main content */}
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '24px', width: '100%', maxWidth: '480px', padding: '0 24px' }}>
        {/* Avatar with pulse rings */}
        <div style={{ position: 'relative', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          <div style={{
            position: 'absolute',
            width: '120px', height: '120px',
            borderRadius: '50%',
            border: '1.5px solid var(--primary)',
            opacity: 0.3,
            animation: 'pulse-ring 2s ease-in-out infinite',
          }} />
          <div style={{
            position: 'absolute',
            width: '140px', height: '140px',
            borderRadius: '50%',
            border: '1px solid var(--primary)',
            opacity: 0.15,
            animation: 'pulse-ring 2s ease-in-out infinite 0.5s',
          }} />
          <Avatar name={contact.name} size={88} />
        </div>

        {/* Name + language */}
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '22px', fontWeight: 600, letterSpacing: '-0.02em', marginBottom: '4px' }}>
            {contact.name}
          </div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px', justifyContent: 'center', fontSize: '13px', color: 'var(--muted-foreground)' }}>
            <span>{contact.language.flag} {contact.language.name}</span>
            {callState.translationEnabled && (
              <>
                <span>→</span>
                <span style={{ color: 'var(--accent)' }}>🌐 Auto-translated</span>
              </>
            )}
          </div>
          <div style={{ marginTop: '6px', fontFamily: 'var(--font-mono)', fontSize: '13px', color: 'var(--primary)' }}>
            {formatDuration(duration)}
          </div>
        </div>

        {/* Waveform */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: '3px',
          height: '40px',
        }}>
          {waveHeights.map((h, i) => (
            <div
              key={i}
              style={{
                width: '3px',
                height: `${h}px`,
                borderRadius: '2px',
                background: callState.muted
                  ? 'var(--muted-foreground)'
                  : i % 3 === 0 ? 'var(--primary)' : 'rgba(0,212,168,0.5)',
                transition: 'height 0.1s ease',
              }}
            />
          ))}
        </div>

        {/* Live captions */}
        {callState.captionsEnabled && (
          <div style={{
            width: '100%',
            minHeight: '56px',
            background: 'var(--card)',
            border: '1px solid var(--border)',
            borderRadius: '10px',
            padding: '12px 16px',
            display: 'flex',
            flexDirection: 'column',
            gap: '6px',
          }}>
            <div style={{
              fontSize: '10px',
              fontFamily: 'var(--font-mono)',
              color: 'var(--accent)',
              letterSpacing: '0.1em',
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
            }}>
              <span style={{
                width: '5px', height: '5px',
                borderRadius: '50%',
                background: 'var(--accent)',
                display: 'inline-block',
                animation: 'signal-pulse 1.5s infinite',
              }} />
              LIVE CAPTIONS · ON-DEVICE STT
              {callState.translationEnabled && (
                <span style={{ marginLeft: '4px', color: 'var(--muted-foreground)' }}>
                  · {contact.language.flag} → 🇬🇧
                </span>
              )}
            </div>
            <div style={{
              fontSize: '14px',
              lineHeight: 1.5,
              color: 'var(--foreground)',
              minHeight: '21px',
              animation: captionText ? 'caption-in 0.2s ease' : 'none',
            }}>
              {captionText}
              <span style={{
                display: 'inline-block',
                width: '2px',
                height: '14px',
                background: 'var(--primary)',
                marginLeft: '2px',
                verticalAlign: 'text-bottom',
                animation: 'signal-pulse 0.8s infinite',
              }} />
            </div>
          </div>
        )}

        {/* Controls */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
          <CallControlBtn
            active={!callState.muted}
            onClick={onMute}
            label={callState.muted ? 'Unmute' : 'Mute'}
            activeColor="var(--primary)"
            inactiveColor="var(--muted-foreground)"
          >
            <MicControlIcon muted={callState.muted} />
          </CallControlBtn>

          <CallControlBtn
            active={callState.captionsEnabled}
            onClick={onToggleCaptions}
            label="Captions"
            activeColor="var(--accent)"
            inactiveColor="var(--muted-foreground)"
          >
            <CaptionIcon />
          </CallControlBtn>

          <CallControlBtn
            active={callState.translationEnabled}
            onClick={onToggleTranslation}
            label="Translate"
            activeColor="var(--accent)"
            inactiveColor="var(--muted-foreground)"
          >
            <span style={{ fontSize: '16px' }}>🌐</span>
          </CallControlBtn>

          {/* End call */}
          <button
            onClick={onEnd}
            title="End call"
            style={{
              width: '56px', height: '56px',
              borderRadius: '50%',
              border: 'none',
              background: '#e53e3e',
              color: '#fff',
              cursor: 'pointer',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              boxShadow: '0 4px 20px rgba(229,62,62,0.4)',
              transition: 'transform 0.1s, opacity 0.1s',
            }}
            onMouseEnter={e => (e.currentTarget as HTMLButtonElement).style.opacity = '0.85'}
            onMouseLeave={e => (e.currentTarget as HTMLButtonElement).style.opacity = '1'}
          >
            <EndCallIcon />
          </button>
        </div>

        {/* Hop info */}
        <div style={{
          fontFamily: 'var(--font-mono)',
          fontSize: '10px',
          color: 'var(--muted-foreground)',
          textAlign: 'center',
          letterSpacing: '0.06em',
        }}>
          {contact.lxmfAddress.slice(0, 8)}…{contact.lxmfAddress.slice(-6)} · 2 hops · LoRa+TCP
        </div>
      </div>
    </div>
  )
}

function CallControlBtn({
  active,
  onClick,
  label,
  activeColor,
  inactiveColor,
  children,
}: {
  active: boolean
  onClick: () => void
  label: string
  activeColor: string
  inactiveColor: string
  children: React.ReactNode
}) {
  return (
    <button
      onClick={onClick}
      title={label}
      style={{
        width: '48px', height: '48px',
        borderRadius: '50%',
        border: `1.5px solid ${active ? activeColor : 'var(--border)'}`,
        background: active ? `${activeColor}20` : 'var(--card)',
        color: active ? activeColor : inactiveColor,
        cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'all 0.15s',
      }}
    >
      {children}
    </button>
  )
}

function MicControlIcon({ muted }: { muted: boolean }) {
  if (muted) {
    return (
      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
        <line x1="1" y1="1" x2="23" y2="23" />
        <path d="M9 9v3a3 3 0 005.12 2.12M15 9.34V4a3 3 0 00-5.94-.6" />
        <path d="M17 16.95A7 7 0 015 12v-2m14 0v2a7 7 0 01-.11 1.23" />
        <line x1="12" y1="19" x2="12" y2="23" />
        <line x1="8" y1="23" x2="16" y2="23" />
      </svg>
    )
  }
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
      <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z" />
      <path d="M19 10v2a7 7 0 01-14 0v-2" />
      <line x1="12" y1="19" x2="12" y2="23" />
      <line x1="8" y1="23" x2="16" y2="23" />
    </svg>
  )
}

function CaptionIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="6" width="20" height="14" rx="2" />
      <path d="M7 13h4M7 17h4M15 13h2M15 17h2" />
    </svg>
  )
}

function EndCallIcon() {
  return (
    <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M10.68 13.31a16 16 0 003.41 2.6l1.27-1.27a2 2 0 012.11-.45 12.84 12.84 0 002.81.7 2 2 0 011.72 2v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07 19.42 19.42 0 01-3.33-2.67m-2.67-3.34A19.79 19.79 0 012 4.18 2 2 0 014 2h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L8.09 9.91" />
      <line x1="23" y1="1" x2="1" y2="23" />
    </svg>
  )
}
