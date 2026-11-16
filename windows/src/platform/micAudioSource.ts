import type { AudioSource } from '../core/tunerController'

export interface MicTrack { stop(): void }
export interface MicStream { getTracks(): MicTrack[] }
export interface MicProcessor {
  onaudioprocess: ((e: { inputBuffer: { getChannelData(ch: number): Float32Array } }) => void) | null
  connect(dest: unknown): void
  disconnect(): void
}
export interface MicContext {
  readonly destination: unknown
  createMediaStreamSource(s: MicStream): { connect(dest: unknown): void; disconnect(): void }
  createScriptProcessor(bufferSize: number, inCh: number, outCh: number): MicProcessor
  close(): Promise<void>
}
export interface MicDeps {
  getUserMedia(constraints: unknown): Promise<MicStream>
  createContext(sampleRate: number): MicContext
}

export const FRAME_SIZE = 4096
export const SAMPLE_RATE = 44_100
const MAX_QUEUED = 4

export const browserMicDeps = (): MicDeps => ({
  getUserMedia: (c) => navigator.mediaDevices.getUserMedia(c as MediaStreamConstraints) as unknown as Promise<MicStream>,
  createContext: (sampleRate) => new AudioContext({ sampleRate }) as unknown as MicContext,
})

/**
 * Microphone as 4096-sample 16-bit mono frames at 44.1 kHz, for the tuner. Echo cancellation, noise suppression and
 * auto gain are off (they would distort the pitch). The microphone is released as soon as the signal aborts.
 */
export class MicAudioSource implements AudioSource {
  constructor(private readonly deps: MicDeps = browserMicDeps()) {}

  async *frames(signal: AbortSignal): AsyncIterable<Int16Array> {
    const queue: Int16Array[] = []
    let wake: (() => void) | null = null
    let stream: MicStream | null = null
    let ctx: MicContext | null = null
    let source: { disconnect(): void } | null = null
    let processor: MicProcessor | null = null
    const onAbort = () => wake?.()
    signal.addEventListener('abort', onAbort, { once: true })
    try {
      stream = await this.deps.getUserMedia({
        audio: { echoCancellation: false, noiseSuppression: false, autoGainControl: false, channelCount: 1 },
      })
      if (signal.aborted) return
      ctx = this.deps.createContext(SAMPLE_RATE)
      const src = ctx.createMediaStreamSource(stream)
      source = src
      processor = ctx.createScriptProcessor(FRAME_SIZE, 1, 1)
      processor.onaudioprocess = (e) => {
        const input = e.inputBuffer.getChannelData(0)
        const out = new Int16Array(input.length)
        for (let i = 0; i < input.length; i++) out[i] = Math.trunc(Math.max(-1, Math.min(1, input[i])) * 32767)
        queue.push(out)
        if (queue.length > MAX_QUEUED) queue.shift() // never fall behind: stale audio is worthless to a tuner
        wake?.()
      }
      src.connect(processor)
      processor.connect(ctx.destination)
      while (!signal.aborted) {
        const f = queue.shift()
        if (f) { yield f; continue }
        await new Promise<void>((res) => { wake = res })
        wake = null
      }
    } finally {
      signal.removeEventListener('abort', onAbort)
      if (processor) { processor.onaudioprocess = null; try { processor.disconnect() } catch { /* gone */ } }
      try { source?.disconnect() } catch { /* gone */ }
      stream?.getTracks().forEach((t) => t.stop())
      void ctx?.close().catch(() => {})
    }
  }
}
