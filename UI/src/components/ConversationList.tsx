import type { Contact, Message } from '../data'
import { Avatar } from '../App'

function formatTime(date: Date) {
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return 'now'
  if (diff < 3600000) return `${Math.floor(diff / 60000)}m`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}h`
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export default function ConversationList({
  contacts,
  messages,
  activeContact,
  onSelect,
}: {
  contacts: Contact[]
  messages: Record<string, Message[]>
  activeContact: Contact | null
  onSelect: (c: Contact) => void
}) {
  return (
    <div style={{
      width: '300px',
      flexShrink: 0,
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      background: 'var(--background)',
      borderRight: '1px solid var(--border)',
    }}>
      {/* Header */}
      <div style={{
        padding: '20px 16px 12px',
        borderBottom: '1px solid var(--border)',
        display: 'flex',
        flexDirection: 'column',
        gap: '12px',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: '15px', fontWeight: 600, letterSpacing: '-0.01em' }}>Messages</span>
          <button style={{
            width: '28px', height: '28px',
            borderRadius: '7px',
            border: '1px solid var(--border)',
            background: 'transparent',
            color: 'var(--muted-foreground)',
            cursor: 'pointer',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
          }}>
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
          </button>
        </div>

        {/* Search */}
        <div style={{ position: 'relative' }}>
          <svg style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)', color: 'var(--muted-foreground)' }}
            width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            placeholder="Search contacts…"
            style={{
              width: '100%',
              padding: '7px 10px 7px 30px',
              background: 'var(--secondary)',
              border: '1px solid var(--border)',
              borderRadius: '7px',
              color: 'var(--foreground)',
              fontSize: '13px',
              fontFamily: 'var(--font-sans)',
              outline: 'none',
            }}
          />
        </div>
      </div>

      {/* List */}
      <div style={{ flex: 1, overflowY: 'auto' }}>
        {contacts.map(contact => {
          const msgs = messages[contact.id] ?? []
          const last = msgs[msgs.length - 1]
          const unread = msgs.filter(m => m.direction === 'in' && m.status !== 'read').length
          const isActive = activeContact?.id === contact.id

          return (
            <button
              key={contact.id}
              onClick={() => onSelect(contact)}
              style={{
                width: '100%',
                padding: '12px 16px',
                background: isActive ? 'rgba(0,212,168,0.06)' : 'transparent',
                border: 'none',
                borderLeft: isActive ? '2px solid var(--primary)' : '2px solid transparent',
                cursor: 'pointer',
                display: 'flex',
                gap: '11px',
                alignItems: 'flex-start',
                textAlign: 'left',
                transition: 'background 0.1s',
              }}
              onMouseEnter={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'rgba(255,255,255,0.03)' }}
              onMouseLeave={e => { if (!isActive) (e.currentTarget as HTMLButtonElement).style.background = 'transparent' }}
            >
              <div style={{ position: 'relative', flexShrink: 0 }}>
                <Avatar name={contact.name} size={40} />
                <div style={{
                  position: 'absolute',
                  bottom: 0,
                  right: 0,
                  width: '10px',
                  height: '10px',
                  borderRadius: '50%',
                  background: contact.online ? 'var(--primary)' : 'var(--muted-foreground)',
                  border: '2px solid var(--background)',
                  boxShadow: contact.online ? '0 0 4px var(--primary)' : 'none',
                }} />
              </div>

              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '2px' }}>
                  <span style={{ fontWeight: 600, fontSize: '14px', color: 'var(--foreground)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '140px' }}>
                    {contact.name}
                  </span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
                    <span style={{ fontSize: '11px', color: 'var(--muted-foreground)' }}>
                      {last ? formatTime(last.timestamp) : ''}
                    </span>
                    {unread > 0 && (
                      <span style={{
                        width: '16px', height: '16px',
                        background: 'var(--primary)',
                        color: 'var(--primary-foreground)',
                        borderRadius: '50%',
                        fontSize: '10px',
                        fontWeight: 700,
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                      }}>
                        {unread}
                      </span>
                    )}
                  </div>
                </div>

                <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ fontSize: '11px' }}>{contact.language.flag}</span>
                  {last ? (
                    <span style={{
                      fontSize: '12px',
                      color: 'var(--muted-foreground)',
                      whiteSpace: 'nowrap',
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      flex: 1,
                    }}>
                      {last.direction === 'out' && <span style={{ color: 'var(--muted-foreground)' }}>You: </span>}
                      {last.content}
                    </span>
                  ) : (
                    <span style={{ fontSize: '12px', color: 'var(--muted-foreground)', fontStyle: 'italic' }}>No messages yet</span>
                  )}
                </div>
              </div>
            </button>
          )
        })}
      </div>
    </div>
  )
}
