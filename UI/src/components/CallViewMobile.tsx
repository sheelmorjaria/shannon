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

type TranscriptEntry = {
  id: number
  speaker: string
  original: string
  translated: string
  lang: Language
}

export default function CallViewMobile({ callState, onEnd, onMute, onCamera, onCaptions, onTranslation }: Props) {
  const [duration, setDuration] = useState(0)
  const [langOpen, setLangOpen] = useState(false)
  const [chatOpen, setChatOpen] = useState(false)
  const [speakingLang] = useState(LANGUAGES.find(l => l.code === 'auto')!)
  const [translateTo] = useState(MY_LANGUAGE)
  const [ttsEnabled] = useState(true)
  const [isTtsSpeaking, setIsTtsSpeaking] = useState(false)
  const [sttState, setSttState] = useState<'idle' | 'listening' | 'processing' | 'showing'>('listening')
  const [captionText, setCaptionText] = useState('')
  const [translatedText, setTranslatedText] = useState('')
  const [detectedLanguage, setDetectedLanguage] = useState<Language | null>(null)
  const [isSpeaking, setIsSpeaking] = useState(true)
  const [transcript, setTranscript] = useState<TranscriptEntry[]>([])
  const captionIdx = useRef(0)
  const captionTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const transcriptRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const t = setInterval(() => setDuration(d => d + 1), 1000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    if (!callState.captionsEnabled) { setSttState('idle'); setCaptionText(''); setTranslatedText(''); return }

    function runCycle() {
      const sample = CAPTION_SAMPLES[captionIdx.current % CAPTION_SAMPLES.length]
      captionIdx.current++
      setSttState('listening')
      setCaptionText('')
      setTranslatedText('')
      setIsSpeaking(true)
      if (sample.lang.code !== 'en') {
        setDetectedLanguage(null)
        setTimeout(() => setDetectedLanguage(sample.lang), 400)
      }
      captionTimer.current = setTimeout(() => {
        setSttState('showing')
        let i = 0
        const typing = setInterval(() => {
          setCaptionText(sample.original.slice(0, i + 1))
          i++
          if (i >= sample.original.length) {
            clearInterval(typing)
            if (callState.translationEnabled) {
              setTimeout(() => {
                setTranslatedText(sample.translated)
                if (ttsEnabled) {
                  setIsTtsSpeaking(true)
                  setTimeout(() => setIsTtsSpeaking(false), 2500)
                }
              }, 150)
            }
            setIsSpeaking(false)
            // Add to transcript
            setTranscript(prev => [{
              id: Date.now(),
              speaker: callState.contact?.name ?? 'Speaker',
              original: sample.original,
              translated: sample.translated,
              lang: sample.lang,
            }, ...prev].slice(0, 20))
            captionTimer.current = setTimeout(runCycle, 4200)
          }
        }, 38)
      }, 300)
    }
    captionTimer.current = setTimeout(runCycle, 1000)
    return () => { if (captionTimer.current) clearTimeout(captionTimer.current) }
  }, [callState.captionsEnabled, callState.translationEnabled, ttsEnabled, callState.contact])

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
      flexDirection: 'column',
      animation: 'fade-in 0.3s ease',
    }}>
      {/* ── Full-screen main video ── */}
      <div style={{ flex: 1, position: 'relative', overflow: 'hidden' }}>
        <VideoTile contact={contact} isSpeaking={isSpeaking} size="main" />

        {/* PiP top-right */}
        <div style={{
          position: 'absolute',
          top: '16px',
          right: '16px',
          width: '100px',
          height: '150px',
          borderRadius: '10px',
          overflow: 'hidden',
          boxShadow: '0 4px 20px rgba(0,0,0,0.7)',
          zIndex: 10,
        }}>
          <VideoTile contact={null} isLocal isMuted={callState.muted} cameraOff={callState.cameraOff} isTtsSpeaking={isTtsSpeaking} size="pip" />
        </div>

        {/* Top bar */}
        <div style={{
          position: 'absolute',
          top: 0, left: 0, right: 0,
          padding: '14px 16px',
          background: 'linear-gradient(to bottom, rgba(0,0,0,0.7) 0%, transparent)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          pointerEvents: 'none',
        }}>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', color: '#9CA3AF', display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#10B981', display: 'inline-block', animation: 'stt-blink 2.5s infinite', boxShadow: '0 0 4px #10B981' }} />
            LXST
          </div>
          <div style={{ fontFamily: 'var(--font-mono)', fontSize: '13px', fontWeight: 600, color: '#fff' }}>
            {formatDuration(duration)}
          </div>
        </div>

        {/* Scrollable transcript — bottom third */}
        {chatOpen && (
          <div style={{
            position: 'absolute',
            bottom: 0,
            left: 0,
            right: 0,
            height: '40%',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column-reverse',
            padding: '12px',
            gap: '8px',
            background: 'linear-gradient(to top, rgba(0,0,0,0.85) 60%, transparent)',
          }} ref={transcriptRef}>
            {transcript.map(entry => (
              <div key={entry.id} style={{ animation: 'caption-in 0.25s ease' }}>
                <div style={{ fontSize: '11px', color: '#10B981', fontWeight: 700, marginBottom: '2px' }}>
                  {entry.speaker} {entry.lang.flag}
                </div>
                <div style={{ fontSize: '14px', color: '#fff', lineHeight: 1.4 }}>{entry.original}</div>
                {callState.translationEnabled && (
                  <div style={{ fontSize: '13px', color: '#9CA3AF', fontStyle: 'italic', marginTop: '2px' }}>
                    🇬🇧 {entry.translated}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </div>

      {/* ── Bottom: captions + control bar ── */}
      <div style={{
        flexShrink: 0,
        background: 'rgba(0,0,0,0.85)',
        backdropFilter: 'blur(12px)',
        borderTop: '1px solid rgba(255,255,255,0.06)',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        gap: '12px',
        padding: '12px 12px 20px',
      }}>
        {/* Transcript toggle */}
        <button
          onClick={() => setChatOpen(o => !o)}
          style={{
            background: 'none', border: 'none', color: '#6B7280',
            fontSize: '12px', cursor: 'pointer',
            display: 'flex', alignItems: 'center', gap: '4px',
          }}
        >
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <polyline points={chatOpen ? "18 15 12 9 6 15" : "6 9 12 15 18 9"} />
          </svg>
          {chatOpen ? 'Hide transcript' : 'Show transcript'}
        </button>

        {/* Captions */}
        {callState.captionsEnabled && (
          <div style={{ width: '100%' }}>
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
          </div>
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
          onLanguageSettings={() => setLangOpen(true)}
          compact
        />
      </div>

      {langOpen && (
        <LanguageSelector
          myLanguage={MY_LANGUAGE}
          speakingLanguage={speakingLang}
          translateTo={translateTo}
          ttsEnabled={ttsEnabled}
          onSave={() => {}}
          onClose={() => setLangOpen(false)}
        />
      )}
    </div>
  )
}
