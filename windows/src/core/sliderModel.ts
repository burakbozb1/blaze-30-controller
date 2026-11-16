import type { SliderThrottle } from './sliderThrottle'

/**
 * Slider state machine. While the finger is down the thumb follows the finger, never the amp state
 * (the amp echoes each sent value back and would otherwise snap the thumb to an older value and lose the
 * final one). Outside a drag it follows the amp state.
 */
export class SliderModel {
  local: number
  private dragging = false

  constructor(initial: number, private readonly throttle: SliderThrottle) {
    this.local = initial
  }

  /** Returns the value to send now, or null if the throttle holds it back. */
  onDrag(value: number): number | null {
    this.dragging = true
    this.local = value
    const v = Math.round(value)
    return this.throttle.onChange(v) ? v : null
  }

  /** Finger lifted: returns the final value if it has not been sent yet. */
  onFinish(): number | null {
    const v = Math.round(this.local)
    const result = this.throttle.onFinished(v) ? v : null
    this.dragging = false
    return result
  }

  onExternal(value: number): void {
    if (!this.dragging) {
      this.local = value
      this.throttle.reset(value)
    }
  }
}
