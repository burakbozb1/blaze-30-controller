# Amp → App Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** The app reads what the amp reports (full state dump on connect, live knob changes) and keeps `AmpState` equal to the amp.

**Architecture:** The transport exposes the amp's notifications as raw frames (`incoming`). `ProtocolCodec.decode` turns a frame into a list of the existing `AmpCommand`s (state setters), so the existing `AmpState.apply` reducer (and its clamping) is reused. `AmpRepository` applies them to its state without ever writing back, and forgets its own re-send memory for any control the amp changed.

**Tech Stack:** Kotlin, coroutines/Flow, JUnit4 + kotlinx-coroutines-test (existing project).

**Spec:** `docs/superpowers/specs/2026-11-15-amp-to-app-sync-design.md` (protocol facts: `docs/protocol.md`)

**Build note:** run Gradle through the `X:` drive: `subst X: "<project dir>"`, then in `X:\` use `./gradlew.bat --no-daemon ...` (non-ASCII project path).

## Global Constraints

- Manifest permissions stay exactly `BLUETOOTH_SCAN` (neverForLocation) and `BLUETOOTH_CONNECT`.
- Notifications are 16 bytes: `FE 0F … FF`; anything else is ignored. Reading is read-only: **incoming data never causes a write**.
- Values read from the amp pass through `AmpState.apply` (same clamping as values the app sends).
- The factory-reset frame `FE0D01010000000000FF` stays unrepresentable.
- Ignore frames: heartbeat `FE 0F 41 …`, aux volume `04 01 00`, dump frames `BD`, `BE`, `BF`, and every unknown type.
- Real captured frames (2026-11-15) are the test fixtures; the raw log stays out of git.

## Review Focus

1. A knob sweep floods notifications (dozens per second) → state ends on the last value, in order, UI not starved (Task 3 test).
2. Short, long, wrong-header, wrong-trailer or unknown-type frames → no crash, no state change (Tasks 1, 3).
3. The user set a control in the app, then changed it on the amp → a later reconnect must not push the app's old value back over the amp's (Task 3).
4. Incoming frames must never trigger a write (echo loop) (Task 3).
5. Foreground refresh while disconnected → no-op, no crash (Task 4).

---

### Task 1: Frame decoder

**Files:**
- Modify: `app/src/main/java/dev/blaze/controller/protocol/ProtocolCodec.kt`
- Test: `app/src/test/java/dev/blaze/controller/protocol/ProtocolCodecDecodeTest.kt`

**Interfaces:**
- Consumes: `AmpCommand` (`SetAmpType`, `SetGain`, `SetVolume`, `SetEqBand`, `SetEffect`, `SetMasterDelay`, `SetMasterReverb`, `SetReverbType/Level/Tone/Time`, `SetDelayLevel/Feedback/Time`), enums `AmpType`, `GuitarEffect`, `ReverbType` (each has `code`).
- Produces: `ProtocolCodec.decode(frame: ByteArray): List<AmpCommand>` — never throws; empty list for anything not understood. Never returns `AmpCommand.Sync`.

Frame map (index = byte position in the 16-byte frame):
- Dump `BB` (`[2]=0xBB`): volume `[3]`, amp type `[5]`, gain `[7]`, EQ bands 0..6 = `[8]..[14]`.
- Dump `BC`: effect `[3]`, master delay `[13]`, master reverb `[14]`.
- Dump `C0`: reverb type `[3]`, reverb level `[4]`, tone `[5]`, time `[6]`, delay level `[7]`, feedback `[8]`, delay time `[9]`.
- Live (`[2]`=group, `[3]`=item, `[4]`=sub, `[5]`=value): `02 01 00` volume, `02 03 00` amp type, `02 03 02` gain, `02 02 00` master delay, `02 02 01` master reverb, `02 05 00` effect, `02 04 0N` (N=1..7) EQ band N-1.
- An enum field whose code is unknown is skipped (the rest of the frame still applies).

- [ ] **Step 1: Write the failing tests** in `ProtocolCodecDecodeTest`, helper `fun frame(hex: String): ByteArray`. Cases (real captured frames):
  - `decodesTheBBDump`: `FE0FBB0C0004190D0A0A0A0A0A0A0AFF` → contains `SetVolume(12)`, `SetAmpType(METAL)`, `SetGain(13)` and `SetEqBand(i, 10)` for i in 0..6.
  - `decodesTheBCDump`: `FE0FBC00141C1412180B1123140000FF` → `SetEffect(BYPASS)`, `SetMasterDelay(0)`, `SetMasterReverb(0)`.
  - `decodesTheC0Dump`: `FE0FC0000A080A0B0E100C640D000DFF` → `SetReverbType(PLATE)`, `SetReverbLevel(10)`, `SetReverbTone(8)`, `SetReverbTime(10)`, `SetDelayLevel(11)`, `SetDelayFeedback(14)`, `SetDelayTime(16)`.
  - `decodesLiveKnobFrames`: `FE0F0201000C000000000000000000FF`→`[SetVolume(12)]`; `FE0F0203020E…`→`[SetGain(14)]`; `FE0F02030004…`→`[SetAmpType(METAL)]`; `FE0F02020032…`→`[SetMasterDelay(50)]`; `FE0F02020103…`→`[SetMasterReverb(3)]`; `FE0F02050002…`→`[SetEffect(CHORUS)]`; `FE0F0204030F…`→`[SetEqBand(2, 15)]`.
  - `ignoresHeartbeatAuxVolumeAndOtherDumps`: `FE0F41000002000000000000000000FF`, `FE0F0401001900000000000000000000`-style aux-volume frame (`FE0F04010019…FF`), `FE0FBD…`, `FE0FBE…`, `FE0FBF…` → all `emptyList()`.
  - `malformedFramesYieldNothing`: 15 bytes, 17 bytes, empty, header `FE0D…` (a write frame), missing trailer (`[15] != 0xFF`) → `emptyList()`, no exception.
  - `unknownEnumCodeIsSkippedButTheRestApplies`: BB frame with `[5]=0x09` → no `SetAmpType`, still has `SetVolume`/`SetGain`.
  - `decodeNeverProducesSyncOrFactoryReset`: every returned command across all fixtures `!= AmpCommand.Sync`.
- [ ] **Step 2: Run to verify FAIL** — `./gradlew.bat --no-daemon :app:testDebugUnitTest --tests "*ProtocolCodecDecodeTest"` · Expected: compile error / FAIL (`decode` not defined).
- [ ] **Step 3: Implement `fun decode(frame: ByteArray): List<AmpCommand>`** in `ProtocolCodec`: validate length 16 / header `FE 0F` / trailer `FF`, then branch on `[2]` for dumps and on `[2],[3],[4]` for live frames per the map above; helper maps enum codes with `entries.firstOrNull { it.code == v }`.
- [ ] **Step 4: Run to verify PASS** — same command · Expected: all `ProtocolCodecDecodeTest` pass; then run the whole suite (`:app:testDebugUnitTest`) · Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git add -A . && git commit -m "feat: decode amp notification frames into state updates"`

### Task 2: Transport exposes incoming frames

**Files:**
- Modify: `app/src/main/java/dev/blaze/controller/ble/AmpTransport.kt`, `.../ble/BleAmpTransport.kt`
- Modify: `app/src/test/java/dev/blaze/controller/ble/FakeAmpTransport.kt`
- Test: `app/src/test/java/dev/blaze/controller/ble/FakeAmpTransportTest.kt`

**Interfaces:**
- Produces: `AmpTransport.incoming: Flow<ByteArray>` (hot, no replay; each element is one notification's bytes, copied). `FakeAmpTransport.emitFromAmp(bytes: ByteArray)` (suspend-free; emits into the same flow).

- [ ] **Step 1: Write the failing test** `incomingFramesReachCollectorsInOrder` in `FakeAmpTransportTest`: collect `t.incoming` into a list inside `backgroundScope`, call `emitFromAmp(byteArrayOf(1))`, `emitFromAmp(byteArrayOf(2))`, `runCurrent()`; assert two elements `[1]`,`[2]` in order.
- [ ] **Step 2: Run to verify FAIL** — `--tests "*FakeAmpTransportTest"` · Expected: compile error (`incoming`/`emitFromAmp` missing).
- [ ] **Step 3: Implement.** Interface: add `val incoming: Flow<ByteArray>`. Fake: `MutableSharedFlow<ByteArray>(extraBufferCapacity = 256)`, `emitFromAmp` uses `tryEmit(bytes.copyOf())`. `BleAmpTransport`: private `MutableSharedFlow<ByteArray>(extraBufferCapacity = 256, onBufferOverflow = BufferOverflow.DROP_OLDEST)`; override both `onCharacteristicChanged` overloads (API 33 `(g, c, value)` and deprecated `(g, c)`), ignore when `g !== gatt` or `c.uuid != NOTIFY_UUID`, `tryEmit(value.copyOf())`. No logging of frame contents.
- [ ] **Step 4: Run to verify PASS** — whole suite plus `:app:assembleDebug :app:lintDebug` · Expected: BUILD SUCCESSFUL, no new lint errors.
- [ ] **Step 5: Commit** — `git commit -am "feat: transport exposes notifications as incoming frames"`

### Task 3: Repository applies what the amp reports

**Files:**
- Modify: `app/src/main/java/dev/blaze/controller/amp/AmpRepository.kt`
- Test: `app/src/test/java/dev/blaze/controller/amp/AmpRepositoryTest.kt`

**Interfaces:**
- Consumes: `AmpTransport.incoming`, `ProtocolCodec.decode`, `AmpState.apply`, existing private `slot(cmd)`/`lastSent`.
- Produces: no new public API; `state` now also changes from incoming frames.

- [ ] **Step 1: Write failing tests** (fixtures as in Task 1; connect first with `FakeAmpTransport`, `syncSettleMs = 0`):
  - `connectDumpFillsTheState`: emit the BB, BC, C0 frames → `state` has volume 12, amp type METAL, gain 13, eq all 10, effect BYPASS, reverb PLATE/10/8/10, delay 11/14/16.
  - `liveKnobFramesUpdateTheStateInOrder`: emit 200 `FE0F020100<v>…` frames for v = 0..50 repeating; `state.volume` equals the last v (Review Focus 1).
  - `incomingFramesNeverCauseWrites`: after emitting every fixture, `fake.written` is unchanged from before (only the connect-time Sync) (Review Focus 4).
  - `amp-side change beats an older app value on reconnect`: `send(SetAmpType(METAL))`, then emit live frame amp type CLEAN, `dropConnection()`, advance time, reconnected → `userFrames()` after the reconnect contains no `SetAmpType` frame, and `state.ampType == CLEAN` (Review Focus 3). A control the user did not touch by the amp is likewise not re-sent.
  - `garbageIsIgnored`: emit 15-byte, empty, wrong-header and random frames → `state` unchanged, repository still processes a following valid frame (Review Focus 2).
- [ ] **Step 2: Run to verify FAIL** — `--tests "*AmpRepositoryTest"` · Expected: new tests FAIL.
- [ ] **Step 3: Implement** in `AmpRepository.init`: `scope.launch { transport.incoming.collect { frame -> ProtocolCodec.decode(frame).forEach { cmd -> _state.value = _state.value.apply(cmd); lastSent.remove(slot(cmd)) } } }`. Never call `transport.write` from this path.
- [ ] **Step 4: Run to verify PASS** — whole suite · Expected: all pass (previous 54 + new).
- [ ] **Step 5: Commit** — `git commit -am "feat: repository mirrors the amp's reported state"`

### Task 4: Refresh on return to foreground

**Files:**
- Modify: `AmpRepository.kt` (add `fun refresh()`), `ui/HomeViewModel.kt` (add `fun onForeground()`), `MainActivity.kt` (`onStart` calls it)
- Test: `AmpRepositoryTest.kt`, `ui/HomeViewModelTest.kt`

**Interfaces:**
- Produces: `AmpRepository.refresh()` — if connected, queues `AmpCommand.Sync` (amp answers with the full dump); no-op otherwise. `HomeViewModel.onForeground()` → `repo.refresh()`.

- [ ] **Step 1: Write failing tests:** repo: `refreshWhenConnectedSendsAnotherSync` (written sync count 1 → 2), `refreshWhenDisconnectedDoesNothingAndDoesNotThrow` (Review Focus 5). VM: `onForegroundRequestsAFreshDump` (connected via fake; after `onForeground()` and `runCurrent()` the fake saw one more sync frame).
- [ ] **Step 2: Run to verify FAIL** · Expected: compile error (`refresh`, `onForeground` missing).
- [ ] **Step 3: Implement** the two methods; `MainActivity.onStart` calls `vm.onForeground()` after the existing permission/Bluetooth handling.
- [ ] **Step 4: Run to verify PASS** — whole suite + `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug` · Expected: BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** — `git commit -am "feat: refresh amp state when the app returns to the foreground"`

### Task 5: On-device acceptance and docs

**Files:**
- Modify: `docs/protocol.md` (notify decode table, "verified" notes), nothing else unless a defect is found.

- [ ] **Step 1:** `./gradlew.bat --no-daemon :app:installDebug` with `ANDROID_SERIAL=<phone adb serial>`; launch the app; phone and amp connected.
- [ ] **Step 2 (user, with the amp):** turn the master volume pot → the app's **Ses** slider follows live; turn Gain → **Gain** follows; turn Amp Type → the selected tone button follows; change GUI ECHO on the amp → the Delay "Ana seviye" slider shows it; leave the app and return → values match the amp. Check the app's Ses slider still sets the amp.
- [ ] **Step 3:** Record in `docs/protocol.md`: notify frame map (dumps `BB`, `BC`, `C0`, live frames), ignored frames, and results of Step 2 per control (verified / not observed).
- [ ] **Step 4: Run** `:app:testDebugUnitTest :app:connectedDebugAndroidTest` (4 device UI tests) · Expected: all pass.
- [ ] **Step 5: Commit** — `git commit -am "docs: amp-to-app sync verified on hardware"`
