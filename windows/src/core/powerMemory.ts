export interface PowerMemoryOptions {
  defaultLevel?: number
  pendingMs?: number
  now?: () => number
}

/**
 * Remembers what a delay/reverb panel was set to before it was switched "off" (master level 0), so switching it
 * back on restores that level. The amp has no separate on/off flag: off simply is level 0.
 *
 * A toggle also remembers the level it just asked for for pendingMs, because the amp state only updates after the
 * write is acknowledged; without that a quick second tap would read the stale state and repeat the first tap.
 */
export class PowerMemory {
  private remembered: number | null = null
  private requested: number | null = null
  private requestedAt = 0
  private readonly defaultLevel: number
  private readonly pendingMs: number
  private readonly now: () => number

  constructor(opts: PowerMemoryOptions = {}) {
    this.defaultLevel = opts.defaultLevel ?? 20
    this.pendingMs = opts.pendingMs ?? 1000
    this.now = opts.now ?? (() => Date.now())
  }

  /** Feed every level the amp state shows (set by the app, the amp's knobs or a reconnect dump). */
  observe(level: number): void {
    if (level > 0) this.remembered = level
    if (this.requested === level) this.requested = null
  }

  /** Level to write when the user taps the power button, given the level currently shown. */
  levelAfterToggle(current: number): number {
    const pending = this.requested !== null && this.now() - this.requestedAt <= this.pendingMs ? this.requested : null
    const effective = pending ?? current
    const next = effective > 0 ? 0 : (this.remembered ?? this.defaultLevel)
    this.requested = next
    this.requestedAt = this.now()
    return next
  }
}
