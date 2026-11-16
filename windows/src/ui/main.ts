import './app.css'
import { AmpRepository } from '../core/ampRepository'
import { AppController } from '../core/appController'
import { FakeTransport } from '../core/fakeTransport'
import { TunerController, type AudioSource } from '../core/tunerController'
import type { AmpTransport } from '../core/transport'
import { MicAudioSource } from '../platform/micAudioSource'
import { WebBluetoothTransport, type BtNavigator } from '../platform/webBluetoothTransport'
import logoUrl from './assets/logo.png'
import { LINKEDIN_URL } from '../shell/guards'
import { TAB_TUNER, buildScreens } from './screens'

interface ElectronBridge { openLinkedIn(): void }
declare global {
  interface Window {
    blaze?: ElectronBridge
    __blazeStart?: () => void
    __blazeTest?: unknown
  }
}

const fake = new URLSearchParams(location.search).get('fake') === '1'

/** Test-only microphone: the UI tests push frames and read how often it was opened and released. */
class FakeMic implements AudioSource {
  private queue: Int16Array[] = []
  private wake: (() => void) | null = null
  starts = 0
  releases = 0
  push(frame: Int16Array) { this.queue.push(frame); this.wake?.() }
  async *frames(signal: AbortSignal): AsyncIterable<Int16Array> {
    this.starts++
    try {
      while (!signal.aborted) {
        const f = this.queue.shift()
        if (f) { yield f; continue }
        await new Promise<void>((res) => { this.wake = res; signal.addEventListener('abort', () => res(), { once: true }) })
      }
    } finally {
      this.releases++
    }
  }
}

const missingBluetooth: BtNavigator = {
  requestDevice: () => Promise.reject(new Error('Web Bluetooth yok')),
  getAvailability: () => Promise.resolve(false),
}
const nav: BtNavigator = (navigator as unknown as { bluetooth?: BtNavigator }).bluetooth ?? missingBluetooth

const fakeTransport = new FakeTransport([{ name: 'BLAZE-30 APP', address: 'AA:BB:CC:00:00:01' }])
const transport: AmpTransport = fake ? fakeTransport : new WebBluetoothTransport(nav)
const mic: AudioSource = fake ? new FakeMic() : new MicAudioSource()
const repo = new AmpRepository(transport)
const app = new AppController(repo, transport, { available: fake ? async () => true : () => nav.getAvailability() })
const tuner = new TunerController(mic)

let tunerRun: AbortController | null = null
let currentTab = 0
const syncTuner = () => {
  const want = currentTab === TAB_TUNER && !document.hidden
  if (want && !tunerRun) {
    const ac = new AbortController()
    tunerRun = ac
    void tuner.run(ac.signal)
  } else if (!want && tunerRun) {
    tunerRun.abort()
    tunerRun = null
  }
}

const screens = buildScreens({
  app, tuner, logoUrl,
  openLinkedIn: () => (window.blaze ? window.blaze.openLinkedIn() : void window.open(LINKEDIN_URL, '_blank', 'noopener')),
  onTab: (t) => { currentTab = t; syncTuner() },
})
document.getElementById('app')!.append(screens.root)

document.addEventListener('visibilitychange', () => {
  syncTuner()
  if (!document.hidden) app.refresh()
})
window.addEventListener('beforeunload', () => { tunerRun?.abort(); repo.disconnect() })

if (fake) window.__blazeTest = { transport: fakeTransport, mic, app, repo }

// In Electron the main process starts the first scan with a user gesture (Web Bluetooth requires one).
window.__blazeStart = () => { void app.start() }
if (!window.blaze) void app.start()
