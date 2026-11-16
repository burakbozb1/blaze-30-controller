package dev.blaze.controller.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SliderThrottleTest {
    private var clock = 0L
    private fun throttle() = SliderThrottle(minIntervalMs = 60) { clock }

    @Test fun firstChangeIsSentThenBurstIsSuppressedUntilIntervalPasses() {
        val t = throttle()
        assertTrue(t.onChange(1))
        clock = 20; assertFalse(t.onChange(2))
        clock = 59; assertFalse(t.onChange(3))
        clock = 60; assertTrue(t.onChange(4))
    }

    @Test fun sameValueIsNeverResent() {
        val t = throttle()
        assertTrue(t.onChange(5))
        clock = 500; assertFalse(t.onChange(5))
    }

    @Test fun finalValueIsAlwaysSentWhenFingerLiftsEvenInsideTheInterval() {
        val t = throttle()
        assertTrue(t.onChange(1))
        clock = 10; assertFalse(t.onChange(9))   // swallowed by the throttle
        assertTrue(t.onFinished(9))              // but the last value still reaches the amp
        assertFalse(t.onFinished(9))             // and is not sent twice
    }
}
