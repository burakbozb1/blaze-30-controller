import { DEFAULT_AMP_STATE, applyCommand, type AmpState } from './ampState'
import { MutableObservable, type Observable } from './observable'
import { Cmd, decode, encode, type AmpCommand } from './protocol'
import type { AmpTransport, ConnectionState, FoundDevice } from './transport'

export interface RepositoryOptions {
  reconnectDelayMs?: number
  syncSettleMs?: number
  maxReconnectAttempts?: number
  maxReconnectDelayMs?: number
}

const sleep = (ms: number) => (ms <= 0 ? Promise.resolve() : new Promise<void>((r) => setTimeout(r, ms)))

/**
 * App-facing amp controller: queues commands so they reach the amp in tap order, mirrors what the app sends and
 * what the amp reports into [state], and keeps the link alive. After a dropped link it reconnects and re-syncs from
 * the amp; it never pushes app values (or defaults) to the amp on its own.
 */
export class AmpRepository {
  private readonly _state = new MutableObservable<AmpState>(DEFAULT_AMP_STATE)
  readonly state: Observable<AmpState> = this._state
  readonly connection: Observable<ConnectionState>

  /** True once automatic reconnecting has been abandoned; the UI then offers a manual retry. */
  private readonly _gaveUp = new MutableObservable<boolean>(false)
  readonly gaveUp: Observable<boolean> = this._gaveUp

  private readonly queue: AmpCommand[] = []
  private pumping = false
  private device: FoundDevice | null = null
  private wantConnected = false
  private reconnectToken = 0
  private reconnecting = false

  private readonly reconnectDelayMs: number
  private readonly syncSettleMs: number
  private readonly maxReconnectAttempts: number
  private readonly maxReconnectDelayMs: number

  constructor(private readonly transport: AmpTransport, opts: RepositoryOptions = {}) {
    this.reconnectDelayMs = opts.reconnectDelayMs ?? 1000
    this.syncSettleMs = opts.syncSettleMs ?? 150
    this.maxReconnectAttempts = opts.maxReconnectAttempts ?? 8
    this.maxReconnectDelayMs = opts.maxReconnectDelayMs ?? 10_000
    this.connection = transport.connection
    transport.connection.subscribe((s) => this.onConnection(s))
    // Read-only mirror of what the amp reports; never written back.
    transport.onIncoming((frame) => {
      for (const cmd of decode(frame)) this._state.value = applyCommand(this._state.value, cmd)
    })
  }

  connect(device: FoundDevice): void {
    this.device = device
    this.wantConnected = true
    this._gaveUp.value = false
    void this.transport.connect(device).catch(() => {})
  }

  reconnect(): void {
    if (this.device) this.connect(this.device)
  }

  disconnect(): void {
    this.wantConnected = false
    this.reconnectToken++
    this.reconnecting = false
    this.transport.disconnect()
  }

  /** Asks the amp for a fresh full state dump (it answers the sync frame with one). No-op when not connected. */
  refresh(): void {
    if (this.transport.connection.value.kind === 'connected') this.enqueue(Cmd.sync)
  }

  send(cmd: AmpCommand): void {
    this.enqueue(cmd)
  }

  private enqueue(cmd: AmpCommand): void {
    this.queue.push(cmd)
    void this.pump()
  }

  private async pump(): Promise<void> {
    if (this.pumping) return
    this.pumping = true
    try {
      for (let cmd = this.queue.shift(); cmd; cmd = this.queue.shift()) {
        try {
          await this.deliver(cmd)
        } catch {
          // A write can time out or be rejected: drop that command, keep serving the queue.
        }
      }
    } finally {
      this.pumping = false
    }
  }

  private async deliver(cmd: AmpCommand): Promise<void> {
    if (this.transport.connection.value.kind !== 'connected') return
    await this.transport.write(encode(cmd))
    if (cmd.kind === 'Sync') {
      await sleep(this.syncSettleMs) // give the amp a moment to open its session before the next frame
      return
    }
    this._state.value = applyCommand(this._state.value, cmd)
  }

  private onConnection(s: ConnectionState): void {
    switch (s.kind) {
      case 'connected':
        this.reconnectToken++
        this.reconnecting = false
        this._gaveUp.value = false
        // A new link always starts with the sync; the amp answers with its full state and that is the truth.
        // Nothing the app sent earlier is replayed: it could overwrite knob changes made on the amp meanwhile.
        this.enqueue(Cmd.sync)
        break
      case 'disconnected':
      case 'failed':
        this.startReconnectLoop()
        break
      case 'connecting':
        break
    }
  }

  /** Retries with growing pauses (base, x2, ... capped); after maxReconnectAttempts it stops and sets gaveUp. */
  private startReconnectLoop(): void {
    const d = this.device
    if (!d || !this.wantConnected || this._gaveUp.value || this.reconnecting) return
    this.reconnecting = true
    const token = ++this.reconnectToken
    void (async () => {
      let attempts = 0
      let pause = this.reconnectDelayMs
      while (token === this.reconnectToken && this.wantConnected && this.transport.connection.value.kind !== 'connected') {
        if (attempts >= this.maxReconnectAttempts) {
          this._gaveUp.value = true
          break
        }
        await sleep(pause)
        if (token !== this.reconnectToken || !this.wantConnected) break
        try {
          await this.transport.connect(d)
        } catch {
          /* the connection state carries the failure */
        }
        attempts++
        pause = Math.min(pause * 2, this.maxReconnectDelayMs)
      }
      if (token === this.reconnectToken) this.reconnecting = false
    })()
  }
}
