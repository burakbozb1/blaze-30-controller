import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { AppController } from './appController'
import { AmpRepository } from './ampRepository'
import { FakeTransport } from './fakeTransport'
import { AmpType, Cmd, GuitarEffect, ReverbType, encode } from './protocol'
import { statusPresentation, type Status } from './statusPresentation'
import type { FoundDevice } from './transport'

const blaze: FoundDevice = { name: 'BLAZE-30 APP', address: 'AA:BB:CC:00:00:01' }
const blaze2: FoundDevice = { name: 'BLAZE-30 APP', address: 'AA:BB:CC:DD:EE:FF' }
const flush = () => vi.advanceTimersByTimeAsync(0)
const advance = (ms: number) => vi.advanceTimersByTimeAsync(ms)
const syncFrame = encode(Cmd.sync)
const same = (a: Uint8Array, b: Uint8Array) => a.length === b.length && a.every((v, i) => v === b[i])
const live = (group: number, item: number, sub: number, value: number) => {
  const f = new Uint8Array(16)
  f[0] = 0xfe; f[1] = 0x0f; f[2] = group; f[3] = item; f[4] = sub; f[5] = value; f[15] = 0xff
  return f
}

const make = (devices: FoundDevice[]) => {
  const fake = new FakeTransport(devices)
  const repo = new AmpRepository(fake, { reconnectDelayMs: 1000, syncSettleMs: 0 })
  const app = new AppController(repo, fake, { rescanDelayMs: 3000, maxScanWindows: 3 })
  return { fake, repo, app }
}
const ready = async (devices: FoundDevice[]) => {
  const m = make(devices)
  m.app.onBluetoothAvailable(true); await flush()
  return m
}

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

describe('connection flow', () => {
  test('tapping a tone sends its frame', async () => {
    const { fake, app } = await ready([blaze])
    app.onTone(AmpType.METAL); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setAmpType(AmpType.METAL)))
    expect(app.ui.value.amp.ampType).toBe(AmpType.METAL)
  })

  test('a single device found connects automatically', async () => {
    const { app } = await ready([blaze])
    expect(app.ui.value.status).toBe('connected')
  })

  test('no device found shows not found, and retry rescans without crashing', async () => {
    const { app } = await ready([])
    expect(app.ui.value.status).toBe('notFound')
    app.retry(); await flush()
    expect(app.ui.value.status).toBe('notFound')
  })

  test('several blazes show a picker and selecting one connects', async () => {
    const { app } = await ready([blaze, blaze2])
    expect(app.ui.value.status).toBe('pick')
    expect(app.ui.value.devices).toEqual([blaze, blaze2])
    app.pick(blaze2); await flush()
    expect(app.ui.value.status).toBe('connected')
  })

  test('bluetooth missing and back are distinct states that recover', async () => {
    const m = make([blaze])
    m.app.onBluetoothAvailable(false); await flush()
    expect(m.app.ui.value.status).toBe('noBluetooth')
    m.app.onBluetoothAvailable(true); await flush()
    expect(m.app.ui.value.status).toBe('connected')
  })

  test('a dropped link shows reconnecting, not connected', async () => {
    const { fake, app } = await ready([blaze])
    fake.dropConnection(); await flush()
    expect(app.ui.value.status).toBe('reconnecting')
  })

  test('when the amp is not found the app keeps looking and connects when it appears', async () => {
    const { fake, app } = await ready([])
    expect(app.ui.value.status).toBe('notFound')
    fake.devices = [blaze]
    await advance(3001)
    expect(app.ui.value.status).toBe('connected')
  })

  test('background rescan stops when bluetooth goes away', async () => {
    const { fake, app } = await ready([])
    app.onBluetoothAvailable(false); await flush()
    fake.devices = [blaze]
    await advance(10_000)
    expect(app.ui.value.status).toBe('noBluetooth')
    expect(fake.connectCalls).toBe(0)
  })

  test('turning bluetooth off stops the reconnect loop', async () => {
    const { fake, app } = await ready([blaze])
    expect(fake.connectCalls).toBe(1)
    app.onBluetoothAvailable(false); fake.dropConnection(); await flush()
    await advance(5000)
    expect(fake.connectCalls).toBe(1)
  })

  test('scanning stops after the budget and offers a manual retry', async () => {
    const { fake, app } = await ready([])
    await advance(60_000)
    expect(fake.scanCalls).toBe(3)
    expect(app.ui.value.status).toBe('gaveUp')
    fake.devices = [blaze]
    app.retry(); await flush()
    expect(app.ui.value.status).toBe('connected')
  })

  test('a lost link that cannot be restored ends in linkLost with retry', async () => {
    const { fake, app } = await ready([blaze])
    fake.failConnects = true
    fake.dropConnection(); await flush()
    await advance(120_000)
    expect(app.ui.value.status).toBe('linkLost')
    fake.failConnects = false
    app.retry(); await flush()
    expect(app.ui.value.status).toBe('connected')
  })

  test('returning to the foreground requests a fresh dump', async () => {
    const { fake, app } = await ready([blaze])
    const count = () => fake.written.filter((f) => same(f, syncFrame)).length
    expect(count()).toBe(1)
    app.refresh(); await flush()
    expect(count()).toBe(2)
  })

  test('start() asks the adapter availability first', async () => {
    const fake = new FakeTransport([blaze])
    const repo = new AmpRepository(fake, { syncSettleMs: 0 })
    const app = new AppController(repo, fake, { available: async () => false })
    await app.start(); await flush()
    expect(app.ui.value.status).toBe('noBluetooth')
  })
})

describe('controls', () => {
  test('aux volume sends its frame and updates the state; the master pot moves both volumes', async () => {
    const { fake, app } = await ready([blaze])
    app.setAuxVolume(12); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setAuxVolume(12)))
    expect(app.ui.value.amp.auxVolume).toBe(12)
    const before = fake.written.length
    for (const v of [5, 17, 33]) { fake.emitFromAmp(live(0x02, 0x01, 0x00, v)); fake.emitFromAmp(live(0x04, 0x01, 0x00, v)) }
    await flush()
    expect([app.ui.value.amp.volume, app.ui.value.amp.auxVolume]).toEqual([33, 33])
    expect(fake.written.length).toBe(before)
  })

  test('eq band sends that band and clamps to the protocol range', async () => {
    const { fake, app } = await ready([blaze])
    app.setEq(3, 99); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setEqBand(3, 20)))
    expect(app.ui.value.amp.eq[3]).toBe(20)
  })

  test('choosing wah sends the wah effect', async () => {
    const { fake, app } = await ready([blaze])
    app.setEffect(GuitarEffect.WAH); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setEffect(GuitarEffect.WAH)))
  })

  test('reverb and delay controls send their frames', async () => {
    const { fake, app } = await ready([blaze])
    app.setReverbType(ReverbType.SPRING); app.setReverbLevel(7); app.setReverbTone(8); app.setReverbTime(9)
    app.setDelayLevel(1); app.setDelayFeedback(2); app.setDelayTime(3); await flush()
    expect(fake.written).toHaveLength(8)
    expect(fake.written[1]).toEqual(encode(Cmd.setReverbType(ReverbType.SPRING)))
    expect(fake.written[7]).toEqual(encode(Cmd.setDelayTime(3)))
  })

  test('master delay and reverb send the guitar tab frames', async () => {
    const { fake, app } = await ready([blaze])
    app.setMasterDelay(20); app.setMasterReverb(30); await flush()
    expect(fake.written.at(-2)).toEqual(encode(Cmd.setMasterDelay(20)))
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterReverb(30)))
    expect(app.ui.value.amp.masterDelay).toBe(20)
  })

  test('delay off writes zero and on restores the previous level', async () => {
    const { fake, app } = await ready([blaze])
    app.setMasterDelay(30); await flush()
    expect(app.ui.value.delayOn).toBe(true)
    app.toggleDelay(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterDelay(0)))
    expect(app.ui.value.delayOn).toBe(false)
    app.toggleDelay(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterDelay(30)))
    expect(app.ui.value.delayOn).toBe(true)
  })

  test('zero set on the amp, then toggle, restores the last non-zero level', async () => {
    const { fake, app } = await ready([blaze])
    fake.emitFromAmp(live(0x02, 0x02, 0x00, 12)); await flush()
    fake.emitFromAmp(live(0x02, 0x02, 0x00, 0)); await flush()
    expect(app.ui.value.delayOn).toBe(false)
    app.toggleDelay(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterDelay(12)))
  })

  test('a never-set level toggles on to the default', async () => {
    const { fake, app } = await ready([blaze])
    expect(app.ui.value.delayOn).toBe(false)
    app.toggleDelay(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterDelay(20)))
  })

  test('two quick taps end where they started', async () => {
    const { fake, app } = await ready([blaze])
    app.setMasterDelay(30); await flush()
    app.toggleDelay(); app.toggleDelay(); await flush()
    expect(fake.written.at(-2)).toEqual(encode(Cmd.setMasterDelay(0)))
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterDelay(30)))
    expect(app.ui.value.delayOn).toBe(true)
  })

  test('reverb power mirrors delay power', async () => {
    const { fake, app } = await ready([blaze])
    app.setMasterReverb(40); await flush()
    app.toggleReverb(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterReverb(0)))
    expect(app.ui.value.reverbOn).toBe(false)
    app.toggleReverb(); await flush()
    expect(fake.written.at(-1)).toEqual(encode(Cmd.setMasterReverb(40)))
    expect(app.ui.value.reverbOn).toBe(true)
  })

  test('reset eq writes seven flat bands in order', async () => {
    const { fake, app } = await ready([blaze])
    app.setEq(2, 3); app.setEq(6, 18); await flush()
    expect(app.ui.value.amp.eq[2]).toBe(3)
    app.resetEq(); await flush()
    const last7 = fake.written.slice(-7)
    for (let i = 0; i < 7; i++) expect(last7[i]).toEqual(encode(Cmd.setEqBand(i, 10)))
    expect(app.ui.value.amp.eq).toEqual(Array(7).fill(10))
  })
})

describe('statusPresentation', () => {
  test('only a connected amp enables the controls', () => {
    const all: Status[] = ['starting', 'noBluetooth', 'scanning', 'notFound', 'gaveUp', 'pick', 'connecting', 'connected', 'reconnecting', 'linkLost']
    for (const s of all) expect(statusPresentation(s).controlsEnabled).toBe(s === 'connected')
    expect(statusPresentation('connected')).toEqual({ led: 'connected', label: 'BAĞLI', controlsEnabled: true })
    expect(statusPresentation('reconnecting').led).toBe('searching')
    expect(statusPresentation('linkLost').led).toBe('disconnected')
  })
})
