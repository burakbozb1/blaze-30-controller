import type { Range } from './protocol'

/** Pure geometry/value rules of the knobs, kept free of the DOM so they are unit-tested headless. */
export const KnobMath = {
  /** Vertical finger travel (px) that sweeps a knob through its whole range. */
  FULL_RANGE_PX: 220,

  /** The arc covers 270 degrees (from bottom-left, clockwise, to bottom-right). */
  SWEEP_DEGREES: 270,

  /** Angle of value within the 270-degree sweep: 0 at the range start, SWEEP_DEGREES at the end. */
  sweepDegrees(value: number, range: Range): number {
    const span = Math.max(range.max - range.min, 1)
    const clamped = Math.min(range.max, Math.max(range.min, value))
    return ((clamped - range.min) / span) * 270
  },

  /** New value after an upward drag of upwardDragPx starting from startValue (downward drags are negative). */
  valueAfterDrag(startValue: number, upwardDragPx: number, range: Range): number {
    const span = range.max - range.min
    const raw = startValue + (upwardDragPx / 220) * span
    return Math.min(range.max, Math.max(range.min, Math.round(raw))) + 0
  },

  /** One popup step up or down, clamped to the range. */
  step(value: number, delta: number, range: Range): number {
    return Math.min(range.max, Math.max(range.min, value + delta)) + 0
  },
}
