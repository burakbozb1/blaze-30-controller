export interface PitchDetectorOptions {
  sampleRate?: number
  minHz?: number
  maxHz?: number
  threshold?: number
  minRms?: number
}

/**
 * Monophonic pitch detector (YIN: de Cheveigne & Kawahara 2002) for guitar frames of 16-bit PCM.
 * Returns the fundamental frequency in Hz, or null when there is no clear pitch (silence, noise, out of range).
 */
export class PitchDetector {
  readonly sampleRate: number
  readonly minHz: number
  readonly maxHz: number
  readonly threshold: number
  readonly minRms: number

  constructor(o: PitchDetectorOptions = {}) {
    this.sampleRate = o.sampleRate ?? 44_100
    this.minHz = o.minHz ?? 55
    this.maxHz = o.maxHz ?? 1_100
    this.threshold = o.threshold ?? 0.15
    this.minRms = o.minRms ?? 0.01
  }

  detect(samples: Int16Array): number | null {
    const window = Math.floor(samples.length / 2)
    const tauMax = Math.min(Math.floor(this.sampleRate / this.minHz), window - 2)
    const tauMin = Math.max(2, Math.floor(this.sampleRate / this.maxHz))
    if (tauMax <= tauMin + 2) return null

    // Gate: too quiet to be a played string (room noise, hum, a distant sound).
    let energy = 0
    for (let i = 0; i < samples.length; i++) {
      const v = samples[i] / 32768
      energy += v * v
    }
    if (Math.sqrt(energy / samples.length) < this.minRms) return null

    // Difference function d(tau) and its cumulative-mean-normalised form d'(tau).
    const cmnd = new Float64Array(tauMax + 2)
    cmnd[0] = 1
    let running = 0
    for (let tau = 1; tau <= tauMax + 1; tau++) {
      let sum = 0
      for (let j = 0; j < window; j++) {
        const diff = samples[j] - samples[j + tau]
        sum += diff * diff
      }
      running += sum
      cmnd[tau] = running === 0 ? 1 : (sum * tau) / running
    }

    // First dip below the threshold, followed down to its local minimum.
    let tau = tauMin
    while (tau <= tauMax && cmnd[tau] >= this.threshold) tau++
    if (tau > tauMax) return null
    while (tau + 1 <= tauMax && cmnd[tau + 1] < cmnd[tau]) tau++

    // Parabolic interpolation for sub-sample accuracy.
    const s0 = cmnd[tau - 1]
    const s1 = cmnd[tau]
    const s2 = cmnd[tau + 1]
    const denominator = 2 * (s0 - 2 * s1 + s2)
    const refined = denominator === 0 ? tau : tau + (s0 - s2) / denominator

    const hz = this.sampleRate / refined
    return hz >= this.minHz && hz <= this.maxHz ? hz : null
  }
}
