import { useState, useEffect, useRef } from 'react'
import type { CallState, Language } from '../data'
import { CAPTION_SAMPLES, LANGUAGES } from '../data'
import VideoTile from './VideoTile'
import ControlBar from './ControlBar'
import LiveCaptionBar from './LiveCaptionBar'
import LanguageSelector from './LanguageSelector'

const MY_LANGUAGE = LANGUAGES.find(l => l.code === 'en')!

type Props = {
  callState: CallState
  onEnd: () => void
  onMute: () => void
  onCamera: () => void
  onCaptions: () => void
  onTranslation: () => void
}

export default function CallViewDesktop({ callState, onEnd, onMute, onCamera, onCaptions, onTranslation }: Props) {
  const [duration, setDuration] = useState(0)
  const [chatOpen, setChatOpen] = useState(false)
  const [langSelectorOpen, setLangSelectorOpen] = useState(false)
  const [speakingLang, setSpeakingLang] = useState<Language>(LANGUAGES.find(l => l.code === 'auto')!)
  const [translateTo, setTranslateTo] = useState<Language>(MY_LANGUAGE)
  const [ttsEnabled, setTtsEnabled] = useState(true)
  const [isTtsSpeaking, setIsTtsSpeaking] = useState(false)

  // STT caption state machine
  const [sttState, setSttState] = useState<'idle' | 'listening' | 'processing' | 'showing'>('listening')
  const [captionText, setCaptionText] = useState('')
  const [translatedText, setTranslatedText] = useState('')
  const [detectedLanguage, setDetectedLanguage] = useState<Language | null>(null)
  const [isSpeaking, setIsSpeaking] = useState(true)
  const captionIdx = useRef(0)
  const captionTimer = useRef<ReturnType<typeof setTimeout> | null>(null)

  useEffect(() => {
    const t = setInterval(() => setDuration(d => d + 1), 1000)
    return () => clearInterval(t)
  }, [])

  // STT state machine: idle → listening (300ms) → showing → idle loop
  useEffect(() => {
    if (!callState.captionsEnabled) {
      setSttState('idle')
      setCaptionText('')
      setTranslatedText('')
      return
    }

    function runCaptionCycle() {
      const sample = CAPTION_SAMPLES[captionIdx.current % CAPTION_SAMPLES.length]
      captionIdx.current++

      // Start listening
      setSttState('listening')
      setCaptionText('')
      setTranslatedText('')
      setIsSpeaking(true)

      // After 300ms "listening" dots → start typing caption
      captionTimer.current = setTimeout(() => {
        setSttState('showing')

        // Simulate language auto-detection
        if (sample.lang.code !== 'en') {
          setDetectedLanguage(null)
          setTimeout(() => setDetectedLanguage(sample.lang), 400)
        }

        // Typewriter effect for original text
        let i = 0
        const typing = setInterval(() => {
          setCaptionText(sample.original.slice(0, i + 1))
          i++
          if (i >= sample.original.length) {
            clearInterval(typing)
            // After typing completes, show translation (150ms delay)
            if (callState.translationEnabled) {
              setTimeout(() => {
                setTranslatedText(sample.translated)
                // Simulate TTS speaking the translation
                if (ttsEnabled) {
                  setIsTtsSpeaking(true)
                  setTimeout(() => setIsTtsSpeaking(false), 2800)
                }
              }, 150)
            }
            // Pause, then next cycle
            setIsSpeaking(false)
            captionTimer.current = setTimeout(runCaptionCycle, 4000)
          }
        }, 35)
      }, 300)
    }

    captionTimer.current = setTimeout(runCaptionCycle, 1200)
    return () => {
      if (captionTimer.current) clearTimeout(captionTimer.current)
    }
  }, [callState.captionsEnabled, callState.translationEnabled, ttsEnabled])

  function formatDuration(s: number) {
    const m = Math.floor(s / 60).toString().padStart(2, '0')
    return `${m}:${(s % 60).toString().padStart(2, '0')}`
  }

  const contact = callState.contact!

  return (
    <div style={{
      position: 'fixed',
      inset: 0,
      zIndex: 100,
      background: '#0F0F0F',
      display: 'flex',
      animation: 'fade-in 0.3s ease',
    }}>
      {/* ── Main video area ── */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        {/* Main speaker tile — 70% centered */}
        <div style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '0',
        }}>
          <VideoTile
            contact={contact}
            isSpeaking={isSpeaking}
            isMuted={false}
            size="main"
          />
        </div>

        {/* PiP — local user, top-right */}
        <div style={{
          position: 'absolute',
          top: '16px',
          right: chatOpen ? '16px' : '16px',
          width: '200px',
          height: '150px',
          borderRadius: '10px',
          overflow: 'hidden',
          boxShadow: '0 4px 24px rgba(0,0,0,0.6)',
          zIndex: 10,
          cursor: 'grab',
        }}>
          <VideoTile
            contact={null}
            isLocal
            isMuted={callState.muted}
            cameraOff={callState.cameraOff}
            isTtsSpeaking={isTtsSpeaking}
            size="pip"
          />
        </div>

        {/* ── Top bar: network info + duration ── */}
        <div style={{
          position: 'absolute',
          top: 0,
          left: 0,
          right: 0,
          padding: '16px 20px',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent 100%)',
          pointerEvents: 'none',
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div style={{
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              background: 'rgba(0,0,0,0.5)',
              backdropFilter: 'blur(8px)',
              padding: '5px 12px',
              borderRadius: '20px',
              border: '1px solid rgba(255,255,255,0.08)',
            }}>
              <div style={{
                width: '7px', height: '7px', borderRadius: '50%',
                background: '#10B981',
                animation: 'stt-blink 3s infinite',
                boxShadow: '0 0 5px #10B981',
              }} />
              <span style={{ fontSize: '12px', color: '#9CA3AF', fontFamily: 'var(--font-mono)' }}>
                LXST · RETICULUM · 2 hops
              </span>
            </div>
          </div>

          <div style={{
            background: 'rgba(0,0,0,0.5)',
            backdropFilter: 'blur(8px)',
            padding: '5px 12px',
            borderRadius: '20px',
            border: '1px solid rgba(255,255,255,0.08)',
            fontFamily: 'var(--font-mono)',
            fontSize: '14px',
            color: '#fff',
            fontWeight: 500,
          }}>
            {formatDuration(duration)}
          </div>
        </div>

        {/* ── Bottom: captions + control bar ── */}
        <div style={{
          position: 'absolute',
          bottom: 0,
          left: 0,
          right: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: '16px',
          padding: '0 0 24px',
          background: 'linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent 80%)',
        }}>
          {/* Captions */}
          {callState.captionsEnabled && (
            <LiveCaptionBar
              sttState={sttState}
              speakerName={contact.name}
              speakerLanguage={contact.language}
              detectedLanguage={detectedLanguage}
              captionText={captionText}
              translatedText={translatedText}
              translationEnabled={callState.translationEnabled}
              myLanguage={translateTo}
            />
          )}

          {/* Control bar */}
          <ControlBar
            muted={callState.muted}
            cameraOff={callState.cameraOff}
            captionsEnabled={callState.captionsEnabled}
            translationEnabled={callState.translationEnabled}
            chatOpen={chatOpen}
            onMute={onMute}
            onCamera={onCamera}
            onCaptions={onCaptions}
            onTranslation={onTranslation}
            onChat={() => setChatOpen(o => !o)}
            onEnd={onEnd}
            onLanguageSettings={() => setLangSelectorOpen(true)}
          />
        </div>
      </div>

      {/* ── Activity sidebar: right edge ── */}
      <div style={{
        width: '48px',
        background: 'rgba(0,0,0,0.6)',
        backdropFilter: 'blur(12px)',
        borderLeft: '1px solid rgba(255,255,255,0.06)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        paddingTop: '80px',
        gap: '8px',
      }}>
        <ActivityBtn onClick={() => setChatOpen(o => !o)} active={chatOpen} label="Chat">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <path d="M21 15a2 2 0 01-2 2H7l-4 4V5a2 2 0 012-2h14a2 2 0 012 2z" />
          </svg>
        </ActivityBtn>
        <ActivityBtn onClick={() => setLangSelectorOpen(true)} active={false} label="Language">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <circle cx="12" cy="12" r="10" />
            <line x1="2" y1="12" x2="22" y2="12" />
            <path d="M12 2a15.3 15.3 0 014 10 15.3 15.3 0 01-4 10 15.3 15.3 0 01-4-10 15.3 15.3 0 014-10z" />
          </svg>
        </ActivityBtn>
      </div>

      {/* ── Chat sidebar overlay ── */}
      {chatOpen && <ChatSidebar contact={contact} onClose={() => setChatOpen(false)} />}

      {/* ── Language selector modal ── */}
      {langSelectorOpen && (
        <LanguageSelector
          myLanguage={MY_LANGUAGE}
          speakingLanguage={speakingLang}
          translateTo={translateTo}
          ttsEnabled={ttsEnabled}
          onSave={(sp, tr, t) => {
            setSpeakingLang(sp)
            setTranslateTo(tr)
            setTtsEnabled(t)
          }}
          onClose={() => setLangSelectorOpen(false)}
        />
      )}
    </div>
  )
}

function ActivityBtn({ onClick, active, label, children }: {
  onClick: () => void; active: boolean; label: string; children: React.ReactNode
}) {
  return (
    <button
      title={label}
      onClick={onClick}
      style={{
        width: '36px', height: '36px',
        borderRadius: '9px',
        border: 'none',
        background: active ? 'rgba(37,99,235,0.2)' : 'transparent',
        color: active ? '#60A5FA' : '#6B7280',
        cursor: 'pointer',
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        transition: 'all 0.15s',
      }}
    >
      {children}
    </button>
  )
}

function ChatSidebar({ contact, onClose }: { contact: { name: string }; onClose: () => void }) {
  const [msg, setMsg] = useState('')
  const [msgs, setMsgs] = useState([
    { id: 1, out: false, text: "Can you see my video okay?" },
    { id: 2, out: true, text: "Yes, clear! And your captions are working perfectly." },
  ])

  return (
    <div style={{
      position: 'absolute',
      top: 0, right: 48, bottom: 0,
      width: '300px',
      background: 'rgba(15,15,15,0.95)',
      backdropFilter: 'blur(16px)',
      borderLeft: '1px solid rgba(255,255,255,0.08)',
      display: 'flex',
      flexDirection: 'column',
      animation: 'slide-up 0.2s ease',
      zIndex: 20,
    }}>
      {/* header */}
      <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
        <span style={{ fontWeight: 600, fontSize: '14px' }}>In-call chat</span>
        <button onClick={onClose} style={{ background: 'none', border: 'none', color: '#6B7280', cursor: 'pointer', fontSize: '18px' }}>×</button>
      </div>

      {/* messages */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {msgs.map(m => (
          <div key={m.id} style={{
            alignSelf: m.out ? 'flex-end' : 'flex-start',
            background: m.out ? '#2563EB' : 'rgba(255,255,255,0.08)',
            color: '#fff',
            padding: '8px 12px',
            borderRadius: m.out ? '12px 12px 3px 12px' : '12px 12px 12px 3px',
            fontSize: '13px',
            maxWidth: '80%',
            lineHeight: 1.5,
          }}>
            {m.text}
          </div>
        ))}
      </div>

      {/* input */}
      <div style={{ padding: '12px', borderTop: '1px solid rgba(255,255,255,0.08)', display: 'flex', gap: '8px' }}>
        <input
          value={msg}
          onChange={e => setMsg(e.target.value)}
          onKeyDown={e => {
            if (e.key === 'Enter' && msg.trim()) {
              setMsgs(prev => [...prev, { id: Date.now(), out: true, text: msg.trim() }])
              setMsg('')
            }
          }}
          placeholder={`Message ${contact.name}…`}
          style={{
            flex: 1,
            background: 'rgba(255,255,255,0.06)',
            border: '1px solid rgba(255,255,255,0.1)',
            borderRadius: '8px',
            color: '#fff',
            padding: '8px 12px',
            fontSize: '13px',
            fontFamily: 'var(--font-sans)',
            outline: 'none',
          }}
        />
      </div>
    </div>
  )
}

