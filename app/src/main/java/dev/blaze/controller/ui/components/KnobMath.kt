package dev.blaze.controller.ui.components

import kotlin.math.roundToInt

/** Pure geometry/value rules of the knobs, kept free of Compose so they are unit-tested on the JVM. */
object KnobMath {
    /** Vertical finger travel (dp) that sweeps a knob through its whole range. */
    const val FULL_RANGE_DP = 220f

    /** The arc covers 270 degrees (from bottom-left, clockwise, to bottom-right). */
    const val SWEEP_DEGREES = 270f

    /** Angle of [value] within the 270-degree sweep: 0 at the range start, [SWEEP_DEGREES] at the end. */
    fun sweepDegrees(value: Int, range: IntRange): Float {
        val span = (range.last - range.first).coerceAtLeast(1)
        val clamped = value.coerceIn(range)
        return (clamped - range.first).toFloat() / span * SWEEP_DEGREES
    }

    /** New value after an upward drag of [upwardDragDp] starting from [startValue] (downward drags are negative). */
    fun valueAfterDrag(startValue: Int, upwardDragDp: Float, range: IntRange): Int {
        val span = (range.last - range.first).toFloat()
        val raw = startValue + upwardDragDp / FULL_RANGE_DP * span
        return raw.roundToInt().coerceIn(range)
    }

    /** One popup step up or down, clamped to the range. */
    fun step(value: Int, delta: Int, range: IntRange): Int = (value + delta).coerceIn(range)
}
