/** Amp types with their wire codes (docs/protocol.md). */
export const AmpType = { CLEAN: 0, ACOUSTIC: 1, DRIVE: 2, DISTORTION: 3, METAL: 4 } as const
export type AmpType = (typeof AmpType)[keyof typeof AmpType]

export const GuitarEffect = { BYPASS: 0, FLANGER: 1, CHORUS: 2, PHASER: 3, WAH: 4, VIBRATO: 5 } as const
export type GuitarEffect = (typeof GuitarEffect)[keyof typeof GuitarEffect]

export const ReverbType = { PLATE: 0, ROOM: 1, SPRING: 2 } as const
export type ReverbType = (typeof ReverbType)[keyof typeof ReverbType]

export interface Range {
  readonly min: number
  readonly max: number
}

/** Value ranges from docs/protocol.md. Out-of-range input is clamped by the codec, never sent raw. */
export const Ranges = {
  GAIN: { min: 0, max: 30 },
  VOLUME: { min: 0, max: 50 },
  AUX_VOLUME: { min: 0, max: 50 },
  EQ_LEVEL: { min: 0, max: 20 },
  EQ_BANDS: 7,
  EFFECT_KNOB: { min: 0, max: 20 },
  MASTER_FX: { min: 0, max: 50 },
} as const

export const clamp = (v: number, r: Range): number => Math.min(r.max, Math.max(r.min, Math.trunc(v)))

export type AmpCommand =
  | { kind: 'Sync' }
  | { kind: 'SetAmpType'; type: AmpType }
  | { kind: 'SetGain'; value: number }
  | { kind: 'SetVolume'; value: number }
  | { kind: 'SetAuxVolume'; value: number }
  | { kind: 'SetEqBand'; index: number; level: number }
  | { kind: 'SetEffect'; effect: GuitarEffect }
  | { kind: 'SetMasterDelay'; value: number }
  | { kind: 'SetMasterReverb'; value: number }
  | { kind: 'SetReverbType'; type: ReverbType }
  | { kind: 'SetReverbLevel'; value: number }
  | { kind: 'SetReverbTone'; value: number }
  | { kind: 'SetReverbTime'; value: number }
  | { kind: 'SetDelayLevel'; value: number }
  | { kind: 'SetDelayFeedback'; value: number }
  | { kind: 'SetDelayTime'; value: number }

/** Command constructors. `setEqBand` index is 0-based (0..6); the wire uses bands 1..7. */
export const Cmd = {
  /** Not a user control: the session handshake the amp expects after every new link. */
  sync: { kind: 'Sync' } as AmpCommand,
  setAmpType: (type: AmpType): AmpCommand => ({ kind: 'SetAmpType', type }),
  setGain: (value: number): AmpCommand => ({ kind: 'SetGain', value }),
  setVolume: (value: number): AmpCommand => ({ kind: 'SetVolume', value }),
  /** Music/Bluetooth (AUX) volume, a separate level from the guitar GUI VOL (docs/protocol.md). */
  setAuxVolume: (value: number): AmpCommand => ({ kind: 'SetAuxVolume', value }),
  setEqBand: (index: number, level: number): AmpCommand => {
    if (!Number.isInteger(index) || index < 0 || index >= Ranges.EQ_BANDS) {
      throw new RangeError(`EQ band index must be 0..6, was ${index}`)
    }
    return { kind: 'SetEqBand', index, level }
  },
  setEffect: (effect: GuitarEffect): AmpCommand => ({ kind: 'SetEffect', effect }),
  setMasterDelay: (value: number): AmpCommand => ({ kind: 'SetMasterDelay', value }),
  setMasterReverb: (value: number): AmpCommand => ({ kind: 'SetMasterReverb', value }),
  setReverbType: (type: ReverbType): AmpCommand => ({ kind: 'SetReverbType', type }),
  setReverbLevel: (value: number): AmpCommand => ({ kind: 'SetReverbLevel', value }),
  setReverbTone: (value: number): AmpCommand => ({ kind: 'SetReverbTone', value }),
  setReverbTime: (value: number): AmpCommand => ({ kind: 'SetReverbTime', value }),
  setDelayLevel: (value: number): AmpCommand => ({ kind: 'SetDelayLevel', value }),
  setDelayFeedback: (value: number): AmpCommand => ({ kind: 'SetDelayFeedback', value }),
  setDelayTime: (value: number): AmpCommand => ({ kind: 'SetDelayTime', value }),
}

const frame = (group: number, item: number, sub: number, value: number): Uint8Array =>
  Uint8Array.from([0xfe, 0x0d, group, item, sub, value, 0, 0, 0, 0xff])

const knob = (item: number, value: number) => frame(0x05, item, 0x00, clamp(value, Ranges.EFFECT_KNOB))

/**
 * Encodes a command into the amp's 10-byte frame `FE 0D G I S V 00 00 00 FF` (docs/protocol.md).
 * The factory-reset frame is intentionally not representable.
 */
export function encode(cmd: AmpCommand): Uint8Array {
  switch (cmd.kind) {
    case 'Sync': return Uint8Array.from([0xfe, 0x0d, 0xab, 0x41, 0x34, 0x42, 0, 0, 0, 0xff])
    case 'SetAmpType': return frame(0x02, 0x03, 0x00, cmd.type)
    case 'SetGain': return frame(0x02, 0x03, 0x02, clamp(cmd.value, Ranges.GAIN))
    case 'SetVolume': return frame(0x02, 0x01, 0x00, clamp(cmd.value, Ranges.VOLUME))
    case 'SetAuxVolume': return frame(0x04, 0x01, 0x00, clamp(cmd.value, Ranges.AUX_VOLUME))
    case 'SetEqBand': return frame(0x02, 0x04, cmd.index + 1, clamp(cmd.level, Ranges.EQ_LEVEL))
    case 'SetEffect': return frame(0x02, 0x05, 0x00, cmd.effect)
    case 'SetMasterDelay': return frame(0x02, 0x02, 0x00, clamp(cmd.value, Ranges.MASTER_FX))
    case 'SetMasterReverb': return frame(0x02, 0x02, 0x01, clamp(cmd.value, Ranges.MASTER_FX))
    case 'SetReverbType': return frame(0x05, 0x01, 0x00, cmd.type)
    case 'SetReverbLevel': return knob(0x02, cmd.value)
    case 'SetReverbTone': return knob(0x03, cmd.value)
    case 'SetReverbTime': return knob(0x04, cmd.value)
    case 'SetDelayLevel': return knob(0x05, cmd.value)
    case 'SetDelayFeedback': return knob(0x06, cmd.value)
    case 'SetDelayTime': return knob(0x07, cmd.value)
  }
}

const FRAME_LEN = 16
const pick = <T extends number>(table: Record<string, T>, code: number): T | undefined =>
  Object.values(table).find((v) => v === code)

/**
 * Turns one 16-byte notification (`FE 0F ... FF`) into the commands that set the app's AmpState to what the amp
 * reports. Never throws; anything not understood (heartbeat, unused dumps and aux items, malformed frames) yields [].
 */
export function decode(frame: Uint8Array): AmpCommand[] {
  if (frame.length !== FRAME_LEN) return []
  const b = Array.from(frame)
  if (b[0] !== 0xfe || b[1] !== 0x0f || b[15] !== 0xff) return []
  const out: AmpCommand[] = []
  switch (b[2]) {
    case 0xbb: {
      out.push(Cmd.setVolume(b[3]))
      const t = pick(AmpType, b[5])
      if (t !== undefined) out.push(Cmd.setAmpType(t))
      out.push(Cmd.setGain(b[7]))
      for (let band = 0; band < Ranges.EQ_BANDS; band++) out.push(Cmd.setEqBand(band, b[8 + band]))
      return out
    }
    case 0xbc: {
      const e = pick(GuitarEffect, b[3])
      if (e !== undefined) out.push(Cmd.setEffect(e))
      out.push(Cmd.setMasterDelay(b[13]), Cmd.setMasterReverb(b[14]))
      return out
    }
    case 0xc0: {
      const r = pick(ReverbType, b[3])
      if (r !== undefined) out.push(Cmd.setReverbType(r))
      out.push(
        Cmd.setReverbLevel(b[4]), Cmd.setReverbTone(b[5]), Cmd.setReverbTime(b[6]),
        Cmd.setDelayLevel(b[7]), Cmd.setDelayFeedback(b[8]), Cmd.setDelayTime(b[9]),
      )
      return out
    }
    // BF dump: byte 3 is the aux (music) volume (verified on the amp: it followed the app's aux writes, GUI VOL did not)
    case 0xbf: return [Cmd.setAuxVolume(b[3])]
    case 0x02: return decodeLive(b[3], b[4], b[5])
    // live aux (music) volume: FE 0F 04 01 00 VV ... (the master pot sends it together with GUI VOL)
    case 0x04: return b[3] === 0x01 && b[4] === 0x00 ? [Cmd.setAuxVolume(b[5])] : []
    default: return []
  }
}

function decodeLive(item: number, sub: number, value: number): AmpCommand[] {
  if (item === 0x01 && sub === 0x00) return [Cmd.setVolume(value)]
  if (item === 0x02 && sub === 0x00) return [Cmd.setMasterDelay(value)]
  if (item === 0x02 && sub === 0x01) return [Cmd.setMasterReverb(value)]
  if (item === 0x03 && sub === 0x00) {
    const t = pick(AmpType, value)
    return t === undefined ? [] : [Cmd.setAmpType(t)]
  }
  if (item === 0x03 && sub === 0x02) return [Cmd.setGain(value)]
  if (item === 0x04 && sub >= 1 && sub <= Ranges.EQ_BANDS) return [Cmd.setEqBand(sub - 1, value)]
  if (item === 0x05 && sub === 0x00) {
    const e = pick(GuitarEffect, value)
    return e === undefined ? [] : [Cmd.setEffect(e)]
  }
  return []
}
