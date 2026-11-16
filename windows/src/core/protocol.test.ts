import { describe, expect, test } from 'vitest'
import { AmpType, GuitarEffect, ReverbType, Cmd, decode, encode, type AmpCommand } from './protocol'

const hex = (c: AmpCommand) => [...encode(c)].map((b) => b.toString(16).padStart(2, '0').toUpperCase()).join('')
const frame = (h: string) => Uint8Array.from(h.match(/../g)!.map((x) => parseInt(x, 16)))
const live = (group: number, item: number, sub: number, value: number) => {
  const f = new Uint8Array(16)
  f[0] = 0xfe; f[1] = 0x0f; f[2] = group; f[3] = item; f[4] = sub; f[5] = value; f[15] = 0xff
  return f
}
const bb = 'FE0FBB0C0004190D0A0A0A0A0A0A0AFF'
const bc = 'FE0FBC00141C1412180B1123140000FF'
const c0 = 'FE0FC0000A080A0B0E100C640D000DFF'

describe('encode', () => {
  test('amp type frames match the verified protocol', () => {
    expect(hex(Cmd.setAmpType(AmpType.CLEAN))).toBe('FE0D02030000000000FF')
    expect(hex(Cmd.setAmpType(AmpType.ACOUSTIC))).toBe('FE0D02030001000000FF')
    expect(hex(Cmd.setAmpType(AmpType.DRIVE))).toBe('FE0D02030002000000FF')
    expect(hex(Cmd.setAmpType(AmpType.DISTORTION))).toBe('FE0D02030003000000FF')
    expect(hex(Cmd.setAmpType(AmpType.METAL))).toBe('FE0D02030004000000FF')
  })
  test('sync frame matches the vendor handshake', () => {
    expect(hex(Cmd.sync)).toBe('FE0DAB413442000000FF')
  })
  test('gain and volume frames', () => {
    expect(hex(Cmd.setGain(15))).toBe('FE0D0203020F000000FF')
    expect(hex(Cmd.setVolume(10))).toBe('FE0D0201000A000000FF')
  })
  test('out of range values are clamped, not sent raw', () => {
    expect(hex(Cmd.setGain(999))).toBe('FE0D0203021E000000FF')
    expect(hex(Cmd.setGain(-5))).toBe('FE0D02030200000000FF')
    expect(hex(Cmd.setVolume(999))).toBe('FE0D02010032000000FF')
    expect(hex(Cmd.setEqBand(0, 99))).toBe('FE0D02040114000000FF')
  })
  test('eq band index is zero based and maps to wire band 1..7', () => {
    expect(hex(Cmd.setEqBand(0, 10))).toBe('FE0D0204010A000000FF')
    expect(hex(Cmd.setEqBand(6, 10))).toBe('FE0D0204070A000000FF')
  })
  test('eq band index outside 0..6 is rejected', () => {
    expect(() => Cmd.setEqBand(7, 0)).toThrow(RangeError)
    expect(() => Cmd.setEqBand(-1, 0)).toThrow(RangeError)
  })
  test('effect frames', () => {
    expect(hex(Cmd.setEffect(GuitarEffect.BYPASS))).toBe('FE0D02050000000000FF')
    expect(hex(Cmd.setEffect(GuitarEffect.CHORUS))).toBe('FE0D02050002000000FF')
    expect(hex(Cmd.setEffect(GuitarEffect.VIBRATO))).toBe('FE0D02050005000000FF')
  })
  test('reverb and delay frames', () => {
    expect(hex(Cmd.setReverbType(ReverbType.SPRING))).toBe('FE0D05010002000000FF')
    expect(hex(Cmd.setReverbLevel(10))).toBe('FE0D0502000A000000FF')
    expect(hex(Cmd.setReverbTone(10))).toBe('FE0D0503000A000000FF')
    expect(hex(Cmd.setReverbTime(10))).toBe('FE0D0504000A000000FF')
    expect(hex(Cmd.setDelayLevel(10))).toBe('FE0D0505000A000000FF')
    expect(hex(Cmd.setDelayFeedback(10))).toBe('FE0D0506000A000000FF')
    expect(hex(Cmd.setDelayTime(10))).toBe('FE0D0507000A000000FF')
  })
  test('master delay and reverb frames are the guitar tab echo and reverb', () => {
    expect(hex(Cmd.setMasterDelay(10))).toBe('FE0D0202000A000000FF')
    expect(hex(Cmd.setMasterReverb(10))).toBe('FE0D0202010A000000FF')
    expect(hex(Cmd.setMasterDelay(999))).toBe('FE0D02020032000000FF')
    expect(hex(Cmd.setMasterReverb(-3))).toBe('FE0D02020100000000FF')
  })
  test('every frame is ten bytes with header and trailer', () => {
    const all = [
      Cmd.setAmpType(AmpType.METAL), Cmd.setGain(3), Cmd.setVolume(3), Cmd.setEqBand(3, 3),
      Cmd.setEffect(GuitarEffect.WAH), Cmd.setReverbType(ReverbType.ROOM), Cmd.setReverbLevel(3),
      Cmd.setReverbTone(3), Cmd.setReverbTime(3), Cmd.setDelayLevel(3), Cmd.setDelayFeedback(3), Cmd.setDelayTime(3),
    ]
    for (const c of all) {
      const f = encode(c)
      expect(f.length).toBe(10)
      expect(f[0]).toBe(0xfe)
      expect(f[1]).toBe(0x0d)
      expect(f[9]).toBe(0xff)
    }
  })
  test('aux (music) volume frames are clamped and never the factory reset', () => {
    expect(hex(Cmd.setAuxVolume(10))).toBe('FE0D0401000A000000FF')
    expect(hex(Cmd.setAuxVolume(999))).toBe('FE0D04010032000000FF')
    expect(hex(Cmd.setAuxVolume(-3))).toBe('FE0D04010000000000FF')
    for (let v = -5; v <= 60; v++) expect(hex(Cmd.setAuxVolume(v))).not.toBe('FE0D01010000000000FF')
  })
  test('no encodable command is the factory reset, over the full value range', () => {
    const factoryReset = 'FE0D01010000000000FF'
    for (let v = -5; v <= 60; v++) {
      const cmds = [
        Cmd.setVolume(v), Cmd.setGain(v), Cmd.setEqBand(0, v), Cmd.setMasterDelay(v), Cmd.setMasterReverb(v),
        Cmd.setReverbLevel(v), Cmd.setReverbTone(v), Cmd.setReverbTime(v),
        Cmd.setDelayLevel(v), Cmd.setDelayFeedback(v), Cmd.setDelayTime(v),
      ]
      for (const c of cmds) expect(hex(c)).not.toBe(factoryReset)
    }
    for (const t of Object.values(AmpType)) expect(hex(Cmd.setAmpType(t))).not.toBe(factoryReset)
    for (const e of Object.values(GuitarEffect)) expect(hex(Cmd.setEffect(e))).not.toBe(factoryReset)
    expect(hex(Cmd.sync)).not.toBe(factoryReset)
  })
})

describe('decode (frames captured from the real amp)', () => {
  const same = (got: AmpCommand[], expected: AmpCommand[]) => {
    expect(got.length).toBe(expected.length)
    for (const e of expected) expect(got).toContainEqual(e)
  }
  test('BB dump', () => {
    same(decode(frame(bb)), [Cmd.setVolume(12), Cmd.setAmpType(AmpType.METAL), Cmd.setGain(13), ...Array.from({ length: 7 }, (_, i) => Cmd.setEqBand(i, 10))])
  })
  test('BC dump', () => {
    same(decode(frame(bc)), [Cmd.setEffect(GuitarEffect.BYPASS), Cmd.setMasterDelay(0), Cmd.setMasterReverb(0)])
  })
  test('C0 dump', () => {
    same(decode(frame(c0)), [
      Cmd.setReverbType(ReverbType.PLATE), Cmd.setReverbLevel(10), Cmd.setReverbTone(8), Cmd.setReverbTime(10),
      Cmd.setDelayLevel(11), Cmd.setDelayFeedback(14), Cmd.setDelayTime(16),
    ])
  })
  test('live knob frames', () => {
    expect(decode(live(0x02, 0x01, 0x00, 12))).toEqual([Cmd.setVolume(12)])
    expect(decode(live(0x02, 0x03, 0x02, 14))).toEqual([Cmd.setGain(14)])
    expect(decode(live(0x02, 0x03, 0x00, 4))).toEqual([Cmd.setAmpType(AmpType.METAL)])
    expect(decode(live(0x02, 0x02, 0x00, 50))).toEqual([Cmd.setMasterDelay(50)])
    expect(decode(live(0x02, 0x02, 0x01, 3))).toEqual([Cmd.setMasterReverb(3)])
    expect(decode(live(0x02, 0x05, 0x00, 2))).toEqual([Cmd.setEffect(GuitarEffect.CHORUS)])
    expect(decode(live(0x02, 0x04, 0x03, 15))).toEqual([Cmd.setEqBand(2, 15)])
  })
  test('the aux volume comes from the BF dump (captured on the real amp)', () => {
    expect(decode(frame('FE0FBF100001000A0A0A0A0A0A0A00FF'))).toEqual([Cmd.setAuxVolume(16)])
    expect(decode(frame('FE0FBF0C0001000A0A0A0A0A0A0A19FF'))).toEqual([Cmd.setAuxVolume(12)])
  })
  test('live aux (music) volume is decoded', () => {
    expect(decode(live(0x04, 0x01, 0x00, 25))).toEqual([Cmd.setAuxVolume(25)])
    expect(decode(live(0x04, 0x01, 0x00, 0))).toEqual([Cmd.setAuxVolume(0)])
  })
  test('ignores heartbeat, other aux items and the other dumps', () => {
    const ignored = [
      frame('FE0F41000002000000000000000000FF'),
      live(0x04, 0x01, 0x01, 0x05),
      live(0x04, 0x02, 0x00, 0x05),
      frame('FE0FBD1A143206140C0C0C0C0D0D0DFF'),
      frame('FE0FBE00000A0A0A0A0A0A0A000D0DFF'),
    ]
    for (const f of ignored) expect(decode(f)).toEqual([])
  })
  test('malformed frames yield nothing and never throw', () => {
    const good = frame(bb)
    const withByte = (i: number, v: number) => { const c = good.slice(); c[i] = v; return c }
    const bad = [good.slice(0, 15), new Uint8Array([...good, 0]), new Uint8Array(0), withByte(1, 0x0d), withByte(15, 0x00)]
    for (const f of bad) expect(decode(f)).toEqual([])
  })
  test('unknown enum code is skipped but the rest applies', () => {
    const cmds = decode(frame('FE0FBB0C0009190D0A0A0A0A0A0A0AFF'))
    expect(cmds.some((c) => c.kind === 'SetAmpType')).toBe(false)
    expect(cmds).toContainEqual(Cmd.setVolume(12))
    expect(cmds).toContainEqual(Cmd.setGain(13))
  })
  test('decode never produces Sync', () => {
    const all = [bb, bc, c0].map(frame).concat([live(0x02, 0x01, 0x00, 1), live(0x02, 0x03, 0x00, 1), live(0x02, 0x04, 0x01, 1)])
    for (const f of all) expect(decode(f).some((c) => c.kind === 'Sync')).toBe(false)
  })
})
