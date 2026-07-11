import { useState } from 'react'
import type { Language } from '../data'
import { LANGUAGES } from '../data'
import { Avatar } from '../App'

const MY_ADDR = 'f4a7b2c9e103d845f7a4b2c9e103d845'

export default function SettingsPanel({ myLanguage }: { myLanguage: Language }) {
  const [selectedLang, setSelectedLang] = useState(myLanguage)
  const [sttEnabled, setSttEnabled] = useState(true)
  const [ttsEnabled, setTtsEnabled] = useState(true)
  const [autoTranslate, setAutoTranslate] = useState(true)
  const [captionsInCall, setCaptionsInCall] = useState(true)
  const [displayName, setDisplayName] = useState('You')

  return (
    <div style={{ flex: 1, overflowY: 'auto', padding: '28px 32px', display: 'flex', flexDirection: 'column', gap: '32px', maxWidth: '640px' }}>
      <div>
        <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: 'var(--muted-foreground)', letterSpacing: '0.1em', marginBottom: '6px' }}>
          SHANNON
        </div>
        <h2 style={{ fontSize: '22px', fontWeight: 600, letterSpacing: '-0.02em', marginBottom: '4px' }}>Settings</h2>
        <p style={{ fontSize: '13px', color: 'var(--muted-foreground)' }}>
          Decentralized mesh messaging and voice · Powered by Reticulum
        </p>
      </div>

      {/* Identity */}
      <Section title="IDENTITY">
        <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '16px' }}>
          <Avatar name={displayName} size={52} />
          <div>
            <input
              value={displayName}
              onChange={e => setDisplayName(e.target.value)}
              style={{
                background: 'var(--secondary)',
                border: '1px solid var(--border)',
                borderRadius: '7px',
                color: 'var(--foreground)',
                fontSize: '14px',
                fontWeight: 600,
                padding: '7px 12px',
                fontFamily: 'var(--font-sans)',
                outline: 'none',
                marginBottom: '6px',
                width: '200px',
                display: 'block',
              }}
            />
            <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: 'var(--muted-foreground)', wordBreak: 'break-all' }}>
              {MY_ADDR}
            </div>
          </div>
        </div>
        <InfoRow label="Protocol" value="LXMF over Reticulum" />
        <InfoRow label="Voice" value="LXST (Reticulum Streaming)" />
        <InfoRow label="Encryption" value="AES-128-CBC + ECDH" />
      </Section>

      {/* Language */}
      <Section title="MY LANGUAGE">
        <p style={{ fontSize: '13px', color: 'var(--muted-foreground)', marginBottom: '14px' }}>
          Incoming messages and voice calls will be translated to this language on-device.
        </p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
          {LANGUAGES.map(lang => (
            <button
              key={lang.code}
              onClick={() => setSelectedLang(lang)}
              style={{
                padding: '7px 14px',
                borderRadius: '8px',
                border: `1.5px solid ${selectedLang.code === lang.code ? 'var(--primary)' : 'var(--border)'}`,
                background: selectedLang.code === lang.code ? 'rgba(0,212,168,0.1)' : 'var(--card)',
                color: selectedLang.code === lang.code ? 'var(--primary)' : 'var(--foreground)',
                cursor: 'pointer',
                fontSize: '13px',
                fontWeight: 500,
                display: 'flex',
                alignItems: 'center',
                gap: '6px',
                transition: 'all 0.15s',
              }}
            >
              <span>{lang.flag}</span>
              <span>{lang.name}</span>
            </button>
          ))}
        </div>
      </Section>

      {/* AI features */}
      <Section title="SPEECH & TRANSLATION">
        <p style={{ fontSize: '13px', color: 'var(--muted-foreground)', marginBottom: '16px' }}>
          All processing runs fully on-device. No data leaves your device or the Reticulum network.
        </p>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <Toggle
            label="Speech-to-Text (Live Captions)"
            description="Transcribes voice calls in real-time using on-device STT model"
            value={sttEnabled}
            onChange={setSttEnabled}
          />
          <Toggle
            label="Text-to-Speech (Synthesized Voice)"
            description="Reads translated messages aloud using on-device TTS engine"
            value={ttsEnabled}
            onChange={setTtsEnabled}
          />
          <Toggle
            label="Auto-translate incoming messages"
            description="Translate LXMF messages from other languages automatically"
            value={autoTranslate}
            onChange={setAutoTranslate}
          />
          <Toggle
            label="Show live captions during calls"
            description="Display real-time transcription overlay during LXST voice calls"
            value={captionsInCall}
            onChange={setCaptionsInCall}
          />
        </div>
      </Section>

      {/* About */}
      <Section title="ABOUT">
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <InfoRow label="Version" value="0.4.2-alpha" />
          <InfoRow label="Network" value="Reticulum v0.8.1" />
          <InfoRow label="Named for" value="Claude Shannon · Information Theory" />
          <InfoRow label="License" value="MIT Open Source" />
        </div>
      </Section>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{
        fontFamily: 'var(--font-mono)',
        fontSize: '10px',
        color: 'var(--muted-foreground)',
        letterSpacing: '0.1em',
        marginBottom: '14px',
        paddingBottom: '8px',
        borderBottom: '1px solid var(--border)',
      }}>
        {title}
      </div>
      {children}
    </div>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--border)', gap: '16px' }}>
      <span style={{ fontSize: '13px', color: 'var(--muted-foreground)' }}>{label}</span>
      <span style={{ fontSize: '13px', fontFamily: 'var(--font-mono)', color: 'var(--foreground)' }}>{value}</span>
    </div>
  )
}

function Toggle({ label, description, value, onChange }: {
  label: string
  description: string
  value: boolean
  onChange: (v: boolean) => void
}) {
  return (
    <div style={{
      display: 'flex',
      alignItems: 'flex-start',
      justifyContent: 'space-between',
      gap: '16px',
      padding: '14px 16px',
      background: 'var(--card)',
      border: '1px solid var(--border)',
      borderRadius: 'var(--radius)',
    }}>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: '14px', fontWeight: 500, marginBottom: '3px' }}>{label}</div>
        <div style={{ fontSize: '12px', color: 'var(--muted-foreground)', lineHeight: 1.4 }}>{description}</div>
      </div>
      <button
        onClick={() => onChange(!value)}
        style={{
          width: '44px',
          height: '24px',
          borderRadius: '12px',
          border: 'none',
          background: value ? 'var(--primary)' : 'var(--muted)',
          cursor: 'pointer',
          position: 'relative',
          flexShrink: 0,
          transition: 'background 0.2s',
        }}
      >
        <div style={{
          position: 'absolute',
          top: '3px',
          left: value ? '23px' : '3px',
          width: '18px',
          height: '18px',
          borderRadius: '50%',
          background: '#fff',
          transition: 'left 0.2s',
          boxShadow: '0 1px 3px rgba(0,0,0,0.3)',
        }} />
      </button>
    </div>
  )
}
