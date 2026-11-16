import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import {
  NOTIFY_UUID, SERVICE_UUID, WRITE_UUID, WebBluetoothTransport,
  type BtCharacteristic, type BtDevice, type BtNavigator,
} from './webBluetoothTransport'

type Listener = (e: unknown) => void

class FakeChar implements BtCharacteristic {
  value: DataView | null = null
  writes: Uint8Array[] = []
  notifying = false
  failWrite = false
  hangWrite = false
  private ls = new Map<string, Set<Listener>>()
  async startNotifications() { this.notifying = true; return this }
  async writeValueWithResponse(v: BufferSource) {
    if (this.hangWrite) return new Promise<void>(() => {})
    if (this.failWrite) throw new Error('GATT write failed')
    this.writes.push(new Uint8Array(v as ArrayBuffer).slice())
  }
  addEventListener(t: string, l: Listener) { (this.ls.get(t) ?? this.ls.set(t, new Set()).get(t)!).add(l) }
  removeEventListener(t: string, l: Listener) { this.ls.get(t)?.delete(l) }
  notify(bytes: number[]) {
    const buf = new Uint8Array(bytes).buffer
    this.value = new DataView(buf)
    for (const l of this.ls.get('characteristicvaluechanged') ?? []) l({ target: this })
  }
}

class FakeDevice implements BtDevice {
  readonly writeChar = new FakeChar()
  readonly notifyChar = new FakeChar()
  connectCalls = 0
  failConnect = false
  gatt: BtDevice['gatt']
  private ls = new Map<string, Set<Listener>>()
  constructor(public id: string, public name: string) {
    const self = this
    this.gatt = {
      connected: false,
      async connect() {
        self.connectCalls++
        if (self.failConnect) throw new Error('connect failed')
        this.connected = true
        return {
          getPrimaryService: async (uuid: string) => {
            if (uuid !== SERVICE_UUID) throw new Error('no service')
            return {
              getCharacteristic: async (c: string) => {
                if (c === WRITE_UUID) return self.writeChar
                if (c === NOTIFY_UUID) return self.notifyChar
                throw new Error('no characteristic')
              },
            }
          },
        }
      },
      disconnect() { this.connected = false },
    } as BtDevice['gatt']
  }
  addEventListener(t: string, l: Listener) { (this.ls.get(t) ?? this.ls.set(t, new Set()).get(t)!).add(l) }
  removeEventListener(t: string, l: Listener) { this.ls.get(t)?.delete(l) }
  drop() {
    ;(this.gatt as { connected: boolean }).connected = false
    for (const l of this.ls.get('gattserverdisconnected') ?? []) l({})
  }
}

const nav = (dev: FakeDevice | Error) => {
  const calls: unknown[] = []
  const n: BtNavigator = {
    requestDevice: async (opts) => { calls.push(opts); if (dev instanceof Error) throw dev; return dev },
    getAvailability: async () => true,
  }
  return { n, calls }
}
const collect = async (t: WebBluetoothTransport) => {
  const out = []
  for await (const d of t.scan(new AbortController().signal)) out.push(d)
  return out
}

beforeEach(() => { vi.useFakeTimers() })
afterEach(() => { vi.useRealTimers() })

describe('WebBluetoothTransport', () => {
  test('scan asks for a BLAZE-30 device with the amp service and yields it', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const { n, calls } = nav(dev)
    const found = await collect(new WebBluetoothTransport(n))
    expect(found).toEqual([{ name: 'BLAZE-30 APP', address: 'id-1' }])
    expect(JSON.stringify(calls[0])).toContain('BLAZE-30')
    expect(JSON.stringify(calls[0])).toContain(SERVICE_UUID)
  })

  test('a cancelled or empty chooser ends the scan with nothing, no throw', async () => {
    const { n } = nav(new DOMException('no device', 'NotFoundError') as unknown as Error)
    expect(await collect(new WebBluetoothTransport(n))).toEqual([])
  })

  test('connect wires write and notify characteristics and reports connected', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    const [d] = await collect(t)
    const states: string[] = []
    t.connection.subscribe((s) => states.push(s.kind))
    await t.connect(d)
    expect(states).toEqual(['connecting', 'connected'])
    expect(dev.notifyChar.notifying).toBe(true)
  })

  test('notifications reach listeners as copied bytes, in order', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    await t.connect((await collect(t))[0])
    const seen: Uint8Array[] = []
    t.onIncoming((f) => seen.push(f))
    dev.notifyChar.notify([1, 2, 3]); dev.notifyChar.notify([4])
    dev.notifyChar.notify([9])
    expect(seen.map((s) => [...s])).toEqual([[1, 2, 3], [4], [9]])
    seen[0][0] = 99
    expect(dev.notifyChar.value!.getUint8(0)).toBe(9)
  })

  test('write sends the bytes with response; rejects when not connected', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    await expect(t.write(Uint8Array.of(1))).rejects.toThrow()
    await t.connect((await collect(t))[0])
    await t.write(Uint8Array.of(0xfe, 0x0d))
    expect(dev.writeChar.writes.map((w) => [...w])).toEqual([[0xfe, 0x0d]])
  })

  test('a write that never completes times out and rejects', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n, { writeTimeoutMs: 3000 })
    await t.connect((await collect(t))[0])
    dev.writeChar.hangWrite = true
    const p = t.write(Uint8Array.of(1))
    const assertion = expect(p).rejects.toThrow(/timeout/)
    await vi.advanceTimersByTimeAsync(3001)
    await assertion
  })

  test('the gattserverdisconnected event moves to disconnected and blocks writes', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    await t.connect((await collect(t))[0])
    dev.drop()
    expect(t.connection.value.kind).toBe('disconnected')
    await expect(t.write(Uint8Array.of(1))).rejects.toThrow()
  })

  test('a second connect reuses the known device without a new chooser', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const { n, calls } = nav(dev)
    const t = new WebBluetoothTransport(n)
    const [d] = await collect(t)
    await t.connect(d)
    dev.drop()
    await t.connect(d)
    expect(calls).toHaveLength(1)
    expect(dev.connectCalls).toBe(2)
    expect(t.connection.value.kind).toBe('connected')
  })

  test('a failing gatt connect ends in failed', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    dev.failConnect = true
    const t = new WebBluetoothTransport(nav(dev).n)
    await t.connect((await collect(t))[0])
    expect(t.connection.value.kind).toBe('failed')
  })

  test('connecting to an unknown device fails without throwing', async () => {
    const t = new WebBluetoothTransport(nav(new FakeDevice('x', 'BLAZE-30')).n)
    await t.connect({ name: 'BLAZE-30', address: 'never-scanned' })
    expect(t.connection.value.kind).toBe('failed')
  })

  test('a user disconnect closes the gatt link and does not leave a stale listener', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    await t.connect((await collect(t))[0])
    t.disconnect()
    expect(dev.gatt.connected).toBe(false)
    expect(t.connection.value.kind).toBe('disconnected')
    const seen: Uint8Array[] = []
    t.onIncoming((f) => seen.push(f))
    dev.notifyChar.notify([1])
    expect(seen).toHaveLength(0)
  })

  test('a scan while an earlier chooser is still pending reuses that request instead of opening a second one', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    let resolveFirst: (d: FakeDevice) => void = () => {}
    let calls = 0
    const n: BtNavigator = {
      requestDevice: () => { calls++; return new Promise<FakeDevice>((r) => { resolveFirst = r }) },
      getAvailability: async () => true,
    }
    const t = new WebBluetoothTransport(n)
    const first = collect(t)       // window 1 times out in the controller but the request stays pending
    const second = collect(t)      // window 2
    resolveFirst(dev)
    expect(await first).toEqual([{ name: 'BLAZE-30 APP', address: 'id-1' }])
    expect(await second).toEqual([{ name: 'BLAZE-30 APP', address: 'id-1' }])
    expect(calls).toBe(1)
  })

  test('a connect that hangs times out, releases the link and ends in failed (no endless connecting)', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n, { connectTimeoutMs: 5000 })
    const [d] = await collect(t)
    dev.gatt.connect = () => new Promise(() => {}) // WinRT never answers
    const p = t.connect(d)
    await vi.advanceTimersByTimeAsync(5001)
    await p
    expect(t.connection.value).toEqual({ kind: 'failed', reason: 'connect timeout' })
    expect(dev.gatt.connected).toBe(false)
  })

  test('a connect superseded by a newer disconnect leaves no notification listener behind', async () => {
    const dev = new FakeDevice('id-1', 'BLAZE-30 APP')
    const t = new WebBluetoothTransport(nav(dev).n)
    const [d] = await collect(t)
    const p = t.connect(d)
    t.disconnect() // user gives up while the link is being set up
    await p
    const seen: Uint8Array[] = []
    t.onIncoming((f) => seen.push(f))
    dev.notifyChar.notify([1])
    expect(seen).toHaveLength(0)
  })
})
