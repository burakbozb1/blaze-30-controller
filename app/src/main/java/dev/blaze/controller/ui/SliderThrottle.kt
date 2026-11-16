package dev.blaze.controller.ui

/**
 * Decides which slider values reach the amp: at most one per [minIntervalMs] while dragging,
 * never the same value twice, and the final value always when the finger lifts.
 */
class SliderThrottle(private val minIntervalMs: Long, private val now: () -> Long) {
    private var lastAt = Long.MIN_VALUE / 2
    private var lastValue: Int? = null

    fun onChange(value: Int): Boolean {
        if (value == lastValue) return false
        val t = now()
        if (t - lastAt < minIntervalMs) return false
        lastAt = t
        lastValue = value
        return true
    }

    /** The amp (not this slider) set [value]; forget what this slider last sent so the same value can be sent again. */
    fun reset(value: Int) {
        lastValue = value
    }

    fun onFinished(value: Int): Boolean {
        if (value == lastValue) return false
        lastAt = now()
        lastValue = value
        return true
    }
}
