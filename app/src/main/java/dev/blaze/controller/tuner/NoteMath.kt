package dev.blaze.controller.tuner

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln

/**
 * One tuner reading. [cents] is the deviation from the nearest note in [-50, 50]; [string] is the guitar string
 * (6 = low E ... 1 = high E) when the pitch is close to a standard-tuning string, else null.
 */
data class NoteReading(
    val name: String,
    val octave: Int,
    val cents: Double,
    val frequency: Double,
    val string: Int?,
)

object NoteMath {
    const val A4_HZ = 440.0
    private const val MAX_STRING_DISTANCE_SEMITONES = 2.5

    private val names = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    /** MIDI numbers of the standard-tuning strings E2 A2 D3 G3 B3 E4, indexed by string number 6..1. */
    private val standardStrings = mapOf(6 to 40, 5 to 45, 4 to 50, 3 to 55, 2 to 59, 1 to 64)

    fun fromFrequency(hz: Double): NoteReading? {
        if (hz.isNaN() || hz <= 0.0) return null
        val midi = 69.0 + 12.0 * ln(hz / A4_HZ) / ln(2.0)
        val nearest = Math.round(midi).toInt()
        val string = standardStrings.minByOrNull { abs(midi - it.value) }
            ?.takeIf { abs(midi - it.value) <= MAX_STRING_DISTANCE_SEMITONES }?.key
        return NoteReading(
            name = names[Math.floorMod(nearest, 12)],
            octave = floor(nearest / 12.0).toInt() - 1,
            cents = 100.0 * (midi - nearest),
            frequency = hz,
            string = string,
        )
    }
}
