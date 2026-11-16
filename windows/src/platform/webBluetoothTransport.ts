import { MutableObservable, type Observable } from '../core/observable'
import { CONNECTED, CONNECTING, DISCONNECTED, type AmpTransport, type ConnectionState, type FoundDevice } from '../core/transport'

export const SERVICE_UUID = '0000fff0-0000-1000-8000-00805f9b34fb'
export const WRITE_UUID = '0000fff3-0000-1000-8000-00805f9b34fb'
export const NOTIFY_UUID = '0000fff2-0000-1000-8000-00805f9b34fb'
export const NAME_PREFIX = 'BLAZE-30'

// Minimal structural types of the Web Bluetooth API we use (so tests can fake them and no DOM lib type is required).
type Listener = (e: unknown) => void
export interface BtCharacteristic {
  value: DataView | null
  startNotifications(): Promise<unknown>
  writeValueWithResponse(v: BufferSource): Promise<void>
  addEventListener(type: string, l: Listener): void
  removeEventListener(type: string, l: Listener): void
}
export interface BtService { getCharacteristic(uuid: string): Promise<BtCharacteristic> }
export interface BtServer { getPrimaryService(uuid: string): Promise<BtService> }
export interface BtDevice {
  id: string
  name?: string
  gatt: { connected: boolean; connect(): Promise<BtServer>; disconnect(): void }
  addEventListener(type: string, l: Listener): void
  removeEventListener(type: string, l: Listener): void
}
export interface BtNavigator {
  requestDevice(options: unknown): Promise<BtDevice>
  getAvailability(): Promise<boolean>
}

export interface WebBluetoothOptions {
  writeTimeoutMs?: number
  connectTimeoutMs?: number
}

/** The amp over Chromium's Web Bluetooth (Electron). One chooser per scan; reconnects reuse the known device object. */
export class WebBluetoothTransport implements AmpTransport {
  private readonly _connection = new MutableObservable<ConnectionState>(DISCONNECTED)
  readonly connection: Observable<ConnectionState> = this._connection
  private readonly listeners = new Set<(f: Uint8Array) => void>()
  private readonly known = new Map<string, BtDevice>()
  private active: BtDevice | null = null
  private writeChar: BtCharacteristic | null = null
  private notifyChar: BtCharacteristic | null = null
  private token = 0
  private readonly writeTimeoutMs: number
  private readonly connectTimeoutMs: number

  constructor(private readonly bluetooth: BtNavigator, o: WebBluetoothOptions = {}) {
    this.writeTimeoutMs = o.writeTimeoutMs ?? 3000
    this.connectTimeoutMs = o.connectTimeoutMs ?? 10_000
  }

  onIncoming(cb: (frame: Uint8Array) => void): () => void {
    this.listeners.add(cb)
    return () => this.listeners.delete(cb)
  }

  /** A chooser that outlived its scan window is reused by the next window instead of opening a second one. */
  private pending: Promise<BtDevice> | null = null

  async *scan(_signal: AbortSignal): AsyncIterable<FoundDevice> {
    let dev: BtDevice
    try {
      if (!this.pending) {
        const p = this.bluetooth.requestDevice({
          filters: [{ namePrefix: NAME_PREFIX }],
          optionalServices: [SERVICE_UUID],
        })
        this.pending = p
        const clear = () => { if (this.pending === p) this.pending = null }
        p.then(clear, clear)
      }
      dev = await this.pending
    } catch {
      return // chooser cancelled / nothing in range / adapter off: the caller treats it as "not found"
    }
    this.known.set(dev.id, dev)
    yield { name: dev.name ?? NAME_PREFIX, address: dev.id }
  }

  private readonly onNotify: Listener = (e) => {
    const ch = (e as { target: BtCharacteristic }).target
    const v = ch.value
    if (!v) return
    const copy = new Uint8Array(v.buffer.slice(v.byteOffset, v.byteOffset + v.byteLength))
    for (const l of [...this.listeners]) l(copy.slice())
  }

  private readonly onGattDisconnected: Listener = () => {
    if (this._connection.value.kind === 'disconnected') return
    this.teardown(false)
    this._connection.value = DISCONNECTED
  }

  async connect(device: FoundDevice): Promise<void> {
    const dev = this.known.get(device.address)
    if (!dev) {
      this._connection.value = { kind: 'failed', reason: 'unknown device' }
      return
    }
    const token = ++this.token
    this.teardown(false)
    this._connection.value = CONNECTING
    const held: { notify: BtCharacteristic | null } = { notify: null }
    const setup = (async () => {
      const server = await dev.gatt.connect()
      const service = await server.getPrimaryService(SERVICE_UUID)
      const write = await service.getCharacteristic(WRITE_UUID)
      const notify = await service.getCharacteristic(NOTIFY_UUID)
      held.notify = notify
      notify.addEventListener('characteristicvaluechanged', this.onNotify)
      await notify.startNotifications()
      return { write, notify }
    })()
    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<'timeout'>((res) => { timer = setTimeout(() => res('timeout'), this.connectTimeoutMs) })
    const release = () => {
      held.notify?.removeEventListener('characteristicvaluechanged', this.onNotify)
      try { dev.gatt.disconnect() } catch { /* already down */ }
    }
    try {
      const r = await Promise.race([setup, timeout])
      if (r === 'timeout') {
        setup.catch(() => {}) // a late answer from the stack is ignored
        held.notify?.removeEventListener('characteristicvaluechanged', this.onNotify)
        try { dev.gatt.disconnect() } catch { /* already down */ }
        if (token === this.token) this._connection.value = { kind: 'failed', reason: 'connect timeout' }
        return
      }
      if (token !== this.token) { // a newer connect/disconnect superseded this one: leave nothing attached
        r.notify.removeEventListener('characteristicvaluechanged', this.onNotify)
        return
      }
      dev.addEventListener('gattserverdisconnected', this.onGattDisconnected)
      this.active = dev
      this.writeChar = r.write
      this.notifyChar = r.notify
      this._connection.value = CONNECTED
    } catch (e) {
      if (token !== this.token) { held.notify?.removeEventListener('characteristicvaluechanged', this.onNotify); return }
      release()
      this._connection.value = { kind: 'failed', reason: e instanceof Error ? e.message : 'connect failed' }
    } finally {
      clearTimeout(timer)
    }
  }

  async write(bytes: Uint8Array): Promise<void> {
    const ch = this.writeChar
    if (this._connection.value.kind !== 'connected' || !ch) throw new Error('not connected')
    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<never>((_, rej) => {
      timer = setTimeout(() => rej(new Error('write timeout')), this.writeTimeoutMs)
    })
    try {
      await Promise.race([ch.writeValueWithResponse(bytes.slice().buffer), timeout])
    } finally {
      clearTimeout(timer)
    }
  }

  disconnect(): void {
    this.token++
    const dev = this.active
    this.teardown(true)
    if (dev) { try { dev.gatt.disconnect() } catch { /* already down */ } }
    this._connection.value = DISCONNECTED
  }

  private teardown(_user: boolean): void {
    this.notifyChar?.removeEventListener('characteristicvaluechanged', this.onNotify)
    this.active?.removeEventListener('gattserverdisconnected', this.onGattDisconnected)
    this.notifyChar = null
    this.writeChar = null
    this.active = null
  }
}
