package dev.blaze.controller.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SliderModelTest {
    private var clock = 0L
    private fun model(initial: Int) = SliderModel(initial, SliderThrottle(60) { clock })

    @Test
    fun theFinalValueSurvivesTheAmpEchoingAnEarlierValueMidDrag() {
        val m = model(10)
        assertEquals(11, m.onDrag(11f))        // sent
        m.onExternal(11)                       // amp state echoes the sent value while the finger is still down
        clock = 20
        assertNull(m.onDrag(15f))              // throttled, not sent yet
        m.onExternal(11)                       // another echo must NOT snap the thumb back
        assertEquals(15f, m.local, 0f)
        assertEquals(15, m.onFinish())         // and the final value still reaches the amp
    }

    @Test
    fun followsExternalValueWhenNotDragging() {
        val m = model(10)
        m.onExternal(30)
        assertEquals(30f, m.local, 0f)
    }

    @Test
    fun afterTheFingerLiftsExternalValuesAreFollowedAgain() {
        val m = model(10)
        m.onDrag(12f); m.onFinish()
        m.onExternal(7)
        assertEquals(7f, m.local, 0f)
    }

    @Test
    fun tappingBackToTheLastSentValueAfterTheAmpChangedItStillSends() {
        val m = model(10)
        assertEquals(20, m.onDrag(20f)); m.onFinish()    // the app sent 20
        m.onExternal(5)                                   // then the amp moved to 5 (pot)
        clock = 1_000
        assertEquals(20, m.onDrag(20f))                   // the user taps 20 again: this must reach the amp
        assertEquals(null, m.onFinish())                  // and is not sent twice
    }

    @Test
    fun fractionalValuesAreRoundedNotTruncated() {
        val m = model(0)
        assertEquals(15, m.onDrag(14.7f))                 // shown as 15, so 15 must be what is sent
        val n = model(0)
        assertEquals(1, n.onDrag(1f))
        clock = 10
        assertNull(n.onDrag(14.7f))                       // throttled
        assertEquals(15, n.onFinish())                    // the final value is rounded the same way
    }
}
