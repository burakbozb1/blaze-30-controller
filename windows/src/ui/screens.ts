import type { AppController, UiState } from '../core/appController'
import { AmpType, GuitarEffect, Ranges, ReverbType } from '../core/protocol'
import { statusPresentation } from '../core/statusPresentation'
import type { TunerController, TunerUi } from '../core/tunerController'
import {
  PopupHost, createChips, createFader, createKnob, createPanel, createPedal, createPowerButton, primaryButton,
} from './components'
import { h } from './dom'


export const TAB_AMP = 0
export const TAB_EQ = 1
export const TAB_TUNER = 2
const TAB_TITLES = ['AMP', 'EQ', 'AKORT']

export const LINKEDIN_BUTTON = "LINKEDIN'DE BURAK BOZ"

export interface ScreenDeps {
  app: AppController
  tuner: TunerController
  /** Opens the developer's LinkedIn page in the system browser (Electron bridge). */
  openLinkedIn: () => void
  /** Called when the selected tab changes (the tuner listens to the microphone only on the AKORT tab). */
  onTab: (tab: number) => void
  logoUrl: string
}

export interface Screens {
  root: HTMLElement
  /** Currently selected tab. */
  tab(): number
  select(tab: number): void
}

const effects: ReadonlyArray<readonly [GuitarEffect, string]> = [
  [GuitarEffect.BYPASS, 'OFF'], [GuitarEffect.FLANGER, 'FLANGER'], [GuitarEffect.CHORUS, 'CHORUS'],
  [GuitarEffect.PHASER, 'PHASER'], [GuitarEffect.WAH, 'WAH'], [GuitarEffect.VIBRATO, 'VIBRATO'],
]
const reverbs: ReadonlyArray<readonly [ReverbType, string]> = [
  [ReverbType.PLATE, 'PLATE'], [ReverbType.ROOM, 'ROOM'], [ReverbType.SPRING, 'SPRING'],
]
const tones: ReadonlyArray<readonly [AmpType, string]> = [
  [AmpType.CLEAN, 'CLEAN'], [AmpType.ACOUSTIC, 'ACOUSTIC'], [AmpType.DRIVE, 'DRIVE'],
  [AmpType.DISTORTION, 'DISTORTION'], [AmpType.METAL, 'METAL'],
]
const bandLabels = ['100', '200', '400', '800', '1.6k', '3.2k', '6.4k']

function statusText(ui: UiState): string {
  switch (ui.status) {
    case 'starting': return 'Başlatılıyor…'
    case 'noBluetooth': return 'Bluetooth kullanılamıyor. Bilgisayarda Bluetooth (4.0+ LE) açık ve bir adaptör takılı olmalı.'
    case 'scanning': return 'Amfi aranıyor…'
    case 'notFound': return 'Amfi bulunamadı, aramaya devam ediliyor…'
    case 'gaveUp': return 'Amfi bulunamadı. Açık ve yakında olduğundan, telefonun bağlı olmadığından emin ol, sonra tekrar ara.'
    case 'pick': return 'Birden fazla Blaze bulundu, birini seç:'
    case 'connecting': return 'Bağlanıyor…'
    case 'connected': return ''
    case 'reconnecting': return 'Bağlantı koptu, yeniden bağlanılıyor…'
    case 'linkLost': return 'Bağlantı koptu ve amfiye ulaşılamıyor. Amfinin açık olduğundan emin ol.'
  }
}

export function buildScreens(d: ScreenDeps): Screens {
  const { app, tuner } = d
  const popups = new PopupHost()
  let tab = TAB_AMP

  // ---------------------------------------------------------------- header
  const led = h('div', { class: 'led', testid: 'led' }, h('i'), h('span', {}, 'ARANIYOR…'))
  const volume = createKnob({ label: 'ANA SES', range: Ranges.VOLUME, size: 'mid', onChange: (v) => app.setVolume(v), popups })
  const music = createKnob({ label: 'MÜZİK', description: 'Müzik sesi', range: Ranges.AUX_VOLUME, size: 'mid', onChange: (v) => app.setAuxVolume(v), popups })
  const logo = h('img', { class: 'logo', src: d.logoUrl, alt: 'Hakkında', testid: 'logo', tabindex: 0, role: 'button', onclick: () => openDrawer(true), onkeydown: (e: KeyboardEvent) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); openDrawer(true) } } })
  const tabButtons = TAB_TITLES.map((t, i) =>
    h('button', { class: 'tab', role: 'tab', testid: `tab-${t}`, onclick: () => select(i) }, t))
  const tabs = h('div', { class: 'tabs', role: 'tablist' }, ...tabButtons)

  // ---------------------------------------------------------------- AMP tab
  const gain = createKnob({ label: 'GAIN', range: Ranges.GAIN, size: 'big', onChange: (v) => app.setGain(v), popups })
  const effectChips = createChips(effects, (e) => app.setEffect(e))
  const delayPower = createPowerButton('Delay', () => app.toggleDelay())
  const delayKnobs = [
    createKnob({ label: 'ANA', description: 'Delay ANA', range: Ranges.MASTER_FX, size: 'sm', onChange: (v) => app.setMasterDelay(v), popups }),
    createKnob({ label: 'SEVİYE', description: 'Delay SEVİYE', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setDelayLevel(v), popups }),
    createKnob({ label: 'FEEDBACK', description: 'Delay FEEDBACK', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setDelayFeedback(v), popups }),
    createKnob({ label: 'SÜRE', description: 'Delay SÜRE', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setDelayTime(v), popups }),
  ]
  const reverbPower = createPowerButton('Reverb', () => app.toggleReverb())
  const reverbChips = createChips(reverbs, (r) => app.setReverbType(r))
  const reverbKnobs = [
    createKnob({ label: 'ANA', description: 'Reverb ANA', range: Ranges.MASTER_FX, size: 'sm', onChange: (v) => app.setMasterReverb(v), popups }),
    createKnob({ label: 'SEVİYE', description: 'Reverb SEVİYE', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setReverbLevel(v), popups }),
    createKnob({ label: 'TON', description: 'Reverb TON', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setReverbTone(v), popups }),
    createKnob({ label: 'SÜRE', description: 'Reverb SÜRE', range: Ranges.EFFECT_KNOB, size: 'sm', onChange: (v) => app.setReverbTime(v), popups }),
  ]
  const ampTab = h('div', { class: 'amptab' },
    createPanel('', null, h('div', { class: 'gainrow' }, gain.el,
      h('div', { class: 'efx' }, h('div', { class: 'kl left' }, 'EFEKT'), effectChips.el))),
    createPanel('DELAY', delayPower.el, h('div', { class: 'knobs' }, ...delayKnobs.map((k) => k.el))),
    createPanel('REVERB', reverbPower.el, reverbChips.el, h('div', { class: 'knobs' }, ...reverbKnobs.map((k) => k.el))))

  // ---------------------------------------------------------------- EQ tab
  const faders = bandLabels.map((l, i) => createFader(l, Ranges.EQ_LEVEL, (v) => app.setEq(i, v)))
  const eqTab = h('div', { class: 'eqtab' },
    h('section', { class: 'panel eqpanel' }, h('div', { class: 'eq' }, ...faders.map((f) => f.el))),
    h('button', { class: 'reset', testid: 'btn-SIFIRLA', onclick: () => app.resetEq() }, '↺  SIFIRLA'))

  // ---------------------------------------------------------------- tuner tab
  const tn = {
    note: h('div', { class: 'note', testid: 'tuner-note' }, '—'),
    nd: h('div', { class: 'nd' }),
    cents: h('div', { class: 'c', testid: 'tuner-cents' }, '—'),
    ok: h('div', { class: 'ok', testid: 'tuner-status' }, 'Bir tel çal'),
    hz: h('div', { class: 'm', testid: 'tuner-hz' }, '—'),
    str: h('div', { class: 'm', testid: 'tuner-string' }, '—'),
  }
  const bar = h('div', { class: 'bar' }, h('div', { class: 'mk' }), tn.nd)
  const tunerTab = h('div', { class: 'tn idle', testid: 'tuner' }, tn.note, bar, tn.cents, tn.ok, tn.hz, tn.str,
    h('div', { class: 'hint' }, "En iyi sonuç için amfiyi Clean tonda, düşük sesle kullan ve bilgisayarı amfiye yakın tut. Distortion'da algılama zorlaşabilir."))

  const IN_TUNE_CENTS = 5
  const paintTuner = (u: TunerUi) => {
    if (u.kind === 'error') {
      tunerTab.className = 'tn idle'
      tn.note.textContent = '—'; tn.cents.textContent = '—'; tn.hz.textContent = '—'; tn.str.textContent = '—'
      tn.ok.textContent = u.message
      tn.nd.style.display = 'none'
      return
    }
    if (u.kind === 'nosignal') {
      tunerTab.className = 'tn idle'
      tn.note.textContent = '—'; tn.cents.textContent = '—'; tn.hz.textContent = '—'; tn.str.textContent = '—'
      tn.ok.textContent = 'Bir tel çal'
      tn.nd.style.display = 'none'
      return
    }
    const n = u.note
    const inTune = Math.abs(n.cents) <= IN_TUNE_CENTS
    tunerTab.className = `tn on${inTune ? ' intune' : Math.abs(n.cents) <= 15 ? ' near' : ' far'}`
    tn.note.textContent = `${n.name}${n.octave}`
    tn.cents.textContent = `${n.cents >= 0 ? '+' : '−'}${Math.abs(Math.round(n.cents))} cent`
    tn.ok.textContent = inTune ? 'Akortlu' : ' '
    tn.hz.textContent = `${n.frequency.toFixed(1)} Hz`
    tn.str.textContent = n.string ? `${n.string}. tel` : '—'
    tn.nd.style.display = ''
    tn.nd.style.left = `${50 + (Math.max(-50, Math.min(50, n.cents)) / 50) * 50}%`
  }
  tuner.ui.subscribe(paintTuner)

  // ---------------------------------------------------------------- connection card
  const cardText = h('p', { testid: 'card-text' })
  const cardAction = h('div', { class: 'cardact' })
  const card = h('div', { class: 'card', testid: 'connection-card' }, cardText, cardAction)

  // ---------------------------------------------------------------- dock (pedals)
  const pedals = tones.map(([type, name]) => ({ type, ...createPedal(name, () => app.onTone(type)) }))
  const dock = h('div', { class: 'dock', role: 'radiogroup', 'aria-label': 'Ton' }, ...pedals.map((p) => p.el))

  // ---------------------------------------------------------------- about drawer
  const drawerEl = h('div', { class: 'drawerwrap', testid: 'drawer', 'aria-hidden': 'true' },
    h('div', { class: 'scrim', onclick: () => openDrawer(false) }),
    h('aside', { class: 'drawer', role: 'dialog', 'aria-label': 'Hakkında' },
      h('img', { class: 'dlogo', src: d.logoUrl, alt: '' }),
      h('div', { class: 'dname' }, 'BLAZE 30 CONTROLLER'),
      h('div', { class: 'dsub' }, 'Yalnızca Kozmos Blaze 30 amfisiyle çalışır'),
      h('p', { class: 'dtext' },
        'Amfinin tonunu, gain ve ses ayarlarını, EQ\'sunu, delay ve reverb\'ünü Bluetooth ile bilgisayardan yönetirsin; ' +
        'akort için bilgisayarın mikrofonunu kullanır. Yalnızca Bluetooth ve mikrofon kullanır, internet erişimi yoktur; ' +
        'ses ve verilerin bilgisayardan dışarı çıkmaz.'),
      h('div', { class: 'ddev' }, 'Geliştirici: Burak Boz'),
      primaryButton(LINKEDIN_BUTTON, () => d.openLinkedIn())))
  function openDrawer(open: boolean) {
    drawerEl.classList.toggle('open', open)
    drawerEl.setAttribute('aria-hidden', String(!open))
  }
  // swipe in from the left edge (touch) opens the drawer
  let edgeStart: { x: number; id: number } | null = null
  const root = h('div', { class: 'app brushed', testid: 'app' },
    h('div', { class: 'hl' }, logo, h('div', {}, led)),
    tabs,
    h('div', { class: 'hv' }, volume.el, music.el),
    h('div', { class: 'content', testid: 'content' }, ampTab, eqTab, tunerTab, card),
    h('div', { class: 'dockwrap' }, dock),
    drawerEl)
  root.addEventListener('pointerdown', (e) => { if (e.pointerType === 'touch' && e.clientX < 24) edgeStart = { x: e.clientX, id: e.pointerId } })
  root.addEventListener('pointermove', (e) => {
    if (edgeStart && e.pointerId === edgeStart.id && e.clientX - edgeStart.x > 48) { openDrawer(true); edgeStart = null }
  })
  root.addEventListener('pointerup', () => { edgeStart = null })
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') openDrawer(false) })

  // ---------------------------------------------------------------- state -> view
  const content = root.querySelector('.content') as HTMLElement
  function select(i: number) {
    tab = i
    popups.close()
    tabButtons.forEach((b, n) => { b.classList.toggle('on', n === i); b.setAttribute('aria-selected', String(n === i)) })
    ampTab.hidden = i !== TAB_AMP
    eqTab.hidden = i !== TAB_EQ
    tunerTab.hidden = i !== TAB_TUNER
    render(app.ui.value)
    d.onTab(i)
  }

  function render(ui: UiState) {
    const p = statusPresentation(ui.status)
    const en = p.controlsEnabled
    const a = ui.amp
    led.className = `led ${p.led === 'connected' ? '' : p.led === 'searching' ? 'warn' : 'off'}`
    led.querySelector('span')!.textContent = p.label
    volume.set(a.volume, en)
    music.set(a.auxVolume, en)
    gain.set(a.gain, en)
    effectChips.set(a.effect, en)
    delayPower.set(ui.delayOn, en)
    ;[a.masterDelay, a.delayLevel, a.delayFeedback, a.delayTime].forEach((v, i) => delayKnobs[i].set(v, en))
    reverbPower.set(ui.reverbOn, en)
    reverbChips.set(a.reverbType, en)
    ;[a.masterReverb, a.reverbLevel, a.reverbTone, a.reverbTime].forEach((v, i) => reverbKnobs[i].set(v, en))
    faders.forEach((f, i) => f.set(a.eq[i], en))
    pedals.forEach((pd) => pd.set(a.ampType === pd.type, en))
    content.classList.toggle('dim', !en)
    dock.classList.toggle('dim', !en)
    if (!en) popups.close() // a popup left open while the link drops would keep "setting" values the amp never gets

    const showCard = tab !== TAB_TUNER && ui.status !== 'connected'
    card.hidden = !showCard
    if (showCard) {
      cardText.textContent = statusText(ui)
      cardAction.replaceChildren(...cardActions(ui))
    }
  }

  function cardActions(ui: UiState): HTMLElement[] {
    switch (ui.status) {
      case 'pick':
        return ui.devices.map((dev) =>
          h('button', { class: 'dev', testid: `device-${dev.address}`, onclick: () => app.pick(dev) }, `${dev.name}  ${dev.address}`))
      case 'noBluetooth': case 'notFound': case 'gaveUp': case 'starting': case 'scanning':
        return [primaryButton('Tekrar ara', () => app.retry())]
      case 'linkLost': return [primaryButton('Tekrar bağlan', () => app.retry())]
      default: return []
    }
  }

  app.ui.subscribe(render)
  select(TAB_AMP)
  return { root, tab: () => tab, select }
}
