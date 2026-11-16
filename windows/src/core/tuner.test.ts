import { describe, expect, test } from 'vitest'
import { noteFromFrequency, type NoteReading } from './noteMath'
import { PitchDetector } from './pitchDetector'
import { TunerController, type AudioSource, type TunerUi } from './tunerController'

const SR = 44_100
const cents = (measured: number, expected: number) => (1200 * Math.log(measured / expected)) / Math.log(2)

function mulberry32(seed: number) {
  let a = seed
  return () => {
    a |= 0; a = (a + 0x6d2b79f5) | 0
    let t = Math.imul(a ^ (a >>> 15), 1 | a)
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296
  }
}

/** Sum of harmonics (amplitudes for partials 1..n, scaled by amp of full scale), hard-clipped to 16 bit. */
function tone(hz: number, amp = 0.3, amplitudes: number[] = [1], n = 4096): Int16Array {
  const out = new Int16Array(n)
  for (let i = 0; i < n; i++) {
    const t = i / SR
    let v = 0
    amplitudes.forEach((a, h) => { v += a * Math.sin(2 * Math.PI * (h + 1) * hz * t) })
    v *= amp
    out[i] = Math.trunc(Math.max(-1, Math.min(1, v)) * 32767)
  }
  return out
}
const silence = () => new Int16Array(4096)
const noise = (seed: number) => {
  const r = mulberry32(seed)
  return Int16Array.from({ length: 4096 }, () => Math.trunc((r() * 2 - 1) * 0.3 * 32767))
}

describe('noteFromFrequency', () => {
  const reading = (hz: number): NoteReading => noteFromFrequency(hz)!
  test('A440 is A4 in tune and not a guitar string', () => {
    const r = reading(440)
    expect(r.name).toBe('A'); expect(r.octave).toBe(4)
    expect(r.cents).toBeCloseTo(0, 1)
    expect(r.string).toBeNull()
  })
  test('open strings map to their name, octave and number', () => {
    const expected: Array<[number, string, number, number]> = [
      [82.41, 'E', 2, 6], [110, 'A', 2, 5], [146.83, 'D', 3, 4], [196, 'G', 3, 3], [246.94, 'B', 3, 2], [329.63, 'E', 4, 1],
    ]
    for (const [hz, name, octave, string] of expected) {
      const r = reading(hz)
      expect(r.name).toBe(name); expect(r.octave).toBe(octave); expect(r.string).toBe(string)
      expect(Math.abs(r.cents)).toBeLessThan(0.5)
    }
  })
  test('cents follow the sign of the deviation', () => {
    expect(reading(82.41 * 2 ** (30 / 1200)).cents).toBeCloseTo(30, 0)
    expect(reading(82.41 * 2 ** (-30 / 1200)).cents).toBeCloseTo(-30, 0)
  })
  test('sharps use diez names', () => {
    const r = reading(116.54)
    expect(r.name).toBe('A#'); expect(r.octave).toBe(2)
  })
  test('a pitch between two strings picks the nearer one', () => {
    expect(reading(100).string).toBe(5)
  })
  test('non-positive or NaN frequencies give nothing', () => {
    expect(noteFromFrequency(0)).toBeNull()
    expect(noteFromFrequency(-5)).toBeNull()
    expect(noteFromFrequency(NaN)).toBeNull()
  })
})

describe('PitchDetector', () => {
  const detector = new PitchDetector()
  const within = (maxCents: number, expectedHz: number, samples: Int16Array, label = '') => {
    const hz = detector.detect(samples)
    expect(hz, `no pitch for ${expectedHz} Hz ${label}`).not.toBeNull()
    expect(Math.abs(cents(hz!, expectedHz)), `${label}: ${hz} Hz`).toBeLessThanOrEqual(maxCents)
  }
  test('detects each standard string within one cent', () => {
    for (const hz of [82.41, 110, 146.83, 196, 246.94, 329.63]) within(1, hz, tone(hz))
  })
  test('detects a detuned low E on both sides', () => {
    for (const c of [-40, -15, 15, 40]) {
      const hz = 82.41 * 2 ** (c / 1200)
      within(1.5, hz, tone(hz), `detuned ${c}`)
    }
  })
  test('low E with a strong second harmonic still gives the fundamental', () => {
    within(2, 82.41, tone(82.41, 0.3, [0.15, 0.45, 0.25, 0.1]))
  })
  test('guitar-like sawtooth on A2', () => {
    within(2, 110, tone(110, 0.2, [1, 2, 3, 4, 5, 6, 7, 8].map((i) => 1 / i)))
  })
  test('a clipped loud sine still detects', () => {
    within(3, 110, tone(110, 1.5))
  })
  test('silence and very quiet signals give nothing', () => {
    expect(detector.detect(new Int16Array(4096))).toBeNull()
    expect(detector.detect(tone(110, 0.002))).toBeNull()
  })
  test('white noise gives nothing', () => {
    expect(detector.detect(noise(1))).toBeNull()
  })
  test('mains hum below the guitar range gives nothing', () => {
    expect(detector.detect(tone(50))).toBeNull()
  })
  test('never reports a pitch above the detection range', () => {
    for (const hz of [1500, 3000, 6000]) {
      const r = detector.detect(tone(hz))
      expect(r === null || r <= detector.maxHz).toBe(true)
    }
  })
})

/** Frames are pushed by the test one at a time; counts how often the source is opened and released. */
class FakeSource implements AudioSource {
  private queue: Int16Array[] = []
  private wake: (() => void) | null = null
  failOnOpen = false
  starts = 0
  releases = 0
  async *frames(signal: AbortSignal): AsyncIterable<Int16Array> {
    this.starts++
    try {
      if (this.failOnOpen) throw new Error('microphone busy')
      while (!signal.aborted) {
        const f = this.queue.shift()
        if (f) { yield f; continue }
        await new Promise<void>((res) => {
          this.wake = res
          signal.addEventListener('abort', () => res(), { once: true })
        })
      }
    } finally {
      this.releases++
    }
  }
  push(f: Int16Array) { this.queue.push(f); this.wake?.() }
}
const tick = () => new Promise<void>((r) => setTimeout(r, 0))

describe('TunerController', () => {
  const setup = (failOnOpen = false) => {
    const source = new FakeSource()
    source.failOnOpen = failOnOpen
    const c = new TunerController(source)
    const ac = new AbortController()
    const run = () => { const p = c.run(ac.signal); return p }
    const push = async (...frames: Int16Array[]) => { for (const f of frames) { source.push(f); await tick(); await tick() } }
    const reading = () => { const u: TunerUi = c.ui.value; return u.kind === 'reading' ? u.note : null }
    return { source, c, ac, run, push, reading }
  }

  test('a steady low E shows E2 on string 6', async () => {
    const { run, push, reading } = setup()
    const p = run(); await tick()
    await push(...Array.from({ length: 8 }, () => tone(82.41)))
    const r = reading()!
    expect(r.name).toBe('E'); expect(r.octave).toBe(2); expect(r.string).toBe(6)
    expect(Math.abs(r.cents)).toBeLessThan(2)
    void p
  })

  test('silence and room noise never show a note', async () => {
    const { run, push, c } = setup()
    run(); await tick()
    for (let i = 0; i < 20; i++) { await push(silence()); expect(c.ui.value.kind).toBe('nosignal') }
    for (let i = 0; i < 20; i++) { await push(noise(i)); expect(c.ui.value.kind).toBe('nosignal') }
  })

  test('holds the last note briefly, then goes to no signal', async () => {
    const { run, push, c, reading } = setup()
    run(); await tick()
    await push(...Array.from({ length: 5 }, () => tone(110)))
    expect(reading()!.name).toBe('A')
    for (let i = 0; i < 4; i++) await push(silence())
    expect(c.ui.value.kind).toBe('reading')
    await push(silence())
    expect(c.ui.value.kind).toBe('nosignal')
  })

  test('a single octave glitch frame does not flip the display', async () => {
    const { run, push, reading } = setup()
    run(); await tick()
    await push(...Array.from({ length: 4 }, () => tone(82.41)))
    await push(tone(164.82))
    expect(reading()!.name).toBe('E'); expect(reading()!.octave).toBe(2)
    await push(tone(82.41), tone(82.41))
    expect(reading()!.octave).toBe(2)
  })

  test('a source failure shows an error and run resolves normally', async () => {
    const { run, c } = setup(true)
    await run()
    expect(c.ui.value).toEqual({ kind: 'error', message: 'Mikrofon kullanılamıyor' })
  })

  test('running again after an error recovers', async () => {
    const { source, c, push, reading } = setup(true)
    await c.run(new AbortController().signal)
    expect(c.ui.value.kind).toBe('error')
    source.failOnOpen = false
    c.run(new AbortController().signal); await tick()
    expect(c.ui.value.kind).toBe('nosignal')
    await push(...Array.from({ length: 3 }, () => tone(110)))
    expect(reading()!.name).toBe('A')
  })

  test('aborting run releases the microphone', async () => {
    const { source, run, push, ac } = setup()
    const p = run(); await tick()
    await push(tone(110))
    expect(source.starts).toBe(1); expect(source.releases).toBe(0)
    ac.abort(); await p; await tick()
    expect(source.releases).toBe(1)
  })

  test('the first frames after silence never show a note that was not played', async () => {
    const { run, push, reading } = setup()
    run(); await tick()
    await push(silence(), silence())
    for (const frame of [tone(164.82), tone(82.41), tone(82.41), tone(82.41), tone(82.41)]) {
      await push(frame)
      const shown = reading()
      expect(shown === null || (shown.name === 'E' && shown.octave === 2)).toBe(true)
    }
    expect(reading()!.name).toBe('E')
  })

  test('two detections are not enough to show a note but three are', async () => {
    const { run, push, c, reading } = setup()
    run(); await tick()
    await push(tone(82.41), tone(82.41))
    expect(c.ui.value.kind).toBe('nosignal')
    await push(tone(82.41))
    expect(reading()!.name).toBe('E')
  })

  test('a lone stray detection in silence never shows a note', async () => {
    const { run, push, c } = setup()
    run(); await tick()
    await push(silence(), silence(), silence())
    await push(tone(110))
    expect(c.ui.value.kind).toBe('nosignal')
    for (let i = 0; i < 10; i++) { await push(silence()); expect(c.ui.value.kind).toBe('nosignal') }
  })
})
