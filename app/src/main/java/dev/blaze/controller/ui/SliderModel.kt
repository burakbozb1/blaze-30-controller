package dev.blaze.controller.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt

/**
 * Slider state machine. While the finger is down the thumb follows the finger, never the amp state
 * (the amp echoes each sent value back and would otherwise snap the thumb to an older value and lose the
 * final one). Outside a drag it follows the amp state.
 */
class SliderModel(initial: Int, private val throttle: SliderThrottle) {
    var local by mutableFloatStateOf(initial.toFloat())
        private set
    private var dragging = false

    /** Returns the value to send now, or null if the throttle holds it back. */
    fun onDrag(value: Float): Int? {
        dragging = true
        local = value
        return value.roundToInt().takeIf { throttle.onChange(it) }
    }

    /** Finger lifted: returns the final value if it has not been sent yet. */
    fun onFinish(): Int? {
        val result = local.roundToInt().takeIf { throttle.onFinished(it) }
        dragging = false
        return result
    }

    fun onExternal(value: Int) {
        if (!dragging) {
            local = value.toFloat()
            throttle.reset(value)
        }
    }
}
