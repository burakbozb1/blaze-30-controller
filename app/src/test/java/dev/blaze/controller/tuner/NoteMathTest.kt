package dev.blaze.controller.tuner

import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class NoteMathTest {

    private fun reading(hz: Double): NoteReading = NoteMath.fromFrequency(hz).also { assertNotNull(it) }!!

    @Test
    fun a440IsA4InTuneAndNotAGuitarString() {
        val r = reading(440.0)
        assertEquals("A", r.name)
        assertEquals(4, r.octave)
        assertEquals(0.0, r.cents, 0.01)
        assertNull(r.string) // 5 semitones above the high E string
    }

    @Test
    fun openStringsMapToTheirNameOctaveAndNumber() {
        val expected = listOf(
            Triple(82.41, "E" to 2, 6), Triple(110.0, "A" to 2, 5), Triple(146.83, "D" to 3, 4),
            Triple(196.0, "G" to 3, 3), Triple(246.94, "B" to 3, 2), Triple(329.63, "E" to 4, 1),
        )
        for ((hz, note, string) in expected) {
            val r = reading(hz)
            assertEquals("name at $hz", note.first, r.name)
            assertEquals("octave at $hz", note.second, r.octave)
            assertEquals("string at $hz", string, r.string)
            assertEquals("cents at $hz", 0.0, r.cents, 0.5)
        }
    }

    @Test
    fun centsFollowTheSignOfTheDeviation() {
        assertEquals(30.0, reading(82.41 * 2.0.pow(30.0 / 1200)).cents, 0.2)
        assertEquals(-30.0, reading(82.41 * 2.0.pow(-30.0 / 1200)).cents, 0.2)
    }

    @Test
    fun sharpsUseDiezNames() {
        val r = reading(116.54)
        assertEquals("A#", r.name)
        assertEquals(2, r.octave)
    }

    @Test
    fun aPitchBetweenTwoStringsPicksTheNearerOne() {
        assertEquals(5, reading(100.0).string) // 43.35 semitones: closer to A2 (45) than E2 (40)
    }

    @Test
    fun nonPositiveOrNaNFrequenciesGiveNothing() {
        assertNull(NoteMath.fromFrequency(0.0))
        assertNull(NoteMath.fromFrequency(-5.0))
        assertNull(NoteMath.fromFrequency(Double.NaN))
    }
}
