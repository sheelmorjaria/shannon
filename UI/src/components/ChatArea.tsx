import { useState, useRef, useEffect } from 'react'
import type { Contact, Language, Message } from '../data'
import { Avatar, PhoneIcon } from '../App'

function formatTimestamp(date: Date) {
  return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

export default function ChatArea({
  contact,
  messages,
  myLanguage,
  onSend,
  onCall,
}: {
  contact: Contact
  messages: Message[]
  myLanguage: Language
  onSend: (content: string) => void
  onCall: () => void
}) {
  const [input, setInput] = useState('')
  const [showOriginal, setShowOriginal] = useState<string | null>(null)
  const [translationOn, setTranslationOn] = useState(true)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [messages.length])

  function handleSend() {
    const trimmed = input.trim()
    if (!trimmed) return
    onSend(trimmed)
    setInput('')
  }

  function handleKeyDown(e: React.KeyboardEvent) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden', minWidth: 0 }}>
      {/* Header */}
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '14px 20px',
        borderBottom: '1px solid var(--border)',
        background: 'var(--card)',
        flexShrink: 0,
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
          <div style={{ position: 'relative' }}>
            <Avatar name={contact.name} size={38} />
            <div style={{
              position: 'absolute', bottom: 0, right: 0,
              width: '9px', height: '9px', borderRadius: '50%',
              background: contact.online ? 'var(--primary)' : 'var(--muted-foreground)',
              border: '2px solid var(--card)',
              boxShadow: contact.online ? '0 0 4px var(--primary)' : 'none',
            }} />
          </div>
          <div>
            <div style={{ fontWeight: 600, fontSize: '15px', display: 'flex', alignItems: 'center', gap: '8px' }}>
              {contact.name}
              <span style={{
                fontSize: '11px',
                fontWeight: 500,
                padding: '2px 7px',
                borderRadius: '4px',
                background: 'rgba(124,111,205,0.15)',
                color: 'var(--accent)',
                fontFamily: 'var(--font-mono)',
              }}>
                {contact.language.flag} {contact.language.code.toUpperCase()}
              </span>
            </div>
            <div style={{ fontSize: '11px', color: 'var(--muted-foreground)', fontFamily: 'var(--font-mono)', marginTop: '1px' }}>
              {contact.lxmfAddress.slice(0, 8)}…{contact.lxmfAddress.slice(-6)} · LXMF
            </div>
          </div>
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Translation toggle */}
          <button
            onClick={() => setTranslationOn(t => !t)}
            title={translationOn ? 'Translation on' : 'Translation off'}
            style={{
              padding: '6px 12px',
              borderRadius: '7px',
              border: '1px solid var(--border)',
              background: translationOn ? 'rgba(124,111,205,0.15)' : 'transparent',
              color: translationOn ? 'var(--accent)' : 'var(--muted-foreground)',
              cursor: 'pointer',
              fontSize: '12px',
              fontFamily: 'var(--font-sans)',
              fontWeight: 500,
              display: 'flex',
              alignItems: 'center',
              gap: '6px',
              transition: 'all 0.15s',
            }}
          >
            <span style={{ fontSize: '14px' }}>🌐</span>
            {translationOn ? `${contact.language.flag}→${myLanguage.flag}` : 'Translate'}
          </button>

          {/* Call button */}
          <button
            onClick={onCall}
            disabled={!contact.online}
            title="LXST Voice Call"
            style={{
              width: '36px', height: '36px',
              borderRadius: '50%',
              border: 'none',
              background: contact.online ? 'var(--primary)' : 'var(--muted)',
              color: contact.online ? 'var(--primary-foreground)' : 'var(--muted-foreground)',
              cursor: contact.online ? 'pointer' : 'not-allowed',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              transition: 'opacity 0.15s',
            }}
          >
            <PhoneIcon size={14} />
          </button>
        </div>
      </div>

      {/* Messages */}
      <div style={{
        flex: 1,
        overflowY: 'auto',
        padding: '20px',
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
      }}>
        {messages.length === 0 && (
          <div style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '8px',
            color: 'var(--muted-foreground)',
            fontSize: '13px',
          }}>
            <span style={{ fontSize: '24px' }}>🔒</span>
            <span>End-to-end encrypted via Reticulum</span>
            <span style={{ fontFamily: 'var(--font-mono)', fontSize: '11px' }}>LXMF · {contact.language.flag} ↔ {myLanguage.flag}</span>
          </div>
        )}

        {messages.map((msg, i) => {
          const prev = messages[i - 1]
          const showAvatar = !prev || prev.direction !== msg.direction
          const isOut = msg.direction === 'out'
          const isShowingOriginal = showOriginal === msg.id

          return (
            <div key={msg.id} style={{
              display: 'flex',
              flexDirection: isOut ? 'row-reverse' : 'row',
              alignItems: 'flex-end',
              gap: '8px',
              marginTop: showAvatar ? '12px' : '2px',
            }}>
              {!isOut && (
                <div style={{ width: '28px', flexShrink: 0 }}>
                  {showAvatar && <Avatar name={contact.name} size={28} />}
                </div>
              )}

              <div style={{ maxWidth: '68%', display: 'flex', flexDirection: 'column', alignItems: isOut ? 'flex-end' : 'flex-start', gap: '4px' }}>
                {/* Translation badge */}
                {msg.translated && translationOn && (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '4px',
                      fontSize: '10px',
                      fontFamily: 'var(--font-mono)',
                      color: 'var(--accent)',
                      cursor: 'pointer',
                      userSelect: 'none',
                    }}
                    onClick={() => setShowOriginal(isShowingOriginal ? null : msg.id)}
                  >
                    <span>🌐</span>
                    <span>{msg.originalLanguage?.flag} → {myLanguage.flag}</span>
                    <span style={{ opacity: 0.7 }}>· {isShowingOriginal ? 'show translation' : 'show original'}</span>
                  </div>
                )}

                {/* Bubble */}
                <div style={{
                  padding: '9px 13px',
                  borderRadius: isOut
                    ? '14px 14px 4px 14px'
                    : '14px 14px 14px 4px',
                  background: isOut
                    ? 'var(--primary)'
                    : 'var(--card)',
                  color: isOut ? 'var(--primary-foreground)' : 'var(--foreground)',
                  fontSize: '14px',
                  lineHeight: 1.5,
                  border: isOut ? 'none' : '1px solid var(--border)',
                  position: 'relative',
                }}>
                  {isShowingOriginal && msg.originalContent ? msg.originalContent : msg.content}
                </div>

                {/* Meta row */}
                <div style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '6px',
                  fontSize: '10px',
                  color: 'var(--muted-foreground)',
                  fontFamily: 'var(--font-mono)',
                }}>
                  <span>{formatTimestamp(msg.timestamp)}</span>
                  {msg.lxmfHops !== undefined && (
                    <span title="Reticulum hops">·{msg.lxmfHops}🔗</span>
                  )}
                  {isOut && <StatusIcon status={msg.status} />}
                </div>
              </div>
            </div>
          )
        })}
        <div ref={bottomRef} />
      </div>

      {/* Input */}
      <div style={{
        padding: '12px 16px',
        borderTop: '1px solid var(--border)',
        background: 'var(--card)',
        display: 'flex',
        alignItems: 'flex-end',
        gap: '10px',
        flexShrink: 0,
      }}>
        {/* TTS hint */}
        <button
          title="Speak to send (TTS)"
          style={{
            width: '36px', height: '36px', flexShrink: 0,
            borderRadius: '50%',
            border: '1px solid var(--border)',
            background: 'transparent',
            color: 'var(--muted-foreground)',
            cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'all 0.15s',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLButtonElement).style.color = 'var(--primary)'
            ;(e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--primary)'
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLButtonElement).style.color = 'var(--muted-foreground)'
            ;(e.currentTarget as HTMLButtonElement).style.borderColor = 'var(--border)'
          }}
        >
          <MicIcon />
        </button>

        <div style={{ flex: 1, position: 'relative' }}>
          <textarea
            value={input}
            onChange={e => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={`Message ${contact.name}…`}
            rows={1}
            style={{
              width: '100%',
              padding: '9px 13px',
              background: 'var(--secondary)',
              border: '1px solid var(--border)',
              borderRadius: '10px',
              color: 'var(--foreground)',
              fontSize: '14px',
              fontFamily: 'var(--font-sans)',
              outline: 'none',
              resize: 'none',
              lineHeight: 1.5,
              maxHeight: '120px',
              overflowY: 'auto',
            }}
            onInput={e => {
              const t = e.currentTarget
              t.style.height = 'auto'
              t.style.height = Math.min(t.scrollHeight, 120) + 'px'
            }}
          />
        </div>

        <button
          onClick={handleSend}
          disabled={!input.trim()}
          style={{
            width: '36px', height: '36px', flexShrink: 0,
            borderRadius: '50%',
            border: 'none',
            background: input.trim() ? 'var(--primary)' : 'var(--muted)',
            color: input.trim() ? 'var(--primary-foreground)' : 'var(--muted-foreground)',
            cursor: input.trim() ? 'pointer' : 'not-allowed',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            transition: 'background 0.15s',
          }}
        >
          <SendIcon />
        </button>
      </div>

      {/* LXMF route info bar */}
      <div style={{
        padding: '6px 16px',
        background: 'var(--background)',
        borderTop: '1px solid var(--border)',
        display: 'flex',
        alignItems: 'center',
        gap: '12px',
        fontSize: '10px',
        fontFamily: 'var(--font-mono)',
        color: 'var(--muted-foreground)',
        flexShrink: 0,
      }}>
        <span style={{ color: 'var(--primary)', opacity: 0.8 }}>● RETICULUM</span>
        <span>LXMF · {contact.online ? 'DIRECT PATH' : 'NO PATH'}</span>
        <span>E2EE · AES-128</span>
        <div style={{ flex: 1 }} />
        <span>{myLanguage.flag} {myLanguage.code.toUpperCase()} ↔ {contact.language.flag} {contact.language.code.toUpperCase()}</span>
      </div>
    </div>
  )
}

function StatusIcon({ status }: { status: Message['status'] }) {
  if (status === 'sending') return <span style={{ opacity: 0.5 }}>○</span>
  if (status === 'delivered') return <span>✓</span>
  return <span style={{ color: 'var(--primary)' }}>✓✓</span>
}

function MicIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 1a3 3 0 00-3 3v8a3 3 0 006 0V4a3 3 0 00-3-3z" />
      <path d="M19 10v2a7 7 0 01-14 0v-2" />
      <line x1="12" y1="19" x2="12" y2="23" />
      <line x1="8" y1="23" x2="16" y2="23" />
    </svg>
  )
}

function SendIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
      <line x1="22" y1="2" x2="11" y2="13" />
      <polygon points="22 2 15 22 11 13 2 9 22 2" />
    </svg>
  )
}
