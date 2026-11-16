# UI Redesign ("Pedalboard") Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three plain screens with the approved pedalboard UI: header + tabs (AMP | EQ | AKORT) + sticky footswitch row, drawn knobs with a double-tap value popup, power buttons for delay/reverb, EQ reset, brushed-black background.

**Architecture:** A new component layer (`ui/theme`, `ui/components`) of Canvas-drawn controls on top of the existing, unchanged `HomeViewModel`/`AmpRepository`/`TunerController`. Pure logic (knob math, power memory, status presentation) is unit-tested on the JVM; visuals and gestures are verified by Compose tests and screenshots on the phone. `MainScreen` replaces `HomeScreen`, `SoundScreen`, `TunerScreen` at the end.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas, pointerInput), JUnit4 + coroutines-test, Compose UI tests on the phone.

**Spec:** `docs/superpowers/specs/2026-11-15-ui-redesign-design.md` — visual source of truth: `docs/superpowers/specs/ui-redesign-mockups/5-final-all-screens.html` (open it as text for exact colors/sizes; `3-…` and `4-…` for pedal/power-button detail).

**Build/run notes:** Gradle via `X:` (`subst X: "<project dir>"`), phone adb is wireless. Screenshots: `adb exec-out screencap -p > file.png`.

## Global Constraints

- Dark theme only; red accent. Tokens (from the mockup): red `#FF2D3D` (light `#FF6A3D`), red track `#2B1316`, text `#F2F2F2`, dim label `#B9B9C0`, panel border `#2A2A30`, panel fill `#1E1E22`@0.78 → `#0E0E11`@0.82, LED green `#25D85A`, amber `#F2A900`, LED red `#E0192B`. Body contrast ≥ 4.5:1.
- No bitmap/font assets: everything is drawn with `Canvas`/`Brush`; the brushed texture is generated once in code and cached. No new permissions, no new dependencies.
- Value ranges unchanged (`Ranges`): master volume 0–50, gain 0–30, master delay/reverb 0–50, other delay/reverb knobs 0–20, EQ 0–20 (flat = 10).
- Protocol, BLE, repository, sync and tuner algorithms are not changed. All existing functions stay reachable (tone, gain, volume, EQ, effect, reverb type/level/tone/time, delay level/feedback/time, master delay/reverb, tuner, permission/connection flows).
- Touch targets ≥ 48 dp; pedal column ≥ 64 dp wide × 96 dp tall; knob drag: full range = 220 dp of vertical travel, upward = increase; throttle ≤ 1 command / 60 ms with guaranteed final value (existing `SliderModel`).
- Labels are Turkish (BAĞLI, ANA SES, SEVİYE, SÜRE, TON, AKORT, SIFIRLA, TAMAM); tone/effect/reverb-type names stay English. The power button has **no** "AÇIK/KAPALI" text.

## Review Focus

1. Not connected / connecting / reconnecting → AMP and EQ controls and pedals are disabled and send nothing; the AKORT tab and tab bar stay usable (Tasks 1, 6, 7).
2. A knob or fader dragged while the amp echoes values → thumb never snaps back, final value always sent (Tasks 4, 5).
3. Power toggle edge cases: level changed on the amp, never non-zero, rapid double toggles → correct level, no stuck state (Task 3).
4. Leaving AKORT or backgrounding releases the microphone; rotation keeps the selected tab (Task 7).
5. Large font scale (1.3) and short screens → no control clipped or unreachable (Task 7 test).

---

### Task 1: Theme, tokens and status presentation

**Files:**
- Create: `app/src/main/java/dev/blaze/controller/ui/theme/Pedalboard.kt`
- Test: `app/src/test/java/dev/blaze/controller/ui/theme/StatusPresentationTest.kt`

**Interfaces — Produces:**
```kotlin
object Pb { val Red; val RedLight; val RedTrack; val Text; val TextDim; val Border; val PanelTop; val PanelBottom; val LedGreen; val LedAmber; val LedRed }
@Composable fun PedalboardTheme(content: @Composable () -> Unit)   // darkColorScheme from Pb; fixes the black-on-dark text bug
fun Modifier.brushedBackground(): Modifier                          // horizontal brushed-black texture, generated once and cached
enum class LedState { Connected, Searching, Disconnected }
data class StatusPresentation(val led: LedState, val label: String, val controlsEnabled: Boolean)
fun HomeStatus.presentation(): StatusPresentation
```
Mapping: `Connected` → (Connected, "BAĞLI", true); `Connecting` → (Searching, "BAĞLANILIYOR…", false); `Reconnecting` → (Searching, "YENİDEN BAĞLANILIYOR…", false); `Starting`, `Scanning`, `NotFound` → (Searching, "ARANIYOR…", false); `GaveUp`, `LinkLost`, `NeedsPermission`, `BluetoothOff`, `PickDevice` → (Disconnected, "BAĞLI DEĞİL", false).

- [ ] **Step 1: Write the failing test** `StatusPresentationTest`: one assertion per `HomeStatus` entry against the mapping above (iterate `HomeStatus.entries` and fail if any has no expectation), and `onlyConnectedEnablesControls`.
- [ ] **Step 2: Run** `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "*StatusPresentationTest"` · Expected: FAIL (compile error).
- [ ] **Step 3: Implement** `Pedalboard.kt` (tokens, theme with `darkColorScheme` mapping Pb colors to primary/background/surface/onSurface, `presentation()`, and `brushedBackground()` that draws a cached `ImageBitmap` of horizontal noise streaks over the dark vertical gradient from the mockup, tiled).
- [ ] **Step 4: Run** same command · Expected: PASS; full suite + `:app:assembleDebug :app:lintDebug` · Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): pedalboard theme, tokens and status presentation"`.

### Task 2: Knob math

**Files:**
- Create: `app/src/main/java/dev/blaze/controller/ui/components/KnobMath.kt`
- Test: `app/src/test/java/dev/blaze/controller/ui/components/KnobMathTest.kt`

**Interfaces — Produces:**
```kotlin
object KnobMath {
    const val FULL_RANGE_DP = 220f
    const val SWEEP_DEGREES = 270f
    fun sweepDegrees(value: Int, range: IntRange): Float                 // 0 at min, 270 at max (linear)
    fun valueAfterDrag(startValue: Int, upwardDragDp: Float, range: IntRange): Int
    fun step(value: Int, delta: Int, range: IntRange): Int               // clamped
}
```
`valueAfterDrag` = `round(startValue + upwardDragDp / FULL_RANGE_DP * (last - first))` clamped to the range.

- [ ] **Step 1: Write failing tests** `KnobMathTest`: `sweepDegrees(0,0..50)==0`, `(50,0..50)==270`, `(25,0..50)==135`, `(15,0..30)==135`; `valueAfterDrag(10, 0f, 0..50)==10`; `(0, 220f, 0..50)==50`; `(50, -220f, 0..50)==0`; `(20, 110f, 0..50)==45`... (use exact numbers: half range = +25 → 45); clamping `(40, 500f, 0..50)==50` and `(5, -500f, 0..50)==0`; `(10, 2f, 0..20)==10` (tiny drag rounds away) and `(10, 6f, 0..20)==11`; `step(50,1,0..50)==50`, `step(0,-1,0..50)==0`, `step(10,-1,0..50)==9`.
- [ ] **Step 2: Run** `--tests "*KnobMathTest"` · Expected: FAIL (compile error).
- [ ] **Step 3: Implement `KnobMath`.**
- [ ] **Step 4: Run** same command · Expected: PASS; full suite · Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): knob math"`.

### Task 3: Power memory, EQ reset and power state in the view model

**Files:**
- Create: `app/src/main/java/dev/blaze/controller/ui/PowerMemory.kt`
- Modify: `app/src/main/java/dev/blaze/controller/ui/HomeViewModel.kt` (add `toggleDelay()`, `toggleReverb()`, `resetEq()`; `HomeUiState` gets `val delayOn: Boolean`, `val reverbOn: Boolean` derived from `amp.masterDelay > 0` / `amp.masterReverb > 0`)
- Test: `app/src/test/java/dev/blaze/controller/ui/PowerMemoryTest.kt`, `app/src/test/java/dev/blaze/controller/ui/PowerAndEqViewModelTest.kt`

**Interfaces — Produces:**
```kotlin
class PowerMemory(private val defaultLevel: Int = 20) {
    fun observe(level: Int)                  // remember the last non-zero level
    fun levelAfterToggle(current: Int): Int  // current > 0 -> 0 ; current == 0 -> remembered or default
}
```
`HomeViewModel.toggleDelay()` sends `SetMasterDelay(memory.levelAfterToggle(state.masterDelay))`; same for reverb with `SetMasterReverb`; `resetEq()` sends `SetEqBand(i, 10)` for i in 0..6 in order. The view model observes `repo.state` to feed both memories (amp-side changes included).

- [ ] **Step 1: Write failing tests.** `PowerMemoryTest`: `neverNonZeroTogglesToDefault20`; `remembersLastNonZeroAndRestoresIt` (observe 30 → toggle(30)=0 → observe 0 → toggle(0)=30); `amp-side change updates memory` (observe 12 then 0 → toggle(0)=12); `observingZeroDoesNotForget`; `rapid double toggle returns to the same level` (30 → 0 → 30). `PowerAndEqViewModelTest` (fake transport, connected VM as in `SoundViewModelTest`): `toggleDelayOffWritesZeroThenRestoresPrevious` (set master delay 30; toggle → last frame `SetMasterDelay(0)`, `ui.delayOn == false`; toggle → `SetMasterDelay(30)`); `ampSideZeroThenToggleRestoresTheLastNonZero` (emit live frame master delay 12 then 0; toggle → 12); `toggleReverbMirrorsDelay`; `resetEqWritesSevenFlatBandsInOrder` (7 frames `SetEqBand(0..6, 10)` after the sync frame, state eq all 10).
- [ ] **Step 2: Run** `--tests "*PowerMemoryTest" --tests "*PowerAndEqViewModelTest"` · Expected: FAIL (compile error).
- [ ] **Step 3: Implement** `PowerMemory`, the three view-model functions and the two derived flags.
- [ ] **Step 4: Run** same command · Expected: PASS; full suite · Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): power toggles and EQ reset in the view model"`.

### Task 4: Knob and value popup

**Files:**
- Create: `app/src/main/java/dev/blaze/controller/ui/components/Knob.kt`, `.../components/ValuePopup.kt`
- Test: `app/src/androidTest/java/dev/blaze/controller/ui/components/KnobTest.kt`

**Interfaces — Consumes:** `KnobMath`, `SliderModel`, `SliderThrottle`, `Pb`. **Produces:**
```kotlin
enum class KnobSize(val dp: Int) { Small(46), Medium(62), Large(84) }
@Composable fun Knob(value: Int, range: IntRange, label: String, onValueChange: (Int) -> Unit,
                     size: KnobSize = KnobSize.Small, enabled: Boolean = true, modifier: Modifier = Modifier)
@Composable fun ValuePopup(title: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit, onDismiss: () -> Unit)
```
Knob: drawn per mockup (red arc whose sweep = `KnobMath.sweepDegrees`, soft glow, dark cap inset 16%, red pointer line, label below); vertical drag changes the value through `SliderModel` (continuous value from `KnobMath.valueAfterDrag` logic) so echoes never snap it back; double tap opens `ValuePopup` (Dialog: title, big red value, slider, −/+ (±1 via `KnobMath.step`), TAMAM; values apply live, any dismissal keeps the value). Semantics: `contentDescription = label`, `stateDescription = value`, `progressBarRangeInfo`, and `setProgress` so TalkBack can adjust; hit area ≥ 48 dp even for `Small`. Disabled → dimmed, ignores gestures.

- [ ] **Step 1: Write failing device tests** `KnobTest` (compose rule, state-holding wrapper): `draggingUpIncreasesAndReportsValue` (swipe up 110 dp on a 0..50 knob at 10 → reported values increase and the last reported is ≥ 30); `draggingDownDecreases`; `doubleTapOpensPopupWithTitleAndValue` (node "GAIN" title and "15" visible); `popupPlusMinusStepByOne` (click "+" → 16, click "−" twice → 14); `tamamClosesAndKeepsTheValue`; `semanticsExposeRangeAndSetProgress` (perform `SetProgress(20f)` → reported 20); `disabledKnobIgnoresDrag` (no report).
- [ ] **Step 2: Run** `:app:assembleDebugAndroidTest` · Expected: compile error (Knob missing).
- [ ] **Step 3: Implement** `Knob` and `ValuePopup`.
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` · Expected: BUILD SUCCESSFUL; `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.blaze.controller.ui.components.KnobTest` · Expected: 7 tests pass on the phone. Take a screenshot of a test harness or defer visual check to Task 7.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): drawn knob with drag and double-tap value popup"`.

### Task 5: Pedal switch, power button, LED, tabs, chips, fader

**Files:**
- Create: `ui/components/PedalSwitch.kt`, `PowerButton.kt`, `StatusLed.kt`, `TabBar.kt`, `ChipRow.kt`, `EqFader.kt` (all under `app/src/main/java/dev/blaze/controller/ui/components/`)
- Test: `app/src/androidTest/java/dev/blaze/controller/ui/components/ControlsTest.kt`

**Interfaces — Produces:**
```kotlin
@Composable fun PedalSwitch(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier)
@Composable fun PowerButton(on: Boolean, contentDescription: String, onToggle: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier)
@Composable fun StatusLed(state: LedState, label: String, modifier: Modifier = Modifier)
@Composable fun TabBar(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier)
@Composable fun <T> ChipRow(items: List<Pair<T, String>>, selected: T?, onSelect: (T) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier)
@Composable fun EqFader(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier)
```
Drawing per mockup 3/4/5: pedal = bevelled hex nut + dark well + brushed chrome cap, red ring and red glowing label when `selected`; power button = steel bezel, dome, vector power symbol (arc + line path, **no font glyph**), red LED ring + glow when `on`; LED = glowing dot (green/amber/red) + label; tab = red text + glowing underline when selected; fader = vertical red track with thumb, value text above, label below, drag via `SliderModel`. Semantics: pedal `Role.RadioButton` + `selected`; power `Role.Switch` + `toggleableState`; tab `Role.Tab` + `selected`; fader `setProgress` + range info.

- [ ] **Step 1: Write failing device tests** `ControlsTest`: `pedalClickCallsBackAndSelectedStateIsExposed`; `disabledPedalDoesNotCall`; `powerButtonTogglesAndExposesState` (toggleable state follows `on`); `ledShowsItsLabel` for the three states; `tabSelectionCallsBack` (`AMP|EQ|AKORT`, selected tab exposes selected); `chipRowSelectsAndExposesSelected`; `faderDragUpIncreasesValue` and `faderSetProgressReportsValue`.
- [ ] **Step 2: Run** `:app:assembleDebugAndroidTest` · Expected: compile error.
- [ ] **Step 3: Implement** the six components.
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` · Expected: BUILD SUCCESSFUL; `connectedDebugAndroidTest` for `ControlsTest` · Expected: 8 tests pass.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): pedal, power button, LED, tabs, chips and fader components"`.

### Task 6: Tabs, connection card and main scaffold

**Files:**
- Create: `ui/AmpActions.kt`, `ui/screens/MainScreen.kt`, `ui/screens/AmpTab.kt`, `ui/screens/EqTab.kt`, `ui/screens/TunerTab.kt`, `ui/screens/ConnectionCard.kt`
- Modify: `HomeViewModel.kt` (implements `AmpActions`)
- Test: `app/src/androidTest/java/dev/blaze/controller/ui/screens/MainScreenTest.kt`

**Interfaces — Consumes:** Tasks 1–5, `HomeUiState`, `TunerUi`. **Produces:**
```kotlin
interface AmpActions {   // implemented by HomeViewModel; faked in tests
    fun onTone(type: AmpType); fun setGain(v: Int); fun setVolume(v: Int); fun setEffect(e: GuitarEffect)
    fun setEq(index: Int, level: Int); fun resetEq()
    fun setMasterDelay(v: Int); fun setDelayLevel(v: Int); fun setDelayFeedback(v: Int); fun setDelayTime(v: Int); fun toggleDelay()
    fun setMasterReverb(v: Int); fun setReverbType(t: ReverbType); fun setReverbLevel(v: Int); fun setReverbTone(v: Int); fun setReverbTime(v: Int); fun toggleReverb()
    fun retry(); fun onDevicePicked(d: FoundDevice)
}
data class SystemActions(val onGrantPermission: () -> Unit, val onOpenBluetoothSettings: () -> Unit, val onOpenAppSettings: () -> Unit, val onRequestMic: () -> Unit)
@Composable fun MainScreen(ui: HomeUiState, tuner: TunerUi, micGranted: Boolean, micNeedsSettings: Boolean,
                           tab: Int, onTab: (Int) -> Unit, actions: AmpActions, system: SystemActions)
```
Layout per mockup 5: `brushedBackground`, header (title, `StatusLed` from `ui.status.presentation()`, ANA SES `Knob` Medium on `setVolume`), `TabBar`, content (weight 1; vertical scroll only as fallback when it does not fit), sticky pedal row (5 `PedalSwitch` bound to `ui.amp.ampType`). `AmpTab`: GAIN knob (Large) + effect `ChipRow`, DELAY panel (`PowerButton` bound to `ui.delayOn` + 4 knobs: ANA=master delay 0–50, SEVİYE/FEEDBACK/SÜRE 0–20), REVERB panel (power + type `ChipRow` + ANA/SEVİYE/TON/SÜRE). Knob panels are dimmed when the power is off but stay adjustable. `EqTab`: 7 `EqFader`s + SIFIRLA button (`resetEq`). `TunerTab`: the fixed-size active/inactive tuner panel ported from `TunerScreen` (same texts/behavior incl. mic permission flow). `ConnectionCard` replaces/overlays the content for non-connected statuses with the existing texts and buttons (grant permission / open Bluetooth settings / retry / device picker). When `!presentation.controlsEnabled`: AMP/EQ content and pedals are dimmed and disabled; tab bar and AKORT stay usable.

- [ ] **Step 1: Write failing device tests** `MainScreenTest` with a recording fake `AmpActions`: `pedalTapSendsTone`; `tabsSwitchContent` (EQ shows "SIFIRLA", AKORT shows the tuner prompt); `eqResetCallsResetEq`; `delayPowerCallsToggleAndReflectsState`; `reverbPowerCallsToggle`; `connectedStatusShowsBagliLed`; `notConnectedDisablesControlsButKeepsTunerTab` (status `NotFound`: pedal tap sends nothing, ANA SES drag sends nothing, tab "AKORT" still selectable and shows its content, card shows "Tekrar ara"); `retryCardCallsRetry`; `devicePickerListsDevices`; `micPermissionButtonsOnTunerTab` (grant / settings variants).
- [ ] **Step 2: Run** `:app:assembleDebugAndroidTest` · Expected: compile error.
- [ ] **Step 3: Implement** `AmpActions` (and make `HomeViewModel` implement it without changing existing behavior), the screens and `MainScreen`.
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:assembleDebug :app:lintDebug` · Expected: BUILD SUCCESSFUL; `connectedDebugAndroidTest` for `MainScreenTest` · Expected: 10 tests pass.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): main scaffold with AMP, EQ and AKORT tabs"`.

### Task 7: Wire into the app, remove the old screens, large-font check

**Files:**
- Modify: `app/src/main/java/dev/blaze/controller/MainActivity.kt`
- Delete: `ui/HomeScreen.kt`, `ui/SoundScreen.kt`, `ui/TunerScreen.kt`, `ui/AmpSlider.kt` (if unused), and the instrumented tests of the removed screens (`HomeScreenTest`, `TunerScreenTest`) after moving their still-valid cases into `MainScreenTest`
- Test: `MainScreenTest` (extra cases), existing unit tests

**Behavior:** `MainActivity` shows `PedalboardTheme { MainScreen(...) }`; selected tab in `rememberSaveable` (default AMP, survives rotation); `micGranted`/`micNeedsSettings` logic stays as today; the tuner runs `LaunchedEffect(tab == TUNER && micGranted)` + `repeatOnLifecycle(STARTED) { tuner.run() }` (mic only while AKORT is selected and the app is started); the old home "Akort" button is gone; Bluetooth permission/settings actions go through `SystemActions`.

- [ ] **Step 1: Write failing device tests** (in `MainScreenTest`): `largeFontScaleKeepsAllControlsReachable` (render with `fontScale = 1.3` in a 393×780 dp frame: pedal row, tab bar, GAIN knob, both power buttons visible or reachable by scrolling, no node `assertIsNotDisplayed` for pedals/tabs); `rotationKeepsSelectedTab` is covered in Step 4 on the phone.
- [ ] **Step 2: Run** `:app:assembleDebugAndroidTest` · Expected: FAIL/compile errors until wiring is done.
- [ ] **Step 3: Implement** the wiring and delete the old screens; fix imports; keep `HomeViewModel` API compatible.
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` · Expected: BUILD SUCCESSFUL; then on the phone: `installDebug`, `connectedDebugAndroidTest` (all) · Expected: all device tests pass; `installDebug` again, launch, rotate with `adb shell settings put system user_rotation 1` / `0` (accelerometer off) and confirm the tab is kept; check `adb shell dumpsys audio` shows no record client for the app when AKORT is not selected.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "feat(ui): switch the app to the pedalboard UI and remove the old screens"`.

### Task 8: Visual acceptance and documentation

**Files:** Modify `README.md`, `docs/backlog.md` (move UI redesign to done), fixes found during acceptance.

- [ ] **Step 1:** On the phone (app installed, amp on): take screenshots of AMP, EQ, AKORT (with and without sound), the Gain popup, Delay power off, and the not-connected state (`adb exec-out screencap -p`); compare each with `5-final-all-screens.html` and list differences (arc wrapping the knobs, pedal realism, brushed background, power button, fixed-size tuner panel).
- [ ] **Step 2:** Fix every visible difference that is a defect (alignment, clipping, contrast, tap targets) with a failing device test where the defect is testable; re-screenshot.
- [ ] **Step 3 (user, with the amp and guitar):** pedal taps switch tones and the LED ring follows; knob drag and double-tap popup; power buttons; EQ reset; tuner; status LED states. Collect feedback; apply requested visual adjustments.
- [ ] **Step 4:** Measure smoothness on the test phone (no visible jank while dragging a knob or moving a fader); if needed reduce blur/glow cost.
- [ ] **Step 5: Commit** `git add -A . && git commit -m "docs: UI redesign acceptance, README and backlog"`.
