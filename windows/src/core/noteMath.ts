/**
 * One tuner reading. cents is the deviation from the nearest note in [-50, 50]; string is the guitar string
 * (6 = low E ... 1 = high E) when the pitch is close to a standard-tuning string, else null.
 */
export interface NoteReading {
  readonly name: string
  readonly octave: number
  readonly cents: number
  readonly frequency: number
  readonly string: number | null
}

export const A4_HZ = 440
const MAX_STRING_DISTANCE_SEMITONES = 2.5
const names = ['C', 'C#', 'D', 'D#', 'E', 'F', 'F#', 'G', 'G#', 'A', 'A#', 'B']

/** MIDI numbers of the standard-tuning strings E2 A2 D3 G3 B3 E4, keyed by string number 6..1. */
const standardStrings: ReadonlyArray<readonly [number, number]> = [[6, 40], [5, 45], [4, 50], [3, 55], [2, 59], [1, 64]]

export function noteFromFrequency(hz: number): NoteReading | null {
  if (Number.isNaN(hz) || hz <= 0) return null
  const midi = 69 + (12 * Math.log(hz / A4_HZ)) / Math.log(2)
  const nearest = Math.round(midi)
  let best: readonly [number, number] = standardStrings[0]
  for (const s of standardStrings) if (Math.abs(midi - s[1]) < Math.abs(midi - best[1])) best = s
  const string = Math.abs(midi - best[1]) <= MAX_STRING_DISTANCE_SEMITONES ? best[0] : null
  return {
    name: names[((nearest % 12) + 12) % 12],
    octave: Math.floor(nearest / 12) - 1,
    cents: 100 * (midi - nearest),
    frequency: hz,
    string,
  }
}
