export type Language = {
  code: string
  name: string
  flag: string
}

export const LANGUAGES: Language[] = [
  { code: 'auto', name: 'Auto-Detect', flag: '🌐' },
  { code: 'en',   name: 'English',    flag: '🇬🇧' },
  { code: 'es',   name: 'Spanish',    flag: '🇪🇸' },
  { code: 'fr',   name: 'French',     flag: '🇫🇷' },
  { code: 'de',   name: 'German',     flag: '🇩🇪' },
  { code: 'ja',   name: 'Japanese',   flag: '🇯🇵' },
  { code: 'zh',   name: 'Mandarin',   flag: '🇨🇳' },
  { code: 'pt',   name: 'Portuguese', flag: '🇧🇷' },
  { code: 'ar',   name: 'Arabic',     flag: '🇸🇦' },
]

export type Contact = {
  id: string
  name: string
  lxmfAddress: string
  language: Language
  online: boolean
  lastSeen: string
  avatar: string
  photo: string
}

export type Message = {
  id: string
  contactId: string
  direction: 'in' | 'out'
  content: string
  originalContent?: string
  originalLanguage?: Language
  translated: boolean
  timestamp: Date
  status: 'sending' | 'delivered' | 'read'
  lxmfHops?: number
}

export type CallState = {
  active: boolean
  contact: Contact | null
  duration: number
  captionsEnabled: boolean
  translationEnabled: boolean
  ttsEnabled: boolean
  liveCaption: string
  muted: boolean
  cameraOff: boolean
  sttState: 'idle' | 'listening' | 'processing' | 'showing'
  detectedLanguage: Language | null
  speakingLevel: number
}

export const LOCAL_USER = {
  name: 'You',
  photo: 'https://images.unsplash.com/photo-1590086782957-93c06ef21604?w=400&h=400&fit=crop&auto=format',
}

export const CONTACTS: Contact[] = [
  {
    id: 'c1',
    name: 'Valentina Cruz',
    lxmfAddress: 'a3f8b2c1d9e047f6a8b3c2d1e9f047a6',
    language: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    online: true,
    lastSeen: 'now',
    avatar: 'VC',
    photo: 'https://images.unsplash.com/photo-1544005313-94ddf0286df2?w=400&h=400&fit=crop&auto=format',
  },
  {
    id: 'c2',
    name: 'Kenji Nakamura',
    lxmfAddress: 'b7d4a1e8f203c569a1b8d4e7f203c569',
    language: { code: 'ja', name: 'Japanese', flag: '🇯🇵' },
    online: true,
    lastSeen: 'now',
    avatar: 'KN',
    photo: 'https://images.unsplash.com/photo-1548544149-4835e62ee5b3?w=400&h=400&fit=crop&auto=format',
  },
  {
    id: 'c3',
    name: 'Amara Diallo',
    lxmfAddress: 'c2e9f145a8b3d601c9e2f145a8b3d601',
    language: { code: 'fr', name: 'French', flag: '🇫🇷' },
    online: false,
    lastSeen: '2h ago',
    avatar: 'AD',
    photo: 'https://images.unsplash.com/photo-1590702841774-45166f031529?w=400&h=400&fit=crop&auto=format',
  },
  {
    id: 'c4',
    name: 'Lars Eriksson',
    lxmfAddress: 'd1f6a289b4c7e302d6f1a289b4c7e302',
    language: { code: 'de', name: 'German', flag: '🇩🇪' },
    online: false,
    lastSeen: 'yesterday',
    avatar: 'LE',
    photo: 'https://images.unsplash.com/flagged/photo-1570612861542-284f4c12e75f?w=400&h=400&fit=crop&auto=format',
  },
  {
    id: 'c5',
    name: 'Mei-Lin Zhou',
    lxmfAddress: 'e5c3b8f049a1d627e3c5b8f049a1d627',
    language: { code: 'zh', name: 'Mandarin', flag: '🇨🇳' },
    online: true,
    lastSeen: 'now',
    avatar: 'MZ',
    photo: 'https://images.unsplash.com/photo-1506863530036-1efeddceb993?w=400&h=400&fit=crop&auto=format',
  },
]

export const MESSAGES: Record<string, Message[]> = {
  c1: [
    {
      id: 'm1', contactId: 'c1', direction: 'in',
      content: "Hey! Have you tested the new mesh routing? It's incredible how it finds paths automatically.",
      originalContent: '¡Hola! ¿Has probado el nuevo enrutamiento de malla? Es increíble cómo encuentra rutas automáticamente.',
      originalLanguage: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 14),
      status: 'read', lxmfHops: 2,
    },
    {
      id: 'm2', contactId: 'c1', direction: 'out',
      content: "Yes! Three hops through the local nodes and still under 200ms. The network is growing fast here.",
      translated: false,
      timestamp: new Date(Date.now() - 1000 * 60 * 12),
      status: 'read', lxmfHops: 3,
    },
    {
      id: 'm3', contactId: 'c1', direction: 'in',
      content: 'The voice quality on LXST is also surprisingly good, even with the translation layer running.',
      originalContent: 'La calidad de voz en LXST también es sorprendentemente buena, incluso con la capa de traducción activa.',
      originalLanguage: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 8),
      status: 'read', lxmfHops: 2,
    },
    {
      id: 'm4', contactId: 'c1', direction: 'out',
      content: "Agreed. On-device STT keeps the latency down. No cloud dependency at all.",
      translated: false,
      timestamp: new Date(Date.now() - 1000 * 60 * 5),
      status: 'read', lxmfHops: 2,
    },
    {
      id: 'm5', contactId: 'c1', direction: 'in',
      content: "Perfect for areas with no internet. We tested it in the mountains last weekend!",
      originalContent: '¡Perfecto para zonas sin internet. Lo probamos en la montaña el fin de semana pasado!',
      originalLanguage: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 2),
      status: 'delivered', lxmfHops: 4,
    },
  ],
  c2: [
    {
      id: 'm10', contactId: 'c2', direction: 'in',
      content: "Shannon is exactly what I've been looking for. Mesh networking with translation built in.",
      originalContent: 'Shannonはまさに私が探していたものです。翻訳機能が組み込まれたメッシュネットワーク。',
      originalLanguage: { code: 'ja', name: 'Japanese', flag: '🇯🇵' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2),
      status: 'read', lxmfHops: 1,
    },
    {
      id: 'm11', contactId: 'c2', direction: 'out',
      content: "Named after Claude Shannon — information theory feels right for this kind of network.",
      translated: false,
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 2 + 60000),
      status: 'read', lxmfHops: 1,
    },
  ],
  c3: [
    {
      id: 'm20', contactId: 'c3', direction: 'in',
      content: "The captions during calls work beautifully. Even with background noise.",
      originalContent: "Les sous-titres pendant les appels fonctionnent magnifiquement. Même avec du bruit de fond.",
      originalLanguage: { code: 'fr', name: 'French', flag: '🇫🇷' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 60 * 5),
      status: 'read', lxmfHops: 3,
    },
  ],
  c4: [],
  c5: [
    {
      id: 'm30', contactId: 'c5', direction: 'in',
      content: "Can we schedule a call? I want to show you how the Reticulum node is set up here.",
      originalContent: '我们能安排一次通话吗？我想向你展示这里的Reticulum节点是如何设置的。',
      originalLanguage: { code: 'zh', name: 'Mandarin', flag: '🇨🇳' },
      translated: true,
      timestamp: new Date(Date.now() - 1000 * 60 * 30),
      status: 'delivered', lxmfHops: 5,
    },
  ],
}

export const NETWORK_NODES = [
  { id: 'n1', name: 'Local Interface',     type: 'local',  hops: 0, signal: 100, active: true },
  { id: 'n2', name: 'LoRa Gateway · Roof', type: 'lora',   hops: 1, signal: 87,  active: true },
  { id: 'n3', name: 'TCP Bridge · EU-West',type: 'tcp',    hops: 2, signal: 95,  active: true },
  { id: 'n4', name: 'I2P Tunnel · Exit 7', type: 'i2p',    hops: 3, signal: 72,  active: true },
  { id: 'n5', name: 'LoRa Relay · Valley', type: 'lora',   hops: 2, signal: 45,  active: false },
  { id: 'n6', name: 'Serial · ttyUSB0',    type: 'serial', hops: 1, signal: 91,  active: true },
]

// STT caption samples with original-language text
export const CAPTION_SAMPLES: Array<{ original: string; translated: string; lang: Language }> = [
  {
    lang: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    original: 'Sí, puedo escucharte claramente a través de la malla.',
    translated: 'Yes, I can hear you clearly through the mesh.',
  },
  {
    lang: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    original: 'La señal se enruta a través de tres nodos.',
    translated: 'The signal is routed through three nodes.',
  },
  {
    lang: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    original: 'La transcripción en dispositivo funciona muy bien.',
    translated: 'On-device transcription is working really well.',
  },
  {
    lang: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    original: 'No necesitamos conexión a internet — solo Reticulum.',
    translated: 'No internet connection needed — just Reticulum.',
  },
  {
    lang: { code: 'es', name: 'Spanish', flag: '🇪🇸' },
    original: 'La traducción ocurre localmente, sin latencia de nube.',
    translated: 'The translation happens locally, zero latency to cloud.',
  },
]
