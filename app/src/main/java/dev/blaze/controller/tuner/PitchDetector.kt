package dev.blaze.controller.tuner

import kotlin.math.min
import kotlin.math.sqrt

/**
 * Monophonic pitch detector (YIN: de Cheveigné & Kawahara 2002) for guitar frames of 16-bit PCM.
 * Returns the fundamental frequency in Hz, or null when there is no clear pitch (silence, noise, out of range).
 */
class PitchDetector(
    val sampleRate: Int = 44_100,
    val minHz: Double = 55.0,
    val maxHz: Double = 1_100.0,
    val threshold: Double = 0.15,
    val minRms: Double = 0.01,
) {
    fun detect(samples: ShortArray): Double? {
        val window = samples.size / 2
        val tauMax = min((sampleRate / minHz).toInt(), window - 2)
        val tauMin = maxOf(2, (sampleRate / maxHz).toInt())
        if (tauMax <= tauMin + 2) return null

        // Gate: too quiet to be a played string (room noise, hum, a distant sound).
        var energy = 0.0
        for (s in samples) {
            val v = s / 32768.0
            energy += v * v
        }
        if (sqrt(energy / samples.size) < minRms) return null

        // Difference function d(tau) and its cumulative-mean-normalised form d'(tau).
        val cmnd = DoubleArray(tauMax + 2)
        cmnd[0] = 1.0
        var running = 0.0
        for (tau in 1..tauMax + 1) {
            var sum = 0.0
            for (j in 0 until window) {
                val diff = (samples[j] - samples[j + tau]).toDouble()
                sum += diff * diff
            }
            running += sum
            cmnd[tau] = if (running == 0.0) 1.0 else sum * tau / running
        }

        // First dip below the threshold, followed down to its local minimum.
        var tau = tauMin
        while (tau <= tauMax && cmnd[tau] >= threshold) tau++
        if (tau > tauMax) return null
        while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++

        // Parabolic interpolation for sub-sample accuracy.
        val s0 = cmnd[tau - 1]
        val s1 = cmnd[tau]
        val s2 = cmnd[tau + 1]
        val denominator = 2.0 * (s0 - 2.0 * s1 + s2)
        val refined = if (denominator == 0.0) tau.toDouble() else tau + (s0 - s2) / denominator

        val hz = sampleRate / refined
        return hz.takeIf { it in minHz..maxHz }
    }
}
