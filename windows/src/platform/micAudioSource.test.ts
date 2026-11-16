import { describe, expect, test } from 'vitest'
import { MicAudioSource, type MicContext, type MicDeps, type MicProcessor } from './micAudioSource'

const tick = () => new Promise<void>((r) => setTimeout(r, 0))

const fakeDeps = () => {
  const state = { stopped: 0, closed: 0, constraints: null as unknown, processor: null as MicProcessor | null, rate: 0, failOpen: false }
  const deps: MicDeps = {
    getUserMedia: async (c) => {
      state.constraints = c
      if (state.failOpen) throw new DOMException('denied', 'NotAllowedError')
      return { getTracks: () => [{ stop: () => { state.stopped++ } }] }
    },
    createContext: (rate): MicContext => {
      state.rate = rate
      return {
        destination: {},
        createMediaStreamSource: () => ({ connect() {}, disconnect() {} }),
        createScriptProcessor: () => {
          const p: MicProcessor = { onaudioprocess: null, connect() {}, disconnect() {} }
          state.processor = p
          return p
        },
        close: async () => { state.closed++ },
      }
    },
  }
  return { deps, state }
}
const feed = (state: ReturnType<typeof fakeDeps>['state'], samples: number[]) =>
  state.processor!.onaudioprocess!({ inputBuffer: { getChannelData: () => Float32Array.from(samples) } })

describe('MicAudioSource', () => {
  test('requests raw audio (no echo cancel/noise suppression/AGC) at 44.1 kHz and yields 16-bit frames', async () => {
    const { deps, state } = fakeDeps()
    const ac = new AbortController()
    const it = new MicAudioSource(deps).frames(ac.signal)[Symbol.asyncIterator]()
    const first = it.next()
    await tick()
    feed(state, [0, 0.5, -0.5, 2, -2])
    const r = await first
    expect([...r.value]).toEqual([0, 16383, -16383, 32767, -32767])
    expect(JSON.stringify(state.constraints)).toContain('"echoCancellation":false')
    expect(JSON.stringify(state.constraints)).toContain('"noiseSuppression":false')
    expect(JSON.stringify(state.constraints)).toContain('"autoGainControl":false')
    expect(state.rate).toBe(44100)
    ac.abort(); await it.next()
  })

  test('aborting releases the tracks and closes the audio context', async () => {
    const { deps, state } = fakeDeps()
    const ac = new AbortController()
    const it = new MicAudioSource(deps).frames(ac.signal)[Symbol.asyncIterator]()
    const pending = it.next()
    await tick()
    ac.abort()
    const r = await pending
    await tick()
    expect(r.done).toBe(true)
    expect(state.stopped).toBe(1)
    expect(state.closed).toBe(1)
    expect(state.processor!.onaudioprocess).toBeNull()
  })

  test('a denied microphone rejects so the tuner can show its error', async () => {
    const { deps, state } = fakeDeps()
    state.failOpen = true
    const it = new MicAudioSource(deps).frames(new AbortController().signal)[Symbol.asyncIterator]()
    await expect(it.next()).rejects.toThrow()
    expect(state.stopped).toBe(0)
  })

  test('a slow consumer never builds up a backlog of stale frames', async () => {
    const { deps, state } = fakeDeps()
    const ac = new AbortController()
    const it = new MicAudioSource(deps).frames(ac.signal)[Symbol.asyncIterator]()
    const first = it.next()
    await tick()
    for (let i = 1; i <= 10; i++) feed(state, [i / 100]) // ten frames arrive while the consumer is busy
    const got = [(await first).value[0]]
    for (let i = 0; i < 3; i++) got.push((await it.next()).value[0])
    // only the newest four survive: 0.07 .. 0.10 of full scale
    expect(got).toEqual([7, 8, 9, 10].map((n) => Math.trunc((n / 100) * 32767)))
    ac.abort(); await it.next()
  })
})
