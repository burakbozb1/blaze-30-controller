/**
 * Decides which slider values reach the amp: at most one per minIntervalMs while dragging,
 * never the same value twice, and the final value always when the finger lifts.
 */
export class SliderThrottle {
  private lastAt = Number.NEGATIVE_INFINITY
  private lastValue: number | null = null

  constructor(private readonly minIntervalMs: number, private readonly now: () => number) {}

  onChange(value: number): boolean {
    if (value === this.lastValue) return false
    const t = this.now()
    if (t - this.lastAt < this.minIntervalMs) return false
    this.lastAt = t
    this.lastValue = value
    return true
  }

  /** The amp (not this slider) set value; forget what this slider last sent so the same value can be sent again. */
  reset(value: number): void {
    this.lastValue = value
  }

  onFinished(value: number): boolean {
    if (value === this.lastValue) return false
    this.lastAt = this.now()
    this.lastValue = value
    return true
  }
}
