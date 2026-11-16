import { KnobMath } from '../core/knobMath'
import type { Range } from '../core/protocol'
import { SliderModel } from '../core/sliderModel'
import { SliderThrottle } from '../core/sliderThrottle'
import { POWER_ICON, h, svg } from './dom'

const nowMs = () => performance.now()
const newModel = (initial: number) => new SliderModel(initial, new SliderThrottle(60, nowMs))

// ---------------------------------------------------------------- value popup (double tap / double click)

export interface PopupSpec {
  title: string
  range: Range
  value: number
  onChange: (v: number) => void
}

interface OpenPopup {
  setExternal(v: number): void
  close(): void
}

/** One popup at a time. Changes apply live (throttled); TAMAM, Esc and a click outside only close it. */
export class PopupHost {
  private current: (OpenPopup & { owner: unknown }) | null = null

  open(owner: unknown, spec: PopupSpec): void {
    this.close()
    const model = newModel(spec.value)
    const value = h('div', { class: 'pn', testid: 'popup-value' }, String(spec.value))
    const slider = h('input', {
      type: 'range', min: spec.range.min, max: spec.range.max, step: 1, value: spec.value, class: 'psl', 'aria-label': spec.title,
    })
    const show = () => { value.textContent = String(Math.round(model.local)); slider.value = String(Math.round(model.local)) }
    const send = (v: number | null) => { if (v !== null) spec.onChange(v) }
    slider.addEventListener('input', () => { send(model.onDrag(Number(slider.value))); show() })
    slider.addEventListener('change', () => { send(model.onFinish()); show() })
    /** One step from the value on screen (not the amp's, which may not have echoed the previous tap yet); always sent. */
    const step = (delta: number) => {
      const next = KnobMath.step(Math.round(model.local), delta, spec.range)
      send(model.onDrag(next)); send(model.onFinish()); show()
    }
    const close = () => { root.remove(); document.removeEventListener('keydown', onKey, true); if (this.current?.owner === owner) this.current = null }
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') { e.stopPropagation(); close() } }
    const root = h('div', { class: 'popwrap', testid: 'popup' },
      h('div', { class: 'scrim', onclick: close }),
      h('div', { class: 'pop', role: 'dialog', 'aria-label': spec.title },
        h('div', { class: 'pt', testid: 'popup-title' }, spec.title),
        value,
        slider,
        h('div', { class: 'pm' },
          h('button', { class: 'pmb', testid: 'popup-minus', 'aria-label': 'Azalt', onclick: () => step(-1) }, '−'),
          h('button', { class: 'pmb', testid: 'popup-plus', 'aria-label': 'Arttır', onclick: () => step(+1) }, '+')),
        h('button', { class: 'okb', testid: 'popup-ok', onclick: close }, 'TAMAM')))
    document.addEventListener('keydown', onKey, true)
    document.body.append(root)
    this.current = { owner, setExternal: (v) => { model.onExternal(v); show() }, close }
  }

  /** The amp changed the value behind an open popup. */
  external(owner: unknown, v: number): void {
    const c = this.current
    if (c && c.owner === owner) c.setExternal(v)
  }

  close(): void {
    this.current?.close()
    this.current = null
  }
}

// ---------------------------------------------------------------- knob

export type KnobSize = 'sm' | 'mid' | 'big'

export interface Handle {
  el: HTMLElement
  set(value: number, enabled: boolean): void
}

export interface KnobOptions {
  label: string
  range: Range
  size: KnobSize
  onChange: (v: number) => void
  popups: PopupHost
  /** Accessible name, test id (`knob-<description>`) and popup title. Defaults to the label. */
  description?: string
}

const DOUBLE_TAP_MS = 350
const TAP_SLOP_PX = 6

export function createKnob(o: KnobOptions): Handle {
  const name = o.description ?? o.label
  const model = newModel(o.range.min)
  let enabled = true
  let lastTap = Number.NEGATIVE_INFINITY
  const k = h('div', {
    class: `k ${o.size}`, role: 'slider', tabindex: 0, testid: `knob-${name}`, 'aria-label': name,
    'aria-valuemin': o.range.min, 'aria-valuemax': o.range.max,
  }, h('div', { class: 'glow' }), h('div', { class: 'arc' }), h('div', { class: 'body' }), h('div', { class: 'tick' }))
  const wrap = h('div', { class: 'kw' }, k, h('div', { class: 'kl' }, o.label))

  const paint = () => {
    const v = Math.round(model.local)
    k.style.setProperty('--v', String(KnobMath.sweepDegrees(model.local, o.range)))
    k.setAttribute('aria-valuenow', String(v))
    k.setAttribute('aria-valuetext', String(v))
  }
  const send = (v: number | null) => { if (v !== null) o.onChange(v) }
  const apply = (v: number) => { send(model.onDrag(v)); send(model.onFinish()); paint(); o.popups.external(wrap, Math.round(model.local)) }

  let startY = 0
  let startValue = 0
  let pressed = false
  let moved = false
  k.addEventListener('pointerdown', (e) => {
    if (!enabled) return
    pressed = true; moved = false
    startY = e.clientY; startValue = Math.round(model.local)
    k.setPointerCapture(e.pointerId)
    e.preventDefault()
  })
  k.addEventListener('pointermove', (e) => {
    if (!pressed) return
    const up = startY - e.clientY
    if (!moved && Math.abs(up) > TAP_SLOP_PX) moved = true
    if (!moved) return
    const v = KnobMath.valueAfterDrag(startValue, up, o.range)
    send(model.onDrag(v)); paint()
  })
  const release = (cancel: boolean) => {
    if (!pressed) return
    pressed = false
    if (moved) { send(model.onFinish()); paint(); lastTap = Number.NEGATIVE_INFINITY; return }
    if (cancel) return
    const t = nowMs()
    if (t - lastTap < DOUBLE_TAP_MS) {
      lastTap = Number.NEGATIVE_INFINITY
      o.popups.open(wrap, { title: name, range: o.range, value: Math.round(model.local), onChange: (v) => { o.onChange(v); model.onExternal(v); paint() } })
    } else lastTap = t
  }
  k.addEventListener('pointerup', () => release(false))
  k.addEventListener('pointercancel', () => release(true))
  k.addEventListener('wheel', (e) => {
    if (!enabled) return
    e.preventDefault()
    apply(KnobMath.step(Math.round(model.local), e.deltaY < 0 ? 1 : -1, o.range))
  }, { passive: false })
  k.addEventListener('keydown', (e) => {
    if (!enabled) return
    const d = e.key === 'ArrowUp' || e.key === 'ArrowRight' ? 1 : e.key === 'ArrowDown' || e.key === 'ArrowLeft' ? -1 : e.key === 'PageUp' ? 5 : e.key === 'PageDown' ? -5 : 0
    if (d === 0) return
    e.preventDefault()
    apply(KnobMath.step(Math.round(model.local), d, o.range))
  })

  paint()
  return {
    el: wrap,
    set(value, en) {
      enabled = en
      wrap.classList.toggle('dis', !en)
      k.setAttribute('aria-disabled', String(!en))
      model.onExternal(value)
      paint()
      o.popups.external(wrap, value)
    },
  }
}

// ---------------------------------------------------------------- power button (delay / reverb)

export function createPowerButton(name: string, onToggle: () => void): { el: HTMLElement; set(on: boolean, enabled: boolean): void } {
  const in_ = h('div', { class: 'in' })
  in_.append(svg(POWER_ICON))
  const el = h('button', { class: 'pb', role: 'switch', testid: `power-${name}`, 'aria-label': name, onclick: () => onToggle() }, in_)
  return {
    el,
    set(on, enabled) {
      el.classList.toggle('on', on)
      el.setAttribute('aria-checked', String(on))
      el.disabled = !enabled
    },
  }
}

// ---------------------------------------------------------------- chips (effect / reverb type)

export function createChips<T extends number>(
  items: ReadonlyArray<readonly [T, string]>, onPick: (v: T) => void,
): { el: HTMLElement; set(selected: T, enabled: boolean): void } {
  const buttons = items.map(([v, text]) =>
    h('button', { class: 'chip', role: 'radio', testid: `chip-${text}`, onclick: () => onPick(v) }, text))
  const el = h('div', { class: 'chips', role: 'radiogroup' }, ...buttons)
  return {
    el,
    set(selected, enabled) {
      items.forEach(([v], i) => {
        const b = buttons[i]
        b.classList.toggle('on', v === selected)
        b.setAttribute('aria-checked', String(v === selected))
        b.disabled = !enabled
      })
    },
  }
}

// ---------------------------------------------------------------- panel

export function createPanel(title: string, trailing: HTMLElement | null, ...body: Array<HTMLElement | null>): HTMLElement {
  return h('section', { class: 'panel' },
    title || trailing ? h('div', { class: 'ph' }, h('b', {}, title), trailing) : null,
    ...body)
}

export function primaryButton(text: string, onClick: () => void): HTMLButtonElement {
  return h('button', { class: 'btnr', testid: `btn-${text}`, onclick: onClick }, text)
}

// ---------------------------------------------------------------- pedal (footswitch)

export function createPedal(name: string, onPress: () => void): { el: HTMLElement; set(on: boolean, enabled: boolean): void } {
  const fs = h('div', { class: 'fs' }, h('div', { class: 'nut' }), h('div', { class: 'well' }), h('div', { class: 'chrome' }), h('div', { class: 'ring' }))
  const el = h('button', { class: 'ps', role: 'radio', testid: `pedal-${name}`, 'aria-label': name, onclick: () => onPress() },
    h('div', { class: 'lb' }, name), fs)
  return {
    el,
    set(on, enabled) {
      el.classList.toggle('on', on)
      el.setAttribute('aria-checked', String(on))
      el.disabled = !enabled
    },
  }
}

// ---------------------------------------------------------------- EQ fader

export function createFader(label: string, range: Range, onChange: (v: number) => void): Handle {
  const model = newModel(range.min)
  const span = Math.max(range.max - range.min, 1)
  let enabled = true
  const valueEl = h('div', { class: 'kv', testid: `fader-value-${label}` }, String(range.min))
  const fill = h('div', { class: 'fill' })
  const thumb = h('div', { class: 'thumb' })
  const box = h('div', {
    class: 'fdbox', role: 'slider', tabindex: 0, testid: `fader-${label}`, 'aria-label': label,
    'aria-valuemin': range.min, 'aria-valuemax': range.max, 'aria-orientation': 'vertical',
  }, h('div', { class: 'fdtrack' }, fill), thumb)
  const el = h('div', { class: 'fd' }, valueEl, box, h('div', { class: 'kl' }, label))

  const THUMB_HALF = 7.5
  const valueAt = (clientY: number) => {
    const r = box.getBoundingClientRect()
    const usable = Math.max(r.height - 2 * THUMB_HALF, 1)
    const fraction = 1 - Math.min(1, Math.max(0, (clientY - r.top - THUMB_HALF) / usable))
    return range.min + fraction * span
  }
  const paint = () => {
    const shown = Math.round(Math.min(range.max, Math.max(range.min, model.local)))
    valueEl.textContent = String(shown)
    box.setAttribute('aria-valuenow', String(shown))
    const frac = Math.min(1, Math.max(0, (model.local - range.min) / span))
    el.style.setProperty('--f', String(frac))
  }
  const send = (v: number | null) => { if (v !== null) onChange(v) }

  let down = false
  box.addEventListener('pointerdown', (e) => {
    if (!enabled) return
    down = true
    box.setPointerCapture(e.pointerId)
    e.preventDefault()
    send(model.onDrag(valueAt(e.clientY))); paint()
  })
  box.addEventListener('pointermove', (e) => {
    if (!down) return
    send(model.onDrag(valueAt(e.clientY))); paint()
  })
  const up = () => { if (!down) return; down = false; send(model.onFinish()); paint() }
  box.addEventListener('pointerup', up)
  box.addEventListener('pointercancel', up)
  box.addEventListener('keydown', (e) => {
    if (!enabled) return
    const d = e.key === 'ArrowUp' || e.key === 'ArrowRight' ? 1 : e.key === 'ArrowDown' || e.key === 'ArrowLeft' ? -1 : 0
    if (d === 0) return
    e.preventDefault()
    const next = KnobMath.step(Math.round(model.local), d, range)
    send(model.onDrag(next)); send(model.onFinish()); paint()
  })

  paint()
  return {
    el,
    set(value, en) {
      enabled = en
      el.classList.toggle('dis', !en)
      box.setAttribute('aria-disabled', String(!en))
      model.onExternal(value)
      paint()
    },
  }
}
