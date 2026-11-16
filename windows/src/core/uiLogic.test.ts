import { describe, expect, test } from 'vitest'
import { SliderThrottle } from './sliderThrottle'
import { SliderModel } from './sliderModel'
import { PowerMemory } from './powerMemory'
import { KnobMath } from './knobMath'

describe('SliderThrottle', () => {
  let clock = 0
  const throttle = () => new SliderThrottle(60, () => clock)
  test('first change is sent, then a burst is suppressed until the interval passes', () => {
    clock = 0
    const t = throttle()
    expect(t.onChange(1)).toBe(true)
    clock = 20; expect(t.onChange(2)).toBe(false)
    clock = 59; expect(t.onChange(3)).toBe(false)
    clock = 60; expect(t.onChange(4)).toBe(true)
  })
  test('the same value is never re-sent', () => {
    clock = 0
    const t = throttle()
    expect(t.onChange(5)).toBe(true)
    clock = 500; expect(t.onChange(5)).toBe(false)
  })
  test('the final value is always sent when the finger lifts, even inside the interval', () => {
    clock = 0
    const t = throttle()
    expect(t.onChange(1)).toBe(true)
    clock = 10; expect(t.onChange(9)).toBe(false)
    expect(t.onFinished(9)).toBe(true)
    expect(t.onFinished(9)).toBe(false)
  })
})

describe('SliderModel', () => {
  let clock = 0
  const model = (initial: number) => new SliderModel(initial, new SliderThrottle(60, () => clock))
  test('the final value survives the amp echoing an earlier value mid drag', () => {
    clock = 0
    const m = model(10)
    expect(m.onDrag(11)).toBe(11)
    m.onExternal(11)
    clock = 20
    expect(m.onDrag(15)).toBeNull()
    m.onExternal(11)
    expect(m.local).toBe(15)
    expect(m.onFinish()).toBe(15)
  })
  test('follows the external value when not dragging', () => {
    const m = model(10)
    m.onExternal(30)
    expect(m.local).toBe(30)
  })
  test('after the finger lifts external values are followed again', () => {
    clock = 0
    const m = model(10)
    m.onDrag(12); m.onFinish()
    m.onExternal(7)
    expect(m.local).toBe(7)
  })
  test('tapping back to the last sent value after the amp changed it still sends', () => {
    clock = 0
    const m = model(10)
    expect(m.onDrag(20)).toBe(20); m.onFinish()
    m.onExternal(5)
    clock = 1000
    expect(m.onDrag(20)).toBe(20)
    expect(m.onFinish()).toBeNull()
  })
  test('fractional values are rounded, not truncated', () => {
    clock = 0
    const m = model(0)
    expect(m.onDrag(14.7)).toBe(15)
    const n = model(0)
    expect(n.onDrag(1)).toBe(1)
    clock = 10
    expect(n.onDrag(14.7)).toBeNull()
    expect(n.onFinish()).toBe(15)
  })
})

describe('PowerMemory', () => {
  let clock = 0
  const memory = () => new PowerMemory({ defaultLevel: 20, pendingMs: 1000, now: () => clock })
  test('never non-zero toggles to the default', () => {
    expect(memory().levelAfterToggle(0)).toBe(20)
  })
  test('remembers the last non-zero level and restores it', () => {
    clock = 0
    const m = memory()
    m.observe(30)
    expect(m.levelAfterToggle(30)).toBe(0)
    m.observe(0)
    clock += 5000
    expect(m.levelAfterToggle(0)).toBe(30)
  })
  test('a level set on the amp is remembered too', () => {
    const m = memory()
    m.observe(12); m.observe(0)
    expect(m.levelAfterToggle(0)).toBe(12)
  })
  test('observing zero never forgets the last non-zero', () => {
    const m = memory()
    m.observe(25); m.observe(0); m.observe(0)
    expect(m.levelAfterToggle(0)).toBe(25)
  })
  test('a rapid second toggle undoes the first even before the amp state updates', () => {
    clock = 0
    const m = memory()
    m.observe(30)
    expect(m.levelAfterToggle(30)).toBe(0)
    expect(m.levelAfterToggle(30)).toBe(30)
  })
  test('an unconfirmed request expires so a failed write does not leave the button out of step', () => {
    clock = 0
    const m = memory()
    m.observe(30)
    expect(m.levelAfterToggle(30)).toBe(0)
    clock += 2000
    expect(m.levelAfterToggle(30)).toBe(0)
  })
})

describe('KnobMath', () => {
  const r50 = { min: 0, max: 50 }, r30 = { min: 0, max: 30 }, r20 = { min: 0, max: 20 }
  test('sweep is linear over 270 degrees', () => {
    expect(KnobMath.sweepDegrees(0, r50)).toBeCloseTo(0)
    expect(KnobMath.sweepDegrees(50, r50)).toBeCloseTo(270)
    expect(KnobMath.sweepDegrees(25, r50)).toBeCloseTo(135)
    expect(KnobMath.sweepDegrees(15, r30)).toBeCloseTo(135)
    expect(KnobMath.sweepDegrees(-3, r50)).toBeCloseTo(0)
    expect(KnobMath.sweepDegrees(99, r50)).toBeCloseTo(270)
  })
  test('a drag of one full travel sweeps the whole range', () => {
    expect(KnobMath.valueAfterDrag(10, 0, r50)).toBe(10)
    expect(KnobMath.valueAfterDrag(0, 220, r50)).toBe(50)
    expect(KnobMath.valueAfterDrag(50, -220, r50)).toBe(0)
    expect(KnobMath.valueAfterDrag(20, 110, r50)).toBe(45)
  })
  test('drag is clamped to the range', () => {
    expect(KnobMath.valueAfterDrag(40, 500, r50)).toBe(50)
    expect(KnobMath.valueAfterDrag(5, -500, r50)).toBe(0)
  })
  test('small drags round to the nearest step', () => {
    expect(KnobMath.valueAfterDrag(10, 2, r20)).toBe(10)
    expect(KnobMath.valueAfterDrag(10, 6, r20)).toBe(11)
    expect(KnobMath.valueAfterDrag(10, -6, r20)).toBe(9)
  })
  test('step moves by delta and stays in the range', () => {
    expect(KnobMath.step(50, 1, r50)).toBe(50)
    expect(KnobMath.step(0, -1, r50)).toBe(0)
    expect(KnobMath.step(10, -1, r50)).toBe(9)
    expect(KnobMath.step(10, 1, r50)).toBe(11)
  })
})
