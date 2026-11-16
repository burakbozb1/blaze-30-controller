# Müzik (AUX) Ses Kontrolü Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (the user chose native/inline execution). Steps use checkbox (`- [ ]`) syntax.

**Goal:** A second header knob "MÜZİK" (AUX volume 0–50) next to "ANA SES" on Android and Windows, mirrored from the amp.

**Architecture:** One new command `SetAuxVolume` (write `FE 0D 04 01 00 VV 00 00 00 FF`, live notification `FE 0F 04 01 00 VV …`) flows through the existing codec → AmpState → ViewModel/AppController → header knob on both platforms. Dump (`BF`) parsing is added only after hardware verification (Task 3).

**Tech Stack:** Kotlin/Compose (app/), TypeScript/Electron (windows/); existing JUnit, Compose, Vitest, Playwright suites.

**Spec:** `docs/superpowers/specs/2026-11-16-aux-volume-design.md`

## Global Constraints
- Range `0..50` (same as GUI VOL). Default state value 10. Clamp, never send raw.
- Do NOT touch the factory-reset rule; the new frame must never equal `FE0D01010000000000FF`.
- The command is **unverified on the amp until Task 3** passes: no release/push before that. Dev builds only.
- Public repo: no personal/device data in code, tests, logs or docs; fake BLE address `AA:BB:CC:00:00:01`.
- Accessible name / test tag of the new knob: description `Müzik sesi`, label text `MÜZİK` (Android tag `knob-Müzik sesi`; Windows `data-testid="knob-Müzik sesi"`).
- Windows commands run from the `X:` drive (`subst X: "<project dir>"`); unset `ELECTRON_RUN_AS_NODE` before launching Electron. Commit trailer `Co-Authored-By: Claude Sonnet 5.5 <noreply@anthropic.com>`.

## Review Focus
- The existing codec tests that assert the aux frame is ignored must change to assert it is decoded; every other `04 …` frame stays ignored (e.g. `04 01 01`, `04 02 …`).
- Master-pot sweeps send `02 01 00` and `04 01 00` together: both knobs must follow, in order, without extra writes.
- A not-connected app: the header knob is dimmed/disabled like ANA SES and its popup closes on disconnect.
- Narrow phone / small window: two header knobs must not overlap the LED, logo or tabs (portrait 393 dp, landscape, Windows 800×480).
- The user turning MÜZİK to 0 must send exactly one `FE0D04010000000000FF`-style frame (value byte 0), not a reset.

---

### Task 1: Android core (codec, state, actions)
**Files:** Modify `app/src/main/java/dev/blaze/controller/protocol/AmpCommand.kt`, `protocol/ProtocolCodec.kt`, `amp/AmpState.kt`, `ui/AmpActions.kt`, `ui/HomeViewModel.kt`; Test `app/src/test/.../protocol/ProtocolCodecTest.kt`, `ProtocolCodecDecodeTest.kt`, `amp/AmpStateTest.kt`, `ui/SoundViewModelTest.kt`.
**Interfaces:** Produces `Ranges.AUX_VOLUME = 0..50`, `AmpCommand.SetAuxVolume(value: Int)`, `AmpState.auxVolume: Int` (default 10), `AmpActions.setAuxVolume(v: Int)`, `HomeViewModel.setAuxVolume` (sends `SetAuxVolume`).
- [ ] Step 1: Tests (RED): `encode(SetAuxVolume(10)) == FE0D0401000A000000FF`, `999 → 0x32`, `-3 → 0`, value 0 frame `FE0D04010000000000FF` is not the factory reset; decode `live(0x04,0x01,0x00,25) == [SetAuxVolume(25)]`; change `ignoresHeartbeatAuxVolumeAndTheOtherDumps` so the aux frame is no longer in the ignored list and add ignored `live(0x04,0x01,0x01,5)` and `live(0x04,0x02,0x00,5)`; `AmpState` apply + clamp (`SetAuxVolume(999).auxVolume == 50`); ViewModel `setAuxVolume(12)` writes the encoded frame and updates `ui.amp.auxVolume`; a knob sweep of `02 01 00`+`04 01 00` pairs updates both volume and auxVolume. Run `:app:testDebugUnitTest` → FAIL (does not compile).
- [ ] Step 2: Implement the types, codec branch `item==0x01 && sub==0x00` for group `0x04`, state apply, VM action. Keep `decodeLiveGuitar` for group 0x02 untouched; add a group-0x04 branch in `decode`.
- [ ] Step 3: `:app:testDebugUnitTest` → PASS. Commit.

### Task 2: Android UI (header knob) and tests
**Files:** Modify `ui/screens/MainScreen.kt` (header: add a second `Knob` "MÜZİK", description `Müzik sesi`, size Medium, range `Ranges.AUX_VOLUME`, `actions::setAuxVolume`), `androidTest/.../MainScreenTest.kt` (`RecordingActions.setAuxVolume`), `ScreensShotTest.kt` (`NoActions.setAuxVolume`); Test `MainScreenTest.kt`.
**Interfaces:** Consumes Task 1.
- [ ] Step 1: Device tests (RED): knob `knob-Müzik sesi` exists with the amp's auxVolume as progress; setting progress calls `auxVolume:<n>`; it is disabled when status is not Connected; double tap opens the popup titled `Müzik sesi`; portrait 393 dp and landscape: the two header knobs, the LED/logo and (landscape) the tab bar do not overlap (compare bounds).
- [ ] Step 2: Implement; header row = logo + LED (weight 1) | tabs (landscape) | ANA SES knob | MÜZİK knob. Run `:app:connectedDebugAndroidTest` (phone must be connected; see Task 3 for pairing) and view screenshots via `ScreensShotTest`.
- [ ] Step 3: Commit. (If the phone is not paired yet, compile with `:app:assembleDebug androidTest` first and run the device tests when paired.)

### Task 3: Hardware verification build and protocol facts (needs the user)
**Files:** Modify `app/src/main/java/dev/blaze/controller/ble/BleAmpTransport.kt` (debug-only raw frame log: `if (BuildConfig.DEBUG) Log.d("AmpRx", hex)` for each notification and `AmpTx` for each write; `buildFeatures { buildConfig = true }` in `app/build.gradle.kts` if needed), `docs/protocol.md`.
- [ ] Step 1: Install the debug build on the phone (`installDebug`, after uninstalling the release-signed copy), ask the user to pair adb (wireless debugging code), capture `adb logcat -s AmpRx AmpTx`.
- [ ] Step 2: With the user: (a) play music over Bluetooth, turn MÜZİK up/down in the app: does the music volume change, and does the log show `FE0D04 01 00 VV`? (b) turn the amp's master pot: do `04 01 00` frames arrive and does the MÜZİK knob follow? (c) disconnect/reconnect (Sync): note which dump byte equals the AUX value you set (try distinct values, e.g. 7, then 31).
- [ ] Step 3: Record findings in `docs/protocol.md` as `verified`/`observed`. If the dump position is confirmed, add it to `decode` with a test using the captured frame; otherwise add the documented fallback note. Commit. If (a) fails, STOP and report to the user (no release).

### Task 4: Windows (core, UI, tests)
**Files:** Modify `windows/src/core/protocol.ts` (`Ranges.AUX_VOLUME`, `AmpCommand` `SetAuxVolume`, `Cmd.setAuxVolume`, encode, decode group 0x04), `ampState.ts` (`auxVolume`, default 10, apply), `appController.ts` (`setAuxVolume`), `ui/screens.ts` (header knob `MÜZİK`, description `Müzik sesi`, in `.hv` next to ANA SES; render `volume`-like), `ui/app.css` (`.hv` becomes a flex row with a gap); Test `protocol.test.ts`, `ampState.test.ts`, `appController.test.ts`, `tests-ui/amp.spec.ts`.
**Interfaces:** Consumes the same wire facts as Task 1.
- [ ] Step 1: Port the Task 1 tests (same vectors) and a UI spec: `knob-Müzik sesi` drag writes `[0xfe,0x0d,0x04,0x01,0x00,VV,0,0,0,0xff]`; the header knobs do not overlap the logo/LED/tabs at 1100×650, 800×480 and 600×820. Run `npm test` / `npm run test:ui` → FAIL.
- [ ] Step 2: Implement. Step 3: all green (`npm test`, `npm run typecheck`, `npm run test:ui`), screenshot check. Commit.

### Task 5: Docs and release candidate (no push)
**Files:** Modify `README.md` (feature list: music volume), `CLAUDE.md` (status, aux frame no longer "not applied"), `docs/backlog.md` (remove the AUX item), `docs/protocol.md` (if not done).
- [ ] Step 1: Update docs. Step 2: Build the signed Android release APK (`BLAZE_RELEASE_PROPS`, version 0.2.0 / code 3) and the Windows exe (0.3.0 via `npm version minor`); report SHA-256s. Step 3: **Do not push or publish**; the user approves after hands-on testing.

## Final review
One fresh opus reviewer over the whole branch (review-package, plan Review Focus verbatim); one fix pass, each fix RED→GREEN.
