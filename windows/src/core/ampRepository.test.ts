import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { AmpRepository } from './ampRepository'
import { FakeTransport } from './fakeTransport'
import { AmpType, Cmd, GuitarEffect, ReverbType, encode } from './protocol'
import type { FoundDevice } from './transport'

const device: FoundDevice = { name: 'BLAZE-30 APP', address: 'AA:BB:CC:00:00:01' }
const syncFrame = encode(Cmd.sync)
const same = (a: Uint8Array, b: Uint8Array) => a.length === b.length && a.every((v, i) => v === b[i])
const hex = (h: string) => Uint8Array.from(h.match(/../g)!.map((x) => parseInt(x, 16)))
const live = (group: number, item: number, sub: number, value: number) => {
  const f = new Uint8Array(16)
  f[0] = 0xfe; f[1] = 0x0f; f[2] = group; f[3] = item; f[4] = sub; f[5] = value; f[15] = 0xff
  return f
}
const userFrames = (t: FakeTransport) => t.written.filter((f) => !same(f, syncFrame))
const syncCount = (t: FakeTransport) => t.written.filter((f) => same(f, syncFrame)).length
const flush = () => vi.advanceTimersByTimeAsync(0)
const advance = (ms: number) => vi.advanceTimersByTimeAsync(ms)

const bbDump = hex('FE0FBB0C0004190D0A0A0A0A0A0A0AFF')
const bcDump = hex('FE0FBC00141C1412180B1123140000FF')
const c0Dump = hex('FE0FC0000A080A0B0E100C640D000DFF')

const make = () => {
  const fake = new FakeTransport()
  const repo = new AmpRepository(fake, { reconnectDelayMs: 1000, syncSettleMs: 0, maxReconnectAttempts: 3 })
  return { fake, repo }
}

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

describe('app -> amp', () => {
  test('send writes the encoded frame and updates state', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.send(Cmd.setAmpType(AmpType.DRIVE)); await flush()
    expect(userFrames(fake)).toHaveLength(1)
    expect(userFrames(fake)[0]).toEqual(encode(Cmd.setAmpType(AmpType.DRIVE)))
    expect(repo.state.value.ampType).toBe(AmpType.DRIVE)
  })

  test('rapid taps are written in tap order and the final state is the last tap', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.send(Cmd.setAmpType(AmpType.METAL))
    repo.send(Cmd.setAmpType(AmpType.CLEAN))
    repo.send(Cmd.setAmpType(AmpType.DRIVE))
    await flush()
    expect(userFrames(fake).map((f) => f[5])).toEqual([AmpType.METAL, AmpType.CLEAN, AmpType.DRIVE])
    expect(repo.state.value.ampType).toBe(AmpType.DRIVE)
  })

  test('a dropped connection reconnects and re-syncs but does not replay user values', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.send(Cmd.setAmpType(AmpType.METAL)); repo.send(Cmd.setGain(20)); await flush()

    fake.dropConnection(); await flush()
    expect(repo.connection.value.kind).toBe('disconnected')
    await advance(1001)

    expect(fake.connectCalls).toBe(2)
    expect(repo.connection.value.kind).toBe('connected')
    expect(syncCount(fake)).toBe(2)
    expect(userFrames(fake)).toHaveLength(2)
  })

  test('the first connection pushes nothing to the amp except the sync', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    expect(userFrames(fake)).toHaveLength(0)
    expect(syncCount(fake)).toBe(1)
    expect(fake.written[0]).toEqual(syncFrame)
  })

  test('send while disconnected changes nothing and does not throw', async () => {
    const { fake, repo } = make()
    const before = repo.state.value
    repo.send(Cmd.setAmpType(AmpType.METAL)); await flush()
    expect(repo.state.value).toBe(before)
    expect(userFrames(fake)).toHaveLength(0)
  })

  test('a user disconnect does not auto-reconnect', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.disconnect(); await flush()
    await advance(5000)
    expect(fake.connectCalls).toBe(1)
    expect(repo.connection.value.kind).toBe('disconnected')
  })

  test('reconnect backs off, then gives up and exposes it', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    expect(fake.connectCalls).toBe(1)

    fake.failConnects = true
    fake.dropConnection(); await flush()
    await advance(1001); expect(fake.connectCalls).toBe(2)
    await advance(2000); expect(fake.connectCalls).toBe(3)
    await advance(4000); expect(fake.connectCalls).toBe(4)

    await advance(60000)
    expect(fake.connectCalls).toBe(4)
    expect(repo.gaveUp.value).toBe(true)
  })

  test('a user retry after giving up works and clears the flag', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    fake.failConnects = true
    fake.dropConnection(); await flush()
    await advance(60000)
    expect(repo.gaveUp.value).toBe(true)

    fake.failConnects = false
    repo.reconnect(); await flush()
    expect(repo.gaveUp.value).toBe(false)
    expect(repo.connection.value.kind).toBe('connected')
  })

  test('a cancelled or failed write does not stop later commands', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    fake.failNextWrite = new DOMException('write not acknowledged', 'AbortError')
    repo.send(Cmd.setAmpType(AmpType.METAL)); await flush()
    fake.failNextWrite = new Error('stack rejected the write')
    repo.send(Cmd.setGain(5)); await flush()
    repo.send(Cmd.setAmpType(AmpType.CLEAN)); await flush()

    expect(userFrames(fake)).toHaveLength(1)
    expect(userFrames(fake)[0]).toEqual(encode(Cmd.setAmpType(AmpType.CLEAN)))
    expect(repo.state.value.ampType).toBe(AmpType.CLEAN)
  })
})

describe('amp -> app (real frames captured 2026-11-15)', () => {
  test('the connect dump fills the state', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    fake.emitFromAmp(bbDump); fake.emitFromAmp(bcDump); fake.emitFromAmp(c0Dump); await flush()
    const s = repo.state.value
    expect(s.volume).toBe(12)
    expect(s.ampType).toBe(AmpType.METAL)
    expect(s.gain).toBe(13)
    expect(s.eq).toEqual(Array(7).fill(10))
    expect(s.effect).toBe(GuitarEffect.BYPASS)
    expect(s.masterDelay).toBe(0)
    expect(s.reverbType).toBe(ReverbType.PLATE)
    expect([s.reverbLevel, s.reverbTone, s.reverbTime]).toEqual([10, 8, 10])
    expect([s.delayLevel, s.delayFeedback, s.delayTime]).toEqual([11, 14, 16])
  })

  test('a knob sweep ends on the last value, in order', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    for (let i = 0; i < 200; i++) fake.emitFromAmp(live(0x02, 0x01, 0x00, i % 51))
    await flush()
    expect(repo.state.value.volume).toBe(199 % 51)
  })

  test('incoming frames never cause writes', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    const before = fake.written.length
    for (const f of [bbDump, bcDump, c0Dump, live(0x02, 0x03, 0x00, 1), live(0x02, 0x01, 0x00, 7)]) fake.emitFromAmp(f)
    await flush()
    expect(fake.written.length).toBe(before)
  })

  test('a change made on the amp is not overwritten by the app older value after reconnect', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.send(Cmd.setAmpType(AmpType.METAL)); await flush()
    fake.emitFromAmp(live(0x02, 0x03, 0x00, AmpType.CLEAN)); await flush()
    expect(repo.state.value.ampType).toBe(AmpType.CLEAN)

    fake.dropConnection(); await flush()
    await advance(1001)
    expect(fake.connectCalls).toBe(2)
    expect(userFrames(fake)).toHaveLength(1)
    expect(repo.state.value.ampType).toBe(AmpType.CLEAN)
  })

  test('garbage frames are ignored and later valid ones still apply', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    const before = repo.state.value
    const w = (i: number, v: number) => { const c = bbDump.slice(); c[i] = v; return c }
    const garbage = [
      new Uint8Array(0), bbDump.slice(0, 15), new Uint8Array([...bbDump, 0]), w(1, 0x0d), w(15, 0),
      Uint8Array.from({ length: 16 }, (_, i) => (i * 37) & 0xff),
    ]
    for (const g of garbage) fake.emitFromAmp(g)
    await flush()
    expect(repo.state.value).toBe(before)
    fake.emitFromAmp(live(0x02, 0x01, 0x00, 33)); await flush()
    expect(repo.state.value.volume).toBe(33)
  })

  test('refresh when connected asks the amp for a fresh dump', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    expect(syncCount(fake)).toBe(1)
    repo.refresh(); await flush()
    expect(syncCount(fake)).toBe(2)
    expect(userFrames(fake)).toHaveLength(0)
  })

  test('refresh when disconnected does nothing and does not throw', async () => {
    const { fake, repo } = make()
    repo.refresh(); await flush()
    expect(fake.written).toHaveLength(0)
  })

  test('values changed on the amp during an outage are not overwritten after reconnect', async () => {
    const { fake, repo } = make()
    repo.connect(device); await flush()
    repo.send(Cmd.setGain(20)); await flush()
    expect(userFrames(fake)).toHaveLength(1)

    fake.dropConnection(); await flush()
    await advance(1001)
    fake.emitFromAmp(hex('FE0FBB0C000419050A0A0A0A0A0A0AFF')); await flush()

    expect(userFrames(fake)).toHaveLength(1)
    expect(repo.state.value.gain).toBe(5)
  })
})
