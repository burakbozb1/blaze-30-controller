import { MutableObservable, type Observable } from './observable'
import { CONNECTED, DISCONNECTED, type AmpTransport, type ConnectionState, type FoundDevice } from './transport'

/** Test double (also used by the UI tests): records written frames and lets tests drop the link. */
export class FakeTransport implements AmpTransport {
  private readonly _connection = new MutableObservable<ConnectionState>(DISCONNECTED)
  readonly connection: Observable<ConnectionState> = this._connection
  private listeners = new Set<(f: Uint8Array) => void>()

  readonly written: Uint8Array[] = []
  connectCalls = 0
  scanCalls = 0

  /** When true, connect() leaves the link in 'failed' instead of connected. */
  failConnects = false

  /** Thrown (rejected) by the next write() only (e.g. a write timeout). */
  failNextWrite: unknown = null

  constructor(public devices: FoundDevice[] = []) {}

  onIncoming(cb: (frame: Uint8Array) => void): () => void {
    this.listeners.add(cb)
    return () => this.listeners.delete(cb)
  }

  /** Simulates a notification from the amp. */
  emitFromAmp(bytes: Uint8Array): void {
    const copy = bytes.slice()
    for (const l of [...this.listeners]) l(copy.slice())
  }

  async *scan(signal: AbortSignal): AsyncIterable<FoundDevice> {
    this.scanCalls++
    for (const d of this.devices) {
      if (signal.aborted) return
      yield d
    }
  }

  async connect(_device: FoundDevice): Promise<void> {
    this.connectCalls++
    this._connection.value = this.failConnects ? { kind: 'failed', reason: 'test' } : CONNECTED
  }

  async write(bytes: Uint8Array): Promise<void> {
    if (this._connection.value.kind !== 'connected') throw new Error('not connected')
    if (this.failNextWrite !== null) {
      const e = this.failNextWrite
      this.failNextWrite = null
      throw e
    }
    this.written.push(bytes.slice())
  }

  disconnect(): void {
    this._connection.value = DISCONNECTED
  }

  /** Simulates the amp powering off / going out of range. */
  dropConnection(): void {
    this._connection.value = DISCONNECTED
  }
}
