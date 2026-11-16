# Blaze 30 Controller — Windows Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (the user chose native/inline execution for this project). Steps use checkbox (`- [ ]`) syntax.

**Goal:** A Windows desktop app (touch + mouse) with the same controls, sync, tuner and look as the Android app, shipped as one portable `.exe`.

**Architecture:** Pure-TypeScript core (protocol, state, repository, tuner, UI logic) behind a small `AmpTransport` interface, a Web-Bluetooth transport, an HTML/CSS/TS UI, and a thin hardened Electron shell. The core is a line-by-line port of the Android Kotlin core, so the Kotlin sources and their unit tests are the executable reference.

**Tech Stack:** TypeScript, Vite, Vitest, Electron (Chromium Web Bluetooth + `getUserMedia`), Playwright (Electron mode) for UI tests, electron-builder (portable target).

**Spec:** `docs/superpowers/specs/2026-11-15-windows-app-design.md` (read it first). Visual reference (approved look): `docs/superpowers/specs/ui-redesign-mockups/5-final-all-screens.html`. Protocol facts: `docs/protocol.md`.

## Global Constraints
- Code lives in `windows/`; Electron-independent code in `windows/src/core/` (no DOM, no Node APIs) so Vitest runs it headless.
- Port source of truth (read each before porting; port its tests case-for-case with the same numbers): `app/src/main/java/dev/blaze/controller/{protocol,amp,ble,tuner}/`, `.../ui/{SliderModel,SliderThrottle,PowerMemory,HomeViewModel}.kt`, `.../ui/components/KnobMath.kt`; tests under `app/src/test/java/dev/blaze/controller/`.
- Wire protocol exactly as `ProtocolCodec.kt` (frames `FE 0D G I S V 00 00 00 FF`, notifications 16 bytes `FE 0F … FF`). Sync frame `FE0DAB413442000000FF` on every new link. The factory-reset frame must be unrepresentable. Values are clamped, never sent raw.
- BLE: service `0000fff0-0000-1000-8000-00805f9b34fb`, write `fff3`, notify `fff2`. Device name prefix `BLAZE-30`.
- Privacy: no network requests; CSP `default-src 'self'`; `contextIsolation: true`, `nodeIntegration: false`, `sandbox: true`; only the LinkedIn URL `https://www.linkedin.com/in/burakbozb1` may be opened externally.
- Language: UI text Turkish, same strings as Android (`ui/screens/*.kt`, `ui/theme/Pedalboard.kt` `HomeStatus.presentation()`). Developer name "Burak Boz".
- Repo is public: no personal/device data, keys, or serials in any file or test fixture. Fake BLE address `AA:BB:CC:00:00:01`.
- Dev machine has no Bluetooth radio: nothing may require one in tests. Real-amp checks are the user's.
- Windows path has a non-ASCII char: run all commands from the `X:` drive (`subst X: "<project dir>"`, `cd /x/windows`).
- Commits end with `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus
- Rapid double toggle of delay/reverb power (PowerMemory pending window) and reset-EQ sending 7 frames in order.
- Notification frames of wrong length/magic, or a `BB` frame with unknown amp type, must never throw or corrupt state.
- A disconnect mid-drag or mid-write: queue keeps serving, popup closes, no stale slider snap-back (SliderModel rounds, never truncates).
- Mic stream must be released when leaving the AKORT tab and when the window hides/closes (indicator off).
- Chooser cancelled / no amp in range / adapter missing or off: bounded retries, then a manual "TEKRAR DENE"; never an infinite loop, never a crash.
- Touch: knob vertical drag and fader drag must not scroll/zoom the page; double-tap opens the popup; hit targets ≥ 44 px; layout works from 800×480 to full screen.

---

### Task 1: Scaffold and toolchain
**Files:** Create `windows/package.json`, `windows/tsconfig.json`, `windows/vite.config.ts`, `windows/vitest.config.ts`, `windows/index.html`, `windows/src/core/index.ts`, `windows/src/smoke.test.ts`; Modify `.gitignore` (`windows/node_modules/`, `windows/dist/`, `windows/release/`).
**Interfaces:** Produces npm scripts `test` (vitest run), `typecheck` (tsc --noEmit), `dev`, `build`, `test:ui`, `dist`.
- [ ] Step 1: Write `src/smoke.test.ts` asserting `1 + 1 === 2`; run `npx vitest run` → FAIL (no tooling yet).
- [ ] Step 2: `npm init`, install `typescript vite vitest electron electron-builder @playwright/test` (exact current versions, pinned, no `^`); create the config files; strict TS, ES2022, DOM lib only for `src/ui`.
- [ ] Step 3: `npm test` and `npm run typecheck` → PASS. Commit.

### Task 2: Protocol codec
**Files:** Create `windows/src/core/protocol.ts`, `windows/src/core/protocol.test.ts`.
**Interfaces:** Produces `AmpType`, `GuitarEffect`, `ReverbType` (numeric const objects with the Kotlin codes), `Ranges` (`GAIN 0..30, VOLUME 0..50, EQ_LEVEL 0..20, EQ_BANDS 7, EFFECT_KNOB 0..20, MASTER_FX 0..50`), `type AmpCommand` (discriminated union with the same 15 variants as Kotlin, `kind` field; `SetEqBand` throws RangeError for index outside 0..6), `encode(cmd): Uint8Array` (10 bytes), `decode(frame: Uint8Array): AmpCommand[]` (never throws).
- [ ] Step 1: Port every case of `ProtocolCodecTest.kt` and `ProtocolCodecDecodeTest.kt` (same hex strings and expected commands); run → FAIL.
- [ ] Step 2: Implement exactly as `ProtocolCodec.kt` (clamping, `BB/BC/C0/02` decode, ignore non-16-byte or bad magic). Add a test that no `encode` output equals `FE0D01010000000000FF` for any command over its full value range.
- [ ] Step 3: Run → PASS. Commit.

### Task 3: AmpState
**Files:** Create `windows/src/core/ampState.ts`, `ampState.test.ts`.
**Interfaces:** Consumes Task 2. Produces `interface AmpState` (same 14 fields, `eq: number[]`), `DEFAULT_AMP_STATE`, `applyCommand(s: AmpState, c: AmpCommand): AmpState` (pure, immutable, clamps).
- [ ] Step 1: Port `AmpStateTest.kt`; run → FAIL. Step 2: implement as `AmpState.kt`; run → PASS. Commit.

### Task 4: Transport interface and AmpRepository
**Files:** Create `windows/src/core/transport.ts`, `fakeTransport.ts`, `ampRepository.ts`, `ampRepository.test.ts`, `fakeTransport.test.ts`.
**Interfaces:** Consumes Tasks 2–3. Produces:
`type ConnectionState = {kind:'disconnected'|'connecting'|'connected'} | {kind:'failed', reason:string}`; `interface FoundDevice {name:string; address:string}`;
`interface AmpTransport { connection: Observable<ConnectionState>; onIncoming(cb:(f:Uint8Array)=>void): () => void; scan(signal: AbortSignal): AsyncIterable<FoundDevice>; connect(d: FoundDevice): Promise<void>; write(b: Uint8Array): Promise<void> /* rejects Error when not connected */; disconnect(): void }`;
a tiny `Observable<T>` (`get value`, `subscribe(cb): unsubscribe`, `set` on a `MutableObservable`);
`class AmpRepository { constructor(transport, opts?: {reconnectDelayMs=1000, syncSettleMs=150, maxReconnectAttempts=8, maxReconnectDelayMs=10000, timers?}) ; state: Observable<AmpState>; connection; gaveUp: Observable<boolean>; connect(d); reconnect(); disconnect(); refresh(); send(c) }` — same semantics as `AmpRepository.kt` (ordered queue, Sync after every new link, no replay, read-only mirror of incoming, exponential backoff capped, give-up flag). `FakeTransport` records writes and lets tests push frames/connection states. Timers are injectable (fake timers in tests).
- [ ] Step 1: Port `AmpRepositoryTest.kt` and `FakeAmpTransportTest.kt` case-for-case (Vitest fake timers); run → FAIL.
- [ ] Step 2: Implement; a failed/rejected write must drop that command and keep serving the queue. Step 3: PASS. Commit.

### Task 5: UI logic helpers
**Files:** Create `windows/src/core/sliderThrottle.ts`, `sliderModel.ts`, `powerMemory.ts`, `knobMath.ts` and their `.test.ts`.
**Interfaces:** Produces `SliderThrottle(minIntervalMs, now)` (`onChange/onFinished/reset`), `SliderModel(initial, throttle)` (`local`, `onDrag(v): number|null`, `onFinish()`, `onExternal(v)`; uses `Math.round`), `PowerMemory(opts?)` (`observe`, `levelAfterToggle`; defaults 20 / 1000 ms), `KnobMath` (`FULL_RANGE_PX=220`, `SWEEP_DEGREES=270`, `sweepDegrees`, `valueAfterDrag`, `step`). Note `Math.round(14.5)` is 15 in JS but Kotlin's `roundToInt` agrees for positives; negatives are not used.
- [ ] Step 1: Port `SliderThrottleTest`, `SliderModelTest` (incl. fractional rounding case), `PowerMemoryTest`, `KnobMathTest`; run → FAIL. Step 2: implement. Step 3: PASS. Commit.

### Task 6: Tuner core
**Files:** Create `windows/src/core/noteMath.ts`, `pitchDetector.ts`, `tunerController.ts` and tests.
**Interfaces:** Produces `NoteReading {name, octave, cents, frequency, string: number|null}`, `noteFromFrequency(hz)`, `class PitchDetector(opts)` with `detect(samples: Int16Array): number|null` (YIN, defaults 44100/55/1100/0.15/0.01), `type TunerUi = {kind:'nosignal'}|{kind:'reading', note}|{kind:'error', message}`, `interface AudioSource { frames(): AsyncIterable<Int16Array> }`, `class TunerController(source, opts?{holdFrames=5, medianSize=5, minDetections=3}) { ui: Observable<TunerUi>; run(signal: AbortSignal): Promise<void> }` (median = lower middle element; error message "Mikrofon kullanılamıyor").
- [ ] Step 1: Port `NoteMathTest`, `PitchDetectorTest` (synthetic sines), `TunerControllerTest`; run → FAIL. Step 2: implement as Kotlin. Step 3: PASS. Commit.

### Task 7: AppController (status machine and actions)
**Files:** Create `windows/src/core/appController.ts`, `appController.test.ts`.
**Interfaces:** Consumes Tasks 3–6. Produces `type Status = 'starting'|'noBluetooth'|'scanning'|'notFound'|'gaveUp'|'pick'|'connecting'|'connected'|'reconnecting'|'linkLost'`; `interface UiState {status; devices: FoundDevice[]; amp: AmpState; delayOn; reverbOn}`; `class AppController(repo, transport, opts?{scanWindowMs=4000, rescanDelayMs=3000, maxScanWindows=8, available?: ()=>Promise<boolean>})` with `ui: Observable<UiState>`, `start()` (checks adapter availability → `noBluetooth`, else scan loop), `retry()`, `pick(d)`, `refresh()`, and the action methods `onTone, setGain, setVolume, setEq, setMasterDelay, setMasterReverb, setEffect, setReverbType/Level/Tone/Time, setDelayLevel/Feedback/Time, toggleDelay, toggleReverb, resetEq` (7 `SetEqBand(i,10)` in order) — semantics of `HomeViewModel.kt`. `controlsEnabled` rule: only `connected` (`statusPresentation(status)` returns `{ledColor:'green'|'amber'|'red', label, controlsEnabled}` using the Android strings).
- [ ] Step 1: Port `HomeViewModelTest`, `PowerAndEqViewModelTest`, `SoundViewModelTest`, `StatusPresentationTest` (adapted statuses; permission cases dropped, `noBluetooth` added); run → FAIL. Step 2: implement. Step 3: PASS. Commit.

### Task 8: Web Bluetooth transport and microphone source
**Files:** Create `windows/src/platform/webBluetoothTransport.ts`, `webBluetoothTransport.test.ts`, `micAudioSource.ts`, `micWorklet.js` (AudioWorklet buffering to 4096-sample Int16 frames).
**Interfaces:** Consumes Task 4/6 interfaces. Produces `class WebBluetoothTransport implements AmpTransport` (`scan()` = one `navigator.bluetooth.requestDevice({filters:[{namePrefix:'BLAZE-30'}], optionalServices:[FFF0]})`, yields the chosen device, ends; throws/ends empty on `NotFoundError`/cancel; `connect` = `gatt.connect()`, get service/characteristics, `startNotifications` on `fff2`, listener on `gattserverdisconnected`; `write` = `writeValueWithResponse` with a 3 s timeout; Connecting/Connected/Disconnected/Failed states) and `class MicAudioSource implements AudioSource` (`getUserMedia({audio:{echoCancellation:false,noiseSuppression:false,autoGainControl:false}})`, `AudioContext({sampleRate:44100})`, stops tracks and closes the context when the iterator ends/aborts).
- [ ] Step 1: Vitest with hand-written fake `navigator.bluetooth`/GATT objects: connect flow, notification → `onIncoming` with copied bytes, disconnect event → state, write timeout rejects, second `connect` reuses the known device object without a chooser; run → FAIL. Step 2: implement. Step 3: PASS. Commit.

### Task 9: User interface
**Files:** Create under `windows/src/ui/`: `main.ts`, `app.css`, `components/{knob,popup,pedal,powerButton,led,tabs,chips,eqFader,panel,aboutDrawer}.ts`, `screens/{ampTab,eqTab,tunerTab,connectionCard,header}.ts`; copy `Kaynak/Logo.png` → `windows/src/ui/assets/logo.png` (+ 256 px version) (the user owns it; `Kaynak/` itself stays untracked).
**Interfaces:** Consumes `AppController`, `TunerController`, `KnobMath`, `SliderModel`. Test hooks: `data-testid` identical to Android tags (`knob-<label>`, `popup-value|plus|minus|title`, `tab-<title>`, `chip-<text>`, `power-<label>`, `fader-<label>`, `fader-value-<label>`, `btn-<text>`, `logo`, pedals by name).
- [ ] Step 1: Build static markup/CSS from mockup 5 (colors from `ui/theme/Pedalboard.kt` `Pb`); SVG knobs (red arc concentric with the cap), CSS brushed-black background, hex-nut pedals with red ring when active, power buttons without text.
- [ ] Step 2: Behavior with Pointer Events: `touch-action: none` on knobs/faders; vertical drag via `KnobMath.valueAfterDrag` + `SliderModel`; double-tap/double-click → popup (−/+, slider, TAMAM, closes when controls disabled); EQ faders with `SIFIRLA`; tabs AMP | EQ | AKORT (tab bar in the header row when width > height ≥ landscape rule, else under it); header = logo (opens about drawer) + LED + ANA SES knob; drawer from left edge swipe or logo click with About text, "Yalnızca Kozmos Blaze 30 amfisiyle çalışır", "Geliştirici: Burak Boz" and the LinkedIn button (`window.blaze.openLinkedIn()` exposed by preload). Dimmed (single layer) and non-interactive when not connected, with the connection card (retry / "AMFİYİ BUL") on AMP/EQ; AKORT stays usable.
- [ ] Step 3: Tuner tab: starts `TunerController.run` only while AKORT is selected and the window is visible; fixed-size panel with active/inactive look as on Android.
- [ ] Step 4: `npm run typecheck`, `npm run build` clean. Commit.

### Task 10: Electron shell
**Files:** Create `windows/electron/main.ts`, `preload.ts`, `windows/electron/tsconfig.json`.
**Interfaces:** Produces window (min 800×480, remembers nothing), `preload` exposes only `window.blaze = { openLinkedIn(): void }`.
- [ ] Step 1: `main.ts`: `session.defaultSession.setPermissionRequestHandler` allows only `media` (audio) for the app's own origin; `webContents.on('select-bluetooth-device')` collects candidates for up to 3 s, picks the first whose name starts with `BLAZE-30` (calls `callback('')` if none; if several, the lowest-signal-agnostic first plus emit nothing else), `setWindowOpenHandler` denies all, `will-navigate` blocked, CSP header `default-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; worklet/script from self`, `shell.openExternal` only for the exact LinkedIn URL. On start, trigger the first scan with `executeJavaScript('window.__blazeStart()', true)` so Web Bluetooth gets a user-gesture.
- [ ] Step 2: Unit test the pure helper `pickBlaze(devices)` and `isAllowedExternal(url)` with Vitest (not Electron). Step 3: `npm run dev` smoke: window opens, UI renders, status shows `noBluetooth`/"Bluetooth yok" (this PC has no radio). Commit.

### Task 11: UI tests and screenshots
**Files:** Create `windows/tests-ui/*.spec.ts`, `windows/playwright.config.ts`; the app exposes `?fake=1` (or env `BLAZE_FAKE=1`) wiring `FakeTransport` pre-connected for tests only (never bundled as default).
- [ ] Step 1: Write failing Playwright-Electron specs: pedal click sends `SetAmpType` and lights its ring; knob drag changes value and writes a frame; double-click opens popup, `+`/`−` step (16 then 17 even without echo), TAMAM closes; power button toggles delay level 0 ↔ remembered; SIFIRLA sends 7 frames; tab switch; at 600×800 tabs under the header, at 1100×600 tabs in the header row; touch emulation (`hasTouch`) drag on a fader does not scroll; AKORT shows the idle panel; mic stream stops after leaving AKORT (assert fake `getUserMedia` track `readyState==='ended'`); disabled when disconnected; logo opens drawer and LinkedIn button calls the bridge with the exact URL; no outgoing network request (route abort + assert none).
- [ ] Step 2: Run → FAIL, fix until PASS; produce screenshots (`windows/screens/*.png`, git-ignored) of AMP/EQ/AKORT/drawer at 1100×650 and 800×480 and view them for visual defects against mockup 5. Commit.

### Task 12: Packaging, docs, release candidate
**Files:** Create `windows/electron-builder.yml`, `windows/build/icon.ico` (from `logo.png`, multi-size), `windows/README.md`; Modify root `README.md` (Windows section + download link pattern `releases/latest/download/Blaze-30-Controller-Windows.exe`), `docs/backlog.md`.
- [ ] Step 1: `npm run dist` → `windows/release/Blaze-30-Controller-Windows.exe` (portable, unsigned, `artifactName` fixed); record SHA-256. Launch the built exe once, confirm the window opens and shows the Bluetooth-missing state.
- [ ] Step 2: README: download box (SmartScreen "Ek bilgi → Yine de çalıştır" note, SHA-256, "Bluetooth 4.0+/5.0 LE adaptör", "amfi aynı anda tek cihaza bağlanır — telefon bağlıysa kapat"), privacy statement, build instructions.
- [ ] Step 3: Full verification: `npm test`, `npm run typecheck`, `npm run test:ui` all green; leak scan of tracked files (no personal data/keys). Commit. **Do not push or publish a release** — the user approves that after hands-on testing on the laptop.

## Final review
One fresh opus reviewer over the whole `windows/` branch (package via `review-package`), Review Focus above passed verbatim; one fix pass (RED→GREEN each).
