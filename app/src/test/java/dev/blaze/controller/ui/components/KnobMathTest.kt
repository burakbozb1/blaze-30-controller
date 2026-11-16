package dev.blaze.controller.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class KnobMathTest {

    @Test
    fun sweepIsLinearOver270Degrees() {
        assertEquals(0f, KnobMath.sweepDegrees(0, 0..50), 0.001f)
        assertEquals(270f, KnobMath.sweepDegrees(50, 0..50), 0.001f)
        assertEquals(135f, KnobMath.sweepDegrees(25, 0..50), 0.001f)
        assertEquals(135f, KnobMath.sweepDegrees(15, 0..30), 0.001f)
        assertEquals(0f, KnobMath.sweepDegrees(-3, 0..50), 0.001f)   // out of range is clamped
        assertEquals(270f, KnobMath.sweepDegrees(99, 0..50), 0.001f)
    }

    @Test
    fun dragOfOneFullTravelSweepsTheWholeRange() {
        assertEquals(10, KnobMath.valueAfterDrag(10, 0f, 0..50))
        assertEquals(50, KnobMath.valueAfterDrag(0, 220f, 0..50))
        assertEquals(0, KnobMath.valueAfterDrag(50, -220f, 0..50))
        assertEquals(45, KnobMath.valueAfterDrag(20, 110f, 0..50))
    }

    @Test
    fun dragIsClampedToTheRange() {
        assertEquals(50, KnobMath.valueAfterDrag(40, 500f, 0..50))
        assertEquals(0, KnobMath.valueAfterDrag(5, -500f, 0..50))
    }

    @Test
    fun smallDragsRoundToTheNearestStep() {
        assertEquals(10, KnobMath.valueAfterDrag(10, 2f, 0..20))   // 0.18 of a step: no change
        assertEquals(11, KnobMath.valueAfterDrag(10, 6f, 0..20))   // 0.55 of a step: next value
        assertEquals(9, KnobMath.valueAfterDrag(10, -6f, 0..20))
    }

    @Test
    fun stepMovesByDeltaAndStaysInTheRange() {
        assertEquals(50, KnobMath.step(50, 1, 0..50))
        assertEquals(0, KnobMath.step(0, -1, 0..50))
        assertEquals(9, KnobMath.step(10, -1, 0..50))
        assertEquals(11, KnobMath.step(10, 1, 0..50))
    }
}
