import { useState } from 'react'
import { useBackend } from './bridge/useBackend'
import { CONTACTS, MESSAGES, LANGUAGES, type Contact, type Message, type CallState } from './data'
import Sidebar from './components/Sidebar'
import ConversationList from './components/ConversationList'
import ChatArea from './components/ChatArea'
import CallViewDesktop from './components/CallViewDesktop'
import CallViewMobile from './components/CallViewMobile'
import NetworkPanel from './components/NetworkPanel'
import SettingsPanel from './components/SettingsPanel'

export type View = 'messages' | 'calls' | 'network' | 'settings'
export type DeviceMode = 'desktop' | 'mobile'

const MY_LANGUAGE = LANGUAGES.find(l => l.code === 'en')!

export default function App() {
  const [view, setView] = useState<View>('messages')
  const [deviceMode, setDeviceMode] = useState<DeviceMode>('desktop')
  const [activeContact, setActiveContact] = useState<Contact | null>(CONTACTS[0])
  const [messages, setMessages] = useState(MESSAGES)
  const [callState, setCallState] = useState<CallState>({
    active: false, contact: null, duration: 0,
    captionsEnabled: true, translationEnabled: true,
    ttsEnabled: true, liveCaption: '', muted: false,
    cameraOff: false, sttState: 'idle', detectedLanguage: null,
    speakingLevel: 0,
  })

  const { backend, connectionStatus } = useBackend()

  function sendMessage(contactId: string, content: string) {
    const msg: Message = {
      id: `m-${Date.now()}`, contactId, direction: 'out', content,
      translated: false, timestamp: new Date(),
      status: 'sending', lxmfHops: Math.floor(Math.random() * 4) + 1,
    }
    setMessages(prev => ({ ...prev, [contactId]: [...(prev[contactId] ?? []), msg] }))
    setTimeout(() => {
      setMessages(prev => ({
        ...prev,
        [contactId]: prev[contactId].map(m => m.id === msg.id ? { ...m, status: 'delivered' } : m),
      }))
    }, 1200)

    // Hybrid wiring (§3.3): fire the real bridge send alongside the optimistic local update.
    const contact = CONTACTS.find(c => c.id === contactId)
    if (contact) {
      backend?.send({ method: 'message.send', params: { destinationHash: contact.lxmfAddress, content } }).catch(() => {})
    }
  }

  function startCall(contact: Contact) {
    setCallState(prev => ({ ...prev, active: true, contact, sttState: 'listening' }))
  }

  function endCall() {
    setCallState(prev => ({ ...prev, active: false, contact: null, sttState: 'idle' }))
  }

  const callHandlers = {
    onEnd: endCall,
    onMute: () => setCallState(prev => ({ ...prev, muted: !prev.muted })),
    onCamera: () => setCallState(prev => ({ ...prev, cameraOff: !prev.cameraOff })),
    onCaptions: () => setCallState(prev => {
      const next = !prev.captionsEnabled
      backend?.send({ method: 'captions.setEnabled', params: { enabled: next } }).catch(() => {})
      return { ...prev, captionsEnabled: next }
    }),
    onTranslation: () => setCallState(prev => {
      const next = !prev.translationEnabled
      backend?.send({ method: 'captions.setSpeakTranslations', params: { enabled: next } }).catch(() => {})
      return { ...prev, translationEnabled: next }
    }),
  }

  const isMobile = deviceMode === 'mobile'

  // Mobile preview wrapper
  const MobileFrame = ({ children }: { children: React.ReactNode }) => (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      flex: 1,
      background: '#050505',
      padding: '24px',
    }}>
      <div style={{
        width: '390px',
        height: '844px',
        maxHeight: 'calc(100vh - 80px)',
        background: '#0F0F0F',
        borderRadius: '44px',
        overflow: 'hidden',
        boxShadow: '0 0 0 8px #1a1a1a, 0 0 0 10px #000, 0 32px 80px rgba(0,0,0,0.8)',
        position: 'relative',
        border: '1px solid rgba(255,255,255,0.1)',
        flexShrink: 0,
      }}>
        {children}
      </div>
    </div>
  )

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
      {/* ── Top chrome: device switcher + brand ── */}
      <div style={{
        height: '44px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '0 16px',
        background: '#0A0A0A',
        borderBottom: '1px solid rgba(255,255,255,0.06)',
        flexShrink: 0,
        zIndex: 50,
      }}>
        {/* Brand */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <ShannonMark />
          <span style={{ fontSize: '14px', fontWeight: 700, letterSpacing: '-0.01em' }}>Shannon</span>
          <span style={{ fontSize: '11px', color: '#4B5563', fontFamily: 'var(--font-mono)', marginLeft: '4px' }}>
            v0.4.2-alpha · Reticulum
          </span>
        </div>

        {/* Device mode toggle */}
        <div style={{
          display: 'flex',
          background: 'rgba(255,255,255,0.06)',
          borderRadius: '8px',
          padding: '3px',
          gap: '2px',
        }}>
          {(['desktop', 'mobile'] as DeviceMode[]).map(mode => (
            <button
              key={mode}
              onClick={() => setDeviceMode(mode)}
              style={{
                padding: '4px 14px',
                borderRadius: '6px',
                border: 'none',
                background: deviceMode === mode ? 'rgba(37,99,235,0.25)' : 'transparent',
                color: deviceMode === mode ? '#60A5FA' : '#6B7280',
                cursor: 'pointer',
                fontSize: '12px',
                fontWeight: 600,
                display: 'flex',
                alignItems: 'center',
                gap: '5px',
                transition: 'all 0.15s',
              }}
            >
              {mode === 'desktop' ? <MonitorIcon /> : <PhoneIcon />}
              {mode === 'desktop' ? 'Desktop' : 'Mobile'}
            </button>
          ))}
        </div>

        {/* Network badge */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: '12px', color: '#6B7280', fontFamily: 'var(--font-mono)' }}>
          <div style={{ width: '6px', height: '6px', borderRadius: '50%', background: connectionStatus === 'CONNECTED' ? '#10B981' : '#6B7280', boxShadow: connectionStatus === 'CONNECTED' ? '0 0 4px #10B981' : 'none' }} />
          RETICULUM · {connectionStatus}
        </div>
      </div>

      {/* ── Main content ── */}
      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {isMobile ? (
          <MobileFrame>
            <MobileChatView
              contacts={CONTACTS}
              messages={messages}
              activeContact={activeContact}
              onSelect={setActiveContact}
              onSend={sendMessage}
              onCall={startCall}
              myLanguage={MY_LANGUAGE}
              view={view}
              onViewChange={setView}
            />
          </MobileFrame>
        ) : (
          <>
            <Sidebar view={view} onViewChange={setView} />
            <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
              {view === 'messages' && (
                <>
                  <ConversationList
                    contacts={CONTACTS}
                    messages={messages}
                    activeContact={activeContact}
                    onSelect={setActiveContact}
                  />
                  {activeContact ? (
                    <ChatArea
                      contact={activeContact}
                      messages={messages[activeContact.id] ?? []}
                      myLanguage={MY_LANGUAGE}
                      onSend={(c) => sendMessage(activeContact.id, c)}
                      onCall={() => startCall(activeContact)}
                    />
                  ) : <EmptyState />}
                </>
              )}
              {view === 'calls' && (
                <CallsGrid contacts={CONTACTS} onCall={startCall} />
              )}
              {view === 'network' && <NetworkPanel />}
              {view === 'settings' && <SettingsPanel myLanguage={MY_LANGUAGE} />}
            </div>
          </>
        )}
      </div>

      {/* ── Active call overlays ── */}
      {callState.active && callState.contact && (
        deviceMode === 'desktop' ? (
          <CallViewDesktop callState={callState} backend={backend} {...callHandlers} />
        ) : (
          <CallViewMobile callState={callState} backend={backend} {...callHandlers} />
        )
      )}
    </div>
  )
}

/* ── Mobile chat view (condensed, no sidebar) ── */
function MobileChatView({ contacts, messages, activeContact, onSelect, onSend, onCall, myLanguage: _myLanguage, view, onViewChange }: {
  contacts: Contact[]
  messages: Record<string, Message[]>
  activeContact: Contact | null
  onSelect: (c: Contact) => void
  onSend: (id: string, content: string) => void
  onCall: (c: Contact) => void
  myLanguage: typeof MY_LANGUAGE
  view: View
  onViewChange: (v: View) => void
}) {
  const [chatOpen, setChatOpen] = useState(false)
  const activeMsg = activeContact ? messages[activeContact.id] ?? [] : []

  if (chatOpen && activeContact) {
    return (
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
        <div style={{ padding: '12px 16px', borderBottom: '1px solid rgba(255,255,255,0.08)', display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button onClick={() => setChatOpen(false)} style={{ background: 'none', border: 'none', color: '#9CA3AF', cursor: 'pointer', fontSize: '18px' }}>←</button>
          <span style={{ fontWeight: 600, fontSize: '15px' }}>{activeContact.name}</span>
          <button onClick={() => onCall(activeContact)} style={{ marginLeft: 'auto', background: '#2563EB', border: 'none', borderRadius: '50%', width: '32px', height: '32px', color: '#fff', cursor: 'pointer', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <PhoneIcon />
          </button>
        </div>
        <div style={{ flex: 1, overflowY: 'auto', padding: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {activeMsg.map(m => (
            <div key={m.id} style={{
              alignSelf: m.direction === 'out' ? 'flex-end' : 'flex-start',
              maxWidth: '80%',
            }}>
              {m.translated && <div style={{ fontSize: '10px', color: '#7C3AED', marginBottom: '2px' }}>🌐 {m.originalLanguage?.flag}→🇬🇧</div>}
              <div style={{
                padding: '9px 13px',
                borderRadius: m.direction === 'out' ? '14px 14px 3px 14px' : '14px 14px 14px 3px',
                background: m.direction === 'out' ? '#2563EB' : 'rgba(255,255,255,0.08)',
                color: '#fff',
                fontSize: '14px',
                lineHeight: 1.5,
              }}>
                {m.content}
              </div>
            </div>
          ))}
        </div>
        <div style={{ padding: '10px', borderTop: '1px solid rgba(255,255,255,0.08)', display: 'flex', gap: '8px' }}>
          <input
            placeholder="Message…"
            style={{ flex: 1, background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.1)', borderRadius: '20px', color: '#fff', padding: '9px 14px', fontSize: '14px', fontFamily: 'var(--font-sans)', outline: 'none' }}
            onKeyDown={e => {
              if (e.key === 'Enter') {
                onSend(activeContact.id, (e.target as HTMLInputElement).value)
                ;(e.target as HTMLInputElement).value = ''
              }
            }}
          />
        </div>
      </div>
    )
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Header */}
      <div style={{ padding: '16px', borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
        <div style={{ fontWeight: 700, fontSize: '20px', marginBottom: '10px' }}>Messages</div>
        <input placeholder="Search…" style={{ width: '100%', background: 'rgba(255,255,255,0.06)', border: '1px solid rgba(255,255,255,0.08)', borderRadius: '10px', color: '#fff', padding: '9px 14px', fontSize: '13px', fontFamily: 'var(--font-sans)', outline: 'none' }} />
      </div>

      {/* Contact list */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {contacts.map(c => {
          const last = (messages[c.id] ?? []).slice(-1)[0]
          return (
            <button
              key={c.id}
              onClick={() => { onSelect(c); setChatOpen(true) }}
              style={{
                width: '100%', padding: '14px 16px',
                background: 'transparent', border: 'none', borderBottom: '1px solid rgba(255,255,255,0.05)',
                display: 'flex', gap: '12px', alignItems: 'flex-start', cursor: 'pointer', textAlign: 'left',
              }}
            >
              <div style={{ position: 'relative', flexShrink: 0 }}>
                <img src={c.photo} alt={c.name} style={{ width: 48, height: 48, borderRadius: '50%', objectFit: 'cover', background: '#1E1E1E' }} />
                <div style={{ position: 'absolute', bottom: 0, right: 0, width: 11, height: 11, borderRadius: '50%', background: c.online ? '#10B981' : '#4B5563', border: '2px solid #0F0F0F' }} />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontWeight: 600, fontSize: '15px', color: '#fff', marginBottom: '2px' }}>{c.name}</div>
                <div style={{ fontSize: '13px', color: '#6B7280', display: 'flex', gap: '4px', alignItems: 'center' }}>
                  <span>{c.language.flag}</span>
                  <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{last?.content ?? 'No messages'}</span>
                </div>
              </div>
            </button>
          )
        })}
      </div>

      {/* Bottom tab bar */}
      <div style={{ display: 'flex', borderTop: '1px solid rgba(255,255,255,0.08)', background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(12px)' }}>
        {([
          { v: 'messages', label: 'Chats', icon: '💬' },
          { v: 'calls', label: 'Calls', icon: '📞' },
          { v: 'network', label: 'Network', icon: '🌐' },
          { v: 'settings', label: 'Settings', icon: '⚙️' },
        ] as { v: View; label: string; icon: string }[]).map(tab => (
          <button
            key={tab.v}
            onClick={() => onViewChange(tab.v)}
            style={{
              flex: 1, padding: '10px 4px 6px',
              background: 'none', border: 'none', cursor: 'pointer',
              display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '2px',
              color: view === tab.v ? '#60A5FA' : '#6B7280',
              fontSize: '18px',
            }}
          >
            <span>{tab.icon}</span>
            <span style={{ fontSize: '10px', fontWeight: 600 }}>{tab.label}</span>
          </button>
        ))}
      </div>
    </div>
  )
}

/* ── Calls grid ── */
function CallsGrid({ contacts, onCall }: { contacts: Contact[]; onCall: (c: Contact) => void }) {
  return (
    <div style={{ flex: 1, padding: '28px 32px', overflowY: 'auto' }}>
      <div style={{ fontFamily: 'var(--font-mono)', fontSize: '11px', color: '#6B7280', letterSpacing: '0.1em', marginBottom: '20px' }}>
        LXST VOICE — AVAILABLE CONTACTS
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(220px, 1fr))', gap: '16px' }}>
        {contacts.map(c => (
          <div key={c.id} style={{
            background: '#1E1E1E',
            border: '1px solid rgba(255,255,255,0.08)',
            borderRadius: '12px',
            overflow: 'hidden',
          }}>
            <div style={{ height: '120px', position: 'relative', overflow: 'hidden', background: '#111' }}>
              <img src={c.photo} alt={c.name} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
              <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(to top, rgba(0,0,0,0.7) 0%, transparent)' }} />
              <div style={{ position: 'absolute', bottom: '8px', left: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: c.online ? '#10B981' : '#4B5563', boxShadow: c.online ? '0 0 4px #10B981' : 'none' }} />
                <span style={{ fontSize: '12px', color: '#fff', fontWeight: 600 }}>{c.online ? 'Online' : c.lastSeen}</span>
              </div>
            </div>
            <div style={{ padding: '12px 14px' }}>
              <div style={{ fontWeight: 600, fontSize: '14px', marginBottom: '2px' }}>{c.name}</div>
              <div style={{ fontSize: '12px', color: '#6B7280', marginBottom: '12px' }}>{c.language.flag} {c.language.name}</div>
              <button
                onClick={() => onCall(c)}
                disabled={!c.online}
                style={{
                  width: '100%', padding: '9px',
                  background: c.online ? '#2563EB' : 'rgba(255,255,255,0.05)',
                  color: c.online ? '#fff' : '#4B5563',
                  border: 'none', borderRadius: '8px',
                  cursor: c.online ? 'pointer' : 'not-allowed',
                  fontWeight: 600, fontSize: '13px', fontFamily: 'var(--font-sans)',
                  transition: 'background 0.15s',
                  display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px',
                }}
              >
                <PhoneIcon />
                {c.online ? 'Call via LXST' : 'Offline'}
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

function EmptyState() {
  return (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '12px', color: '#4B5563' }}>
      <ShannonMark size={48} />
      <p style={{ fontFamily: 'var(--font-mono)', fontSize: '12px', letterSpacing: '0.1em' }}>SELECT A CONVERSATION</p>
    </div>
  )
}

/* ── Shared icons & mark ── */
export function Avatar({ name, size = 36 }: { name: string; size?: number }) {
  const initials = name.split(' ').map(n => n[0]).join('').slice(0, 2).toUpperCase()
  const hue = name.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0) % 360
  return (
    <div style={{
      width: size, height: size, borderRadius: '50%',
      background: `hsl(${hue}, 40%, 25%)`,
      display: 'flex', alignItems: 'center', justifyContent: 'center',
      fontSize: size * 0.36, fontWeight: 700,
      color: `hsl(${hue}, 60%, 75%)`,
      flexShrink: 0, fontFamily: 'var(--font-sans)',
    }}>
      {initials}
    </div>
  )
}

export function PhoneIcon({ size = 14 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M22 16.92v3a2 2 0 01-2.18 2 19.79 19.79 0 01-8.63-3.07A19.5 19.5 0 014.69 11.5 19.79 19.79 0 011.61 2.84 2 2 0 013.61 1h3a2 2 0 012 1.72c.127.96.361 1.903.7 2.81a2 2 0 01-.45 2.11L7.91 8.64a16 16 0 006.29 6.29l.95-.95a2 2 0 012.11-.45c.907.339 1.85.573 2.81.7A2 2 0 0122 16.92z" />
    </svg>
  )
}

function MonitorIcon() {
  return (
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <rect x="2" y="3" width="20" height="14" rx="2" />
      <line x1="8" y1="21" x2="16" y2="21" />
      <line x1="12" y1="17" x2="12" y2="21" />
    </svg>
  )
}

function ShannonMark({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 32 32" fill="none">
      <circle cx="16" cy="16" r="14" stroke="rgba(255,255,255,0.12)" strokeWidth="1" />
      <circle cx="16" cy="16" r="2.5" fill="#2563EB" />
      <circle cx="6" cy="10" r="1.5" fill="#2563EB" opacity="0.5" />
      <circle cx="26" cy="10" r="1.5" fill="#2563EB" opacity="0.5" />
      <circle cx="10" cy="26" r="1.5" fill="#2563EB" opacity="0.5" />
      <circle cx="22" cy="26" r="1.5" fill="#2563EB" opacity="0.5" />
      <line x1="16" y1="16" x2="6" y2="10" stroke="#2563EB" strokeWidth="0.8" opacity="0.6" />
      <line x1="16" y1="16" x2="26" y2="10" stroke="#2563EB" strokeWidth="0.8" opacity="0.6" />
      <line x1="16" y1="16" x2="10" y2="26" stroke="#2563EB" strokeWidth="0.8" opacity="0.6" />
      <line x1="16" y1="16" x2="22" y2="26" stroke="#2563EB" strokeWidth="0.8" opacity="0.6" />
    </svg>
  )
}
