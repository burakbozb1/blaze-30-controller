import { AmpType, GuitarEffect, ReverbType, Ranges, clamp, type AmpCommand } from './protocol'

/** Single source of truth for what the app believes the amp is set to. Values mirror what the codec sends (clamped). */
export interface AmpState {
  readonly ampType: AmpType
  readonly gain: number
  readonly volume: number
  readonly auxVolume: number
  readonly eq: readonly number[]
  readonly effect: GuitarEffect
  readonly masterDelay: number
  readonly masterReverb: number
  readonly reverbType: ReverbType
  readonly reverbLevel: number
  readonly reverbTone: number
  readonly reverbTime: number
  readonly delayLevel: number
  readonly delayFeedback: number
  readonly delayTime: number
}

/** Neutral starting point before the amp's real state is known (EQ 10 = flat per vendor default). */
export const DEFAULT_AMP_STATE: AmpState = {
  ampType: AmpType.CLEAN,
  gain: 15,
  volume: 10,
  auxVolume: 10,
  eq: Array.from({ length: Ranges.EQ_BANDS }, () => 10),
  effect: GuitarEffect.BYPASS,
  masterDelay: 0,
  masterReverb: 0,
  reverbType: ReverbType.PLATE,
  reverbLevel: 0,
  reverbTone: 10,
  reverbTime: 10,
  delayLevel: 0,
  delayFeedback: 0,
  delayTime: 10,
}

export function applyCommand(s: AmpState, c: AmpCommand): AmpState {
  switch (c.kind) {
    case 'Sync': return s
    case 'SetAmpType': return { ...s, ampType: c.type }
    case 'SetGain': return { ...s, gain: clamp(c.value, Ranges.GAIN) }
    case 'SetVolume': return { ...s, volume: clamp(c.value, Ranges.VOLUME) }
    case 'SetAuxVolume': return { ...s, auxVolume: clamp(c.value, Ranges.AUX_VOLUME) }
    case 'SetEqBand': {
      const eq = s.eq.slice()
      eq[c.index] = clamp(c.level, Ranges.EQ_LEVEL)
      return { ...s, eq }
    }
    case 'SetEffect': return { ...s, effect: c.effect }
    case 'SetMasterDelay': return { ...s, masterDelay: clamp(c.value, Ranges.MASTER_FX) }
    case 'SetMasterReverb': return { ...s, masterReverb: clamp(c.value, Ranges.MASTER_FX) }
    case 'SetReverbType': return { ...s, reverbType: c.type }
    case 'SetReverbLevel': return { ...s, reverbLevel: clamp(c.value, Ranges.EFFECT_KNOB) }
    case 'SetReverbTone': return { ...s, reverbTone: clamp(c.value, Ranges.EFFECT_KNOB) }
    case 'SetReverbTime': return { ...s, reverbTime: clamp(c.value, Ranges.EFFECT_KNOB) }
    case 'SetDelayLevel': return { ...s, delayLevel: clamp(c.value, Ranges.EFFECT_KNOB) }
    case 'SetDelayFeedback': return { ...s, delayFeedback: clamp(c.value, Ranges.EFFECT_KNOB) }
    case 'SetDelayTime': return { ...s, delayTime: clamp(c.value, Ranges.EFFECT_KNOB) }
  }
}
