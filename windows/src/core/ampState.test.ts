import { expect, test } from 'vitest'
import { AmpType, Cmd, GuitarEffect, ReverbType } from './protocol'
import { DEFAULT_AMP_STATE as D, applyCommand } from './ampState'

test('setAmpType updates only the amp type', () => {
  const s = applyCommand(D, Cmd.setAmpType(AmpType.METAL))
  expect(s.ampType).toBe(AmpType.METAL)
  expect(s).toEqual({ ...D, ampType: AmpType.METAL })
})

test('setEqBand updates that band only and does not mutate the old state', () => {
  const before = D.eq.slice()
  const s = applyCommand(D, Cmd.setEqBand(3, 5))
  expect(s.eq[3]).toBe(5)
  expect(s.eq.filter((_, i) => i !== 3)).toEqual(D.eq.filter((_, i) => i !== 3))
  expect(s.eq.length).toBe(7)
  expect(D.eq).toEqual(before)
})

test('a sequence of commands yields the last value', () => {
  const s = [
    Cmd.setAmpType(AmpType.METAL), Cmd.setAmpType(AmpType.CLEAN), Cmd.setAmpType(AmpType.DRIVE),
    Cmd.setEffect(GuitarEffect.WAH), Cmd.setEffect(GuitarEffect.BYPASS), Cmd.setReverbType(ReverbType.SPRING),
  ].reduce(applyCommand, D)
  expect(s.ampType).toBe(AmpType.DRIVE)
  expect(s.effect).toBe(GuitarEffect.BYPASS)
  expect(s.reverbType).toBe(ReverbType.SPRING)
})

test('state mirrors what the codec will send, so values are clamped', () => {
  expect(applyCommand(D, Cmd.setGain(999)).gain).toBe(30)
  expect(applyCommand(D, Cmd.setVolume(-1)).volume).toBe(0)
  expect(applyCommand(D, Cmd.setEqBand(0, 99)).eq[0]).toBe(20)
  expect(applyCommand(D, Cmd.setDelayTime(99)).delayTime).toBe(20)
})

test('every command kind updates its field', () => {
  const s = [
    Cmd.setGain(7), Cmd.setVolume(8), Cmd.setReverbLevel(1), Cmd.setReverbTone(2), Cmd.setReverbTime(3),
    Cmd.setDelayLevel(4), Cmd.setDelayFeedback(5), Cmd.setDelayTime(6),
  ].reduce(applyCommand, D)
  expect([s.gain, s.volume, s.reverbLevel, s.reverbTone, s.reverbTime, s.delayLevel, s.delayFeedback, s.delayTime])
    .toEqual([7, 8, 1, 2, 3, 4, 5, 6])
})

test('master delay and reverb are tracked separately from the effect page controls', () => {
  const s = [Cmd.setMasterDelay(12), Cmd.setMasterReverb(34), Cmd.setDelayLevel(5)].reduce(applyCommand, D)
  expect([s.masterDelay, s.masterReverb, s.delayLevel]).toEqual([12, 34, 5])
  expect(applyCommand(D, Cmd.setMasterDelay(999)).masterDelay).toBe(50)
})

test('sync leaves the state unchanged', () => {
  expect(applyCommand(D, Cmd.sync)).toBe(D)
})

test('aux volume is tracked separately from GUI volume and clamped', () => {
  const s = [Cmd.setVolume(12), Cmd.setAuxVolume(30)].reduce(applyCommand, D)
  expect([s.volume, s.auxVolume]).toEqual([12, 30])
  expect(D.auxVolume).toBe(10)
  expect(applyCommand(D, Cmd.setAuxVolume(999)).auxVolume).toBe(50)
  expect(applyCommand(D, Cmd.setAuxVolume(-1)).auxVolume).toBe(0)
})
