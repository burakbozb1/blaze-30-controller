package dev.blaze.controller.tuner

import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PitchDetectorTest {

    private val sr = 44_100
    private val detector = PitchDetector()

    /** Sum of harmonics ([amplitudes] for partials 1..n, scaled by [amp] of full scale), hard-clipped to 16 bit. */
    private fun tone(hz: Double, amp: Double = 0.3, amplitudes: List<Double> = listOf(1.0), n: Int = 4096): ShortArray =
        ShortArray(n) { i ->
            val t = i.toDouble() / sr
            val v = amplitudes.withIndex().sumOf { (h, a) -> a * sin(2 * PI * (h + 1) * hz * t) } * amp
            (v.coerceIn(-1.0, 1.0) * 32767).toInt().toShort()
        }

    private fun centsBetween(measured: Double, expected: Double) = 1200.0 * ln(measured / expected) / ln(2.0)

    private fun assertWithin(cents: Double, expectedHz: Double, samples: ShortArray, label: String = "") {
        val hz = detector.detect(samples)
        assertNotNull("no pitch for $expectedHz Hz $label", hz)
        assertTrue("$label: ${hz} Hz is ${centsBetween(hz!!, expectedHz)} cents from $expectedHz", abs(centsBetween(hz, expectedHz)) <= cents)
    }

    @Test
    fun detectsEachStandardStringWithinOneCent() {
        for (hz in listOf(82.41, 110.0, 146.83, 196.0, 246.94, 329.63)) assertWithin(1.0, hz, tone(hz))
    }

    @Test
    fun detectsDetunedLowEOnBothSides() {
        for (cents in listOf(-40.0, -15.0, 15.0, 40.0)) {
            val hz = 82.41 * 2.0.pow(cents / 1200)
            assertWithin(1.5, hz, tone(hz), "detuned $cents")
        }
    }

    @Test
    fun lowEWithAStrongSecondHarmonicStillGivesTheFundamental() {
        assertWithin(2.0, 82.41, tone(82.41, amp = 0.3, amplitudes = listOf(0.15, 0.45, 0.25, 0.10)))
    }

    @Test
    fun guitarLikeSawtoothOnA2() {
        assertWithin(2.0, 110.0, tone(110.0, amp = 0.2, amplitudes = (1..8).map { 1.0 / it }))
    }

    @Test
    fun clippedLoudSineStillDetects() {
        assertWithin(3.0, 110.0, tone(110.0, amp = 1.5))
    }

    @Test
    fun silenceAndVeryQuietSignalsGiveNothing() {
        assertNull(detector.detect(ShortArray(4096)))
        assertNull(detector.detect(tone(110.0, amp = 0.002)))
    }

    @Test
    fun whiteNoiseGivesNothing() {
        val rnd = Random(1)
        val noise = ShortArray(4096) { ((rnd.nextDouble() * 2 - 1) * 0.3 * 32767).toInt().toShort() }
        assertNull(detector.detect(noise))
    }

    @Test
    fun mainsHumBelowTheGuitarRangeGivesNothing() {
        assertNull(detector.detect(tone(50.0)))
    }

    @Test
    fun neverReportsAPitchAboveTheDetectionRange() {
        for (hz in listOf(1500.0, 3000.0, 6000.0)) {
            val r = detector.detect(tone(hz))
            assertTrue("$hz Hz reported as $r", r == null || r <= detector.maxHz)
        }
    }
}
