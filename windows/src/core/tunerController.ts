import { MutableObservable, type Observable } from './observable'
import { noteFromFrequency, type NoteReading } from './noteMath'
import { PitchDetector } from './pitchDetector'

/** Source of microphone frames (4096 16-bit samples each). Must end (and release the microphone) when the signal aborts. */
export interface AudioSource {
  frames(signal: AbortSignal): AsyncIterable<Int16Array>
}

export type TunerUi =
  | { kind: 'nosignal' }
  | { kind: 'reading'; note: NoteReading }
  | { kind: 'error'; message: string }

export interface TunerOptions {
  detector?: PitchDetector
  holdFrames?: number
  medianSize?: number
  minDetections?: number
}

/**
 * Turns microphone frames into what the tuner screen shows. A glitch frame cannot flip the display (median of the
 * last medianSize detections, and nothing is shown before minDetections have been collected), the last note is
 * held for holdFrames silent frames, and nothing is shown for silence or noise. Aborting run's signal releases the microphone.
 */
export class TunerController {
  private readonly _ui = new MutableObservable<TunerUi>({ kind: 'nosignal' })
  readonly ui: Observable<TunerUi> = this._ui
  private readonly detector: PitchDetector
  private readonly holdFrames: number
  private readonly medianSize: number
  private readonly minDetections: number

  constructor(private readonly source: AudioSource, o: TunerOptions = {}) {
    this.detector = o.detector ?? new PitchDetector()
    this.holdFrames = o.holdFrames ?? 5
    this.medianSize = o.medianSize ?? 5
    this.minDetections = o.minDetections ?? 3
  }

  /** Listens until aborted. Never rejects; failures end in the error state. */
  async run(signal: AbortSignal): Promise<void> {
    this._ui.value = { kind: 'nosignal' }
    const recent: number[] = []
    let missing = 0
    try {
      for await (const frame of this.source.frames(signal)) {
        if (signal.aborted) break
        const hz = this.detector.detect(frame)
        if (hz === null) {
          if (++missing >= this.holdFrames) {
            recent.length = 0
            this._ui.value = { kind: 'nosignal' }
          }
        } else {
          missing = 0
          recent.push(hz)
          if (recent.length > this.medianSize) recent.shift()
          // A pluck attack or a stray sound gives one or two wrong frames: show nothing until a few agree.
          if (recent.length >= this.minDetections) {
            const note = noteFromFrequency(median(recent))
            if (note) this._ui.value = { kind: 'reading', note }
          }
        }
      }
    } catch {
      if (!signal.aborted) this._ui.value = { kind: 'error', message: 'Mikrofon kullanılamıyor' }
    }
  }
}

/** A value that was actually detected (the lower middle for an even count), never an average of two. */
function median(values: number[]): number {
  return [...values].sort((a, b) => a - b)[Math.floor((values.length - 1) / 2)]
}
