import type { AmpRepository } from './ampRepository'
import { DEFAULT_AMP_STATE, type AmpState } from './ampState'
import { MutableObservable, type Observable } from './observable'
import { PowerMemory } from './powerMemory'
import { Cmd, Ranges, type AmpCommand, type AmpType, type GuitarEffect, type ReverbType } from './protocol'
import type { Status } from './statusPresentation'
import type { AmpTransport, FoundDevice } from './transport'

export interface UiState {
  readonly status: Status
  readonly devices: readonly FoundDevice[]
  readonly amp: AmpState
  /** "On" = master level above zero (the amp has no separate on/off flag). */
  readonly delayOn: boolean
  readonly reverbOn: boolean
}

export interface AppControllerOptions {
  scanWindowMs?: number
  rescanDelayMs?: number
  maxScanWindows?: number
  /** Whether a Bluetooth adapter is usable (navigator.bluetooth.getAvailability in the app). */
  available?: () => Promise<boolean>
}

type Phase =
  | { kind: 'idle' }
  | { kind: 'noBluetooth' }
  | { kind: 'scanning' }
  | { kind: 'notFound' }
  | { kind: 'gaveUp' }
  | { kind: 'pick'; devices: FoundDevice[] }
  | { kind: 'linked' }

const sleep = (ms: number) => new Promise<void>((r) => setTimeout(r, ms))

/**
 * Everything the screen needs and every action it can take. Mirrors the Android HomeViewModel: it finds the amp
 * (bounded scan windows), links it through the repository and exposes a single UiState.
 */
export class AppController {
  private readonly _ui = new MutableObservable<UiState>({
    status: 'starting', devices: [], amp: DEFAULT_AMP_STATE, delayOn: false, reverbOn: false,
  })
  readonly ui: Observable<UiState> = this._ui

  private phase: Phase = { kind: 'idle' }
  private bluetoothAvailable: boolean | null = null
  private scanToken = 0
  private readonly delayPower = new PowerMemory()
  private readonly reverbPower = new PowerMemory()
  private readonly scanWindowMs: number
  private readonly rescanDelayMs: number
  private readonly maxScanWindows: number
  private readonly available: () => Promise<boolean>

  constructor(private readonly repo: AmpRepository, private readonly transport: AmpTransport, o: AppControllerOptions = {}) {
    this.scanWindowMs = o.scanWindowMs ?? 4000
    this.rescanDelayMs = o.rescanDelayMs ?? 3000
    this.maxScanWindows = o.maxScanWindows ?? 8
    this.available = o.available ?? (async () => true)
    repo.state.subscribe((s) => {
      // Every level the amp state shows (also those set on the amp) feeds the power memories.
      this.delayPower.observe(s.masterDelay)
      this.reverbPower.observe(s.masterReverb)
      this.publish()
    })
    repo.connection.subscribe(() => this.publish())
    repo.gaveUp.subscribe(() => this.publish())
    this.publish()
  }

  /** Checks the adapter, then starts looking for the amp. */
  async start(): Promise<void> {
    let ok = false
    try { ok = await this.available() } catch { ok = false }
    this.onBluetoothAvailable(ok)
  }

  onBluetoothAvailable(ok: boolean): void {
    this.bluetoothAvailable = ok
    if (!ok) {
      this.stopAll({ kind: 'noBluetooth' })
    } else if (this.phase.kind === 'idle' || this.phase.kind === 'noBluetooth') {
      this.startScan()
    }
  }

  /** Stop scanning and the repository's reconnect loop; the next scan starts when the blocker clears. */
  private stopAll(next: Phase): void {
    this.scanToken++
    this.repo.disconnect()
    this.setPhase(next)
  }

  /** Manual retry after the app gave up: drop any stale link state, then look for the amp afresh. */
  retry(): void {
    this.repo.disconnect()
    this.startScan()
  }

  pick(device: FoundDevice): void {
    this.connectTo(device)
  }

  /** The window came back to the foreground: re-read the amp so the screen matches it again. */
  refresh(): void {
    this.repo.refresh()
  }

  /**
   * Scans in windows until the amp shows up. A single miss is normal (the amp may still be holding a stale link
   * and not advertising), so after the first empty window we show "not found" but keep looking, for a bounded
   * number of windows; then we stop and leave it to a manual retry.
   */
  private startScan(): void {
    const token = ++this.scanToken
    this.setPhase({ kind: 'scanning' })
    void (async () => {
      let windows = 0
      while (token === this.scanToken) {
        const found = await this.scanOnce()
        if (token !== this.scanToken) return
        windows++
        if (found.length === 0) {
          if (windows >= this.maxScanWindows) { this.setPhase({ kind: 'gaveUp' }); return }
          this.setPhase({ kind: 'notFound' })
          await sleep(this.rescanDelayMs)
        } else if (found.length === 1) {
          this.connectTo(found[0])
          return
        } else {
          this.setPhase({ kind: 'pick', devices: found })
          return
        }
      }
    })()
  }

  private async scanOnce(): Promise<FoundDevice[]> {
    const found: FoundDevice[] = []
    const ac = new AbortController()
    let timer: ReturnType<typeof setTimeout> | undefined
    const timeout = new Promise<void>((res) => { timer = setTimeout(() => { ac.abort(); res() }, this.scanWindowMs) })
    const collect = (async () => {
      try {
        for await (const d of this.transport.scan(ac.signal)) {
          if (!found.some((f) => f.address === d.address)) found.push(d)
        }
      } catch {
        found.length = 0 // scan unavailable (adapter went off, chooser cancelled) -> treated as not found, retried
      }
    })()
    await Promise.race([collect, timeout])
    clearTimeout(timer)
    return found
  }

  private connectTo(device: FoundDevice): void {
    this.setPhase({ kind: 'linked' })
    this.repo.connect(device)
  }

  private setPhase(p: Phase): void {
    this.phase = p
    this.publish()
  }

  private publish(): void {
    const p = this.phase
    const conn = this.repo.connection.value
    let status: Status
    switch (p.kind) {
      case 'idle': status = 'starting'; break
      case 'noBluetooth': status = 'noBluetooth'; break
      case 'scanning': status = 'scanning'; break
      case 'notFound': status = 'notFound'; break
      case 'gaveUp': status = 'gaveUp'; break
      case 'pick': status = 'pick'; break
      case 'linked':
        status = conn.kind === 'connected' ? 'connected'
          : conn.kind === 'connecting' ? 'connecting'
            : this.repo.gaveUp.value ? 'linkLost' : 'reconnecting'
        break
    }
    const amp = this.repo.state.value
    this._ui.value = {
      status,
      devices: p.kind === 'pick' ? p.devices : [],
      amp,
      delayOn: amp.masterDelay > 0,
      reverbOn: amp.masterReverb > 0,
    }
  }

  // ---- actions (same names as the Android AmpActions) ----
  private send(c: AmpCommand): void { this.repo.send(c) }
  onTone(t: AmpType): void { this.send(Cmd.setAmpType(t)) }
  setGain(v: number): void { this.send(Cmd.setGain(v)) }
  setVolume(v: number): void { this.send(Cmd.setVolume(v)) }
  setAuxVolume(v: number): void { this.send(Cmd.setAuxVolume(v)) }
  setEq(index: number, level: number): void { this.send(Cmd.setEqBand(index, level)) }
  setMasterDelay(v: number): void { this.send(Cmd.setMasterDelay(v)) }
  setMasterReverb(v: number): void { this.send(Cmd.setMasterReverb(v)) }
  setEffect(e: GuitarEffect): void { this.send(Cmd.setEffect(e)) }
  setReverbType(t: ReverbType): void { this.send(Cmd.setReverbType(t)) }
  setReverbLevel(v: number): void { this.send(Cmd.setReverbLevel(v)) }
  setReverbTone(v: number): void { this.send(Cmd.setReverbTone(v)) }
  setReverbTime(v: number): void { this.send(Cmd.setReverbTime(v)) }
  setDelayLevel(v: number): void { this.send(Cmd.setDelayLevel(v)) }
  setDelayFeedback(v: number): void { this.send(Cmd.setDelayFeedback(v)) }
  setDelayTime(v: number): void { this.send(Cmd.setDelayTime(v)) }

  /** Power buttons: off = master level 0 (previous level remembered), on = restore it. */
  toggleDelay(): void { this.setMasterDelay(this.delayPower.levelAfterToggle(this.repo.state.value.masterDelay)) }
  toggleReverb(): void { this.setMasterReverb(this.reverbPower.levelAfterToggle(this.repo.state.value.masterReverb)) }

  /** EQ reset: all seven bands back to the centre (10). */
  resetEq(): void {
    for (let band = 0; band < Ranges.EQ_BANDS; band++) this.send(Cmd.setEqBand(band, 10))
  }
}
