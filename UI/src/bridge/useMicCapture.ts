import { useCallback, useEffect, useRef, useState } from 'react'
import type { Backend } from './backend'

/**
 * §5.2: captures microphone audio via the Web Audio API and streams 16-bit PCM as binary
 * WebSocket frames to the bridge, where BridgeServer feeds it to the on-device STT / outgoing
 * AUDIO path. Echo cancellation + noise suppression are enabled via getUserMedia constraints.
 */
export function useMicCapture(backend: Backend | null) {
  const [capturing, setCapturing] = useState(false)
  const audioContextRef = useRef<AudioContext | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const processorRef = useRef<ScriptProcessorNode | null>(null)

  const startCapture = useCallback(async () => {
    if (!backend || capturing) return
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true },
      })
      streamRef.current = stream
      const ctx = new AudioContext({ sampleRate: 16000 })
      audioContextRef.current = ctx
      const source = ctx.createMediaStreamSource(stream)
      const processor = ctx.createScriptProcessor(4096, 1, 1)
      processor.onaudioprocess = (e: AudioProcessingEvent) => {
        const input = e.inputBuffer.getChannelData(0) // Float32Array [-1, 1]
        const pcm16 = new Int16Array(input.length)
        for (let i = 0; i < input.length; i++) {
          const s = Math.max(-1, Math.min(1, input[i]))
          pcm16[i] = s < 0 ? s * 0x8000 : s * 0x7fff
        }
        backend.sendAudio(pcm16.buffer)
      }
      source.connect(processor)
      processor.connect(ctx.destination)
      processorRef.current = processor
      setCapturing(true)
    } catch (err) {
      console.error('Mic capture failed:', err)
    }
  }, [backend, capturing])

  const stopCapture = useCallback(() => {
    processorRef.current?.disconnect()
    streamRef.current?.getTracks().forEach((t) => t.stop())
    audioContextRef.current?.close()
    processorRef.current = null
    streamRef.current = null
    audioContextRef.current = null
    setCapturing(false)
  }, [])

  useEffect(() => () => stopCapture(), [stopCapture])

  return { capturing, startCapture, stopCapture }
}
