# Blaze 30 Controller Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Minimum-permission native Android app that controls the Kozmos Blaze 30 over BLE, centered on one-tap amp-type switching.

**Architecture:** Phase 0 reverse-reads the vendor APK (static only) into `docs/protocol.md`. Phase 1 builds layers `protocol/` (pure Kotlin codec) → `ble/` (transport interface + real/fake impls) → `amp/` (AmpState + repository) → `ui/` (Compose). AmpState is the single source of truth so future presets are serialized AmpStates.

**Tech Stack:** Kotlin, Jetpack Compose, Kotlin coroutines/Flow, JUnit4 + kotlinx-coroutines-test, Android BLE APIs (no third-party BLE lib), jadx (analysis only).

**Spec:** `docs/superpowers/specs/2026-11-15-blaze30-controller-design.md`

**Honesty note:** The BLE protocol is unknown until Task 2. Tasks 5-8 name the *shape* of the code; every byte value, UUID and value range comes from `docs/protocol.md`. After Task 3, re-read Tasks 5-8 and adjust command names to what the protocol actually supports (e.g. if EQ has no separate command, drop it from Phase 1 and note it).

## Global Constraints

- Target device: a recent Android phone. `minSdk 31`, `targetSdk` = latest stable.
- Manifest permissions: ONLY `BLUETOOTH_SCAN` (with `usesPermissionFlags="neverForLocation"`) and `BLUETOOTH_CONNECT`. No INTERNET, no location, no contacts, no SMS, no storage, no install-packages.
- No analytics, ads, push or third-party SDKs.
- Vendor APK is untrusted: extract/decompile only in `analysis/` (never run it, never install it); copy no code, strings, images or branding into `app/`. Only protocol facts (UUIDs, byte layouts) go into `docs/protocol.md`.
- Never send an unverified command to the amp. Every command in `protocol/` must be marked verified-on-hardware in `docs/protocol.md`.
- Phase 1 scope only: amp type (Clean, Drive, Acoustic, Distortion, Metal), gain, volume, 7-band guitar EQ, effect (Bypass, Flanger, Chorus, Phaser, Wah, Vibrato), reverb (Plate/Room/Spring + level/tone/time), delay (level/feedback/time). No AUX/USB, presets, simulation.
- Local git repo; commit after each task.

## Review Focus

1. Amp powered off / out of range at launch → app shows "Amp not found" with retry, no crash.
2. Connection drops mid-use → UI shows disconnected, auto-reconnects, last state re-sent or re-read as protocol allows.
3. User taps tones rapidly (Metal→Clean→Drive in <1 s) → commands are sent in tap order, final amp state equals last tap.
4. Bluetooth off or permission denied → clear prompt/state, no crash, recovers when granted.
5. Slider values out of the protocol range (EQ, gain) → clamped by the codec, never sent raw.
6. Several nearby BLE devices / a second Blaze → only devices matching the Blaze name/service filter listed; user picks one.

---

## Phase 0 — Protocol discovery (spike; output is documentation, not product code)

### Task 1: Tooling and APK extraction

**Files:**
- Create: `analysis/apk/` (extracted APK), `analysis/jadx-out/` (decompiled Java), `.gitignore` (ignores `analysis/`, `tools/`)
- Tools: `tools/jadx/` (downloaded release of jadx from the official skylot/jadx GitHub releases)

- [ ] **Step 1:** `git init` in the project root; add `.gitignore` containing `analysis/`, `tools/`, `build/`, `.gradle/`, `local.properties`.
- [ ] **Step 2:** Download the latest official jadx release zip into `tools/` and unzip it; verify with `tools/jadx/bin/jadx --version` (needs the installed Java; Expected: prints a version).
- [ ] **Step 3:** Decompile: `tools/jadx/bin/jadx -d analysis/jadx-out Kaynak/kozmos.apk`. Expected: `analysis/jadx-out/sources/com/kozmos/` exists (decompile warnings are fine).
- [ ] **Step 4:** Commit `.gitignore`.

### Task 2: Identify the Blaze model and write the protocol doc

**Files:**
- Create: `docs/protocol.md`
- Read: `analysis/jadx-out/sources/com/kozmos/akia_all/BluetoothLeService.java`, `ChoiceActivity.java`, and the `com/kozmos/model/*` packages

- [ ] **Step 1:** Read `ChoiceActivity` / device-name matching to find which model package handles a device advertising as Blaze (search for "blaze", "BLAZE", "30" in the jadx output). Record the model package in `docs/protocol.md`.
- [ ] **Step 2:** From `BluetoothLeService` and that model's `*GuitarActivity` / `*EffectActivity`, extract: advertised device name filter, service UUID, write characteristic UUID, notify characteristic UUID (if any), MTU/write type.
- [ ] **Step 3:** For each Phase 1 control (amp type ×5, gain, volume, EQ ×7, effect ×6, reverb type/level/tone/time, delay level/feedback/time) record: frame layout (header, command id, value bytes, checksum/trailer), value range, and the source file/line it came from.
- [ ] **Step 4:** Document anything the app sends on connect (handshake, time sync, state request) and any notify frames that report amp state.
- [ ] **Step 5:** Mark each command `status: unverified` in the doc. Commit `docs/protocol.md`.

### Task 3: Verify on real hardware (user-assisted)

**Files:**
- Modify: `docs/protocol.md` (status per command)

- [ ] **Step 1:** User installs the Nordic `nRF Connect` app from the Play Store on the test phone, powers the Blaze, and connects to it. Confirm the advertised name and service/characteristic UUIDs match the doc.
- [ ] **Step 2:** Write the amp-type command for Clean, then Metal, via nRF Connect. Expected: amp audibly/OLED switches. Mark `verified`.
- [ ] **Step 3:** Repeat for the remaining Phase 1 commands, one at a time, lowest-risk first (amp type → volume → gain → effect → reverb/delay → EQ). Record anything unexpected (needs handshake, ignores writes, etc.). Commit.
- [ ] **Step 4:** Gate: if amp type switching cannot be verified, stop and revisit approach B (BLE sniffing) with the user before Phase 1.

---

## Phase 1 — Core app

### Task 4: Android project scaffold

**Files:**
- Create: Gradle project in `app/` (package `dev.blaze.controller` — builder may change), `app/src/main/AndroidManifest.xml`, `MainActivity.kt`

**Interfaces:** Produces: buildable app that shows a Compose "Blaze" placeholder.

- [ ] **Step 1:** Create the Gradle Kotlin-DSL project (Compose, JUnit4, coroutines-test) with `minSdk 31`.
- [ ] **Step 2:** Manifest declares only the two Bluetooth permissions from Global Constraints and `<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>`.
- [ ] **Step 3:** Run `./gradlew assembleDebug` and `./gradlew lint`. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4:** Add a unit test `ManifestPermissionsTest` that parses the merged manifest and asserts the permission set equals exactly `{BLUETOOTH_SCAN, BLUETOOTH_CONNECT}` (guards against SDK-injected permissions); run it, then commit.

### Task 5: Protocol codec

**Files:**
- Create: `app/src/main/java/.../protocol/AmpCommand.kt`, `ProtocolCodec.kt`
- Test: `app/src/test/java/.../protocol/ProtocolCodecTest.kt`

**Interfaces:** Produces:
- `enum class AmpType { CLEAN, DRIVE, ACOUSTIC, DISTORTION, METAL }`, `enum class GuitarEffect { BYPASS, FLANGER, CHORUS, PHASER, WAH, VIBRATO }`, `enum class ReverbType { PLATE, ROOM, SPRING }`
- `sealed interface AmpCommand` with `SetAmpType(AmpType)`, `SetGain(Int)`, `SetVolume(Int)`, `SetEqBand(index: Int, level: Int)`, `SetEffect(GuitarEffect)`, `SetReverbType(ReverbType)`, `SetReverbLevel/Tone/Time(Int)`, `SetDelayLevel/Feedback/Time(Int)`
- `object ProtocolCodec { fun encode(cmd: AmpCommand): ByteArray; fun decodeState(frame: ByteArray): AmpStateUpdate? }` (`decodeState` only if Task 2 found notify frames)

- [ ] **Step 1:** Write tests with the exact bytes from `docs/protocol.md`: `encode(SetAmpType(METAL))` equals the documented frame; one test per command family; `encode(SetGain(999))` equals the frame for the documented max (clamping); `encode(SetEqBand(7, 0))` throws `IllegalArgumentException` (valid indexes 0..6).
- [ ] **Step 2:** Run `./gradlew test --tests "*ProtocolCodecTest"`. Expected: FAIL (not defined).
- [ ] **Step 3:** Implement `ProtocolCodec` including checksum if the doc specifies one. Value ranges live as constants next to the frame builders, copied from the doc.
- [ ] **Step 4:** Run tests. Expected: PASS. Commit.

### Task 6: AmpState and reducer

**Files:**
- Create: `app/src/main/java/.../amp/AmpState.kt`
- Test: `app/src/test/java/.../amp/AmpStateTest.kt`

**Interfaces:** Consumes: `AmpCommand` (Task 5). Produces: `data class AmpState(ampType, gain, volume, eq: List<Int> /*7*/, effect, reverbType, reverbLevel, reverbTone, reverbTime, delayLevel, delayFeedback, delayTime)` with `AmpState.DEFAULT` and `fun AmpState.apply(cmd: AmpCommand): AmpState`.

- [ ] **Step 1:** Tests: `DEFAULT.apply(SetAmpType(METAL)).ampType == METAL`; `apply(SetEqBand(3, 5)).eq[3] == 5` and other bands unchanged; applying a sequence of commands in order yields the last value.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement as a pure function. **Step 4:** Run → PASS. Commit.

### Task 7: BLE transport

**Files:**
- Create: `ble/AmpTransport.kt` (interface), `ble/FakeAmpTransport.kt`, `ble/BleAmpTransport.kt`
- Test: `app/src/test/java/.../ble/FakeAmpTransportTest.kt`

**Interfaces:** Produces:
- `interface AmpTransport { val connection: StateFlow<ConnectionState>; suspend fun scan(): Flow<FoundDevice>; suspend fun connect(device: FoundDevice); suspend fun write(bytes: ByteArray); fun disconnect() }`
- `sealed interface ConnectionState { Disconnected; Connecting; Connected; Failed(reason: String) }`; `data class FoundDevice(name: String, address: String)`
- `FakeAmpTransport` records written frames in `val written: List<ByteArray>` and lets tests drop the connection.

- [ ] **Step 1:** Test fake: write after connect is recorded in order; write while disconnected throws `IllegalStateException`.
- [ ] **Step 2:** Run → FAIL; implement the interface and fake; run → PASS.
- [ ] **Step 3:** Implement `BleAmpTransport` on `BluetoothLeScanner`/`BluetoothGatt` using UUIDs and write type from `docs/protocol.md`; scan filter by the documented name/service (Review Focus 6); serialize GATT writes through a single-consumer queue (BLE allows one op in flight).
- [ ] **Step 4:** Manual check on the test phone with a throwaway debug button: connect, send the verified `SetAmpType`. Expected: amp changes. Commit.

### Task 8: AmpRepository

**Files:**
- Create: `amp/AmpRepository.kt`
- Test: `app/src/test/java/.../amp/AmpRepositoryTest.kt`

**Interfaces:** Consumes: `AmpTransport`, `ProtocolCodec`, `AmpState`. Produces: `class AmpRepository(transport: AmpTransport, scope: CoroutineScope) { val state: StateFlow<AmpState>; val connection: StateFlow<ConnectionState>; fun send(cmd: AmpCommand); fun reconnect() }`.

- [ ] **Step 1:** Tests with `FakeAmpTransport` + `runTest`: (a) `send(SetAmpType(DRIVE))` writes the encoded frame and updates `state`; (b) three rapid sends (Metal, Clean, Drive) are written in that order and final state is DRIVE (Review Focus 3); (c) after the fake drops the connection, `connection` becomes `Disconnected`, the repository reconnects, and re-sends the current state's amp type if the protocol has no state read-back (Review Focus 2); (d) `send` while disconnected updates nothing and does not throw.
- [ ] **Step 2:** Run → FAIL. **Step 3:** Implement with a `Channel<AmpCommand>` consumed sequentially. **Step 4:** Run → PASS. Commit.

### Task 9: Tone-switch home screen

**Files:**
- Create: `ui/HomeScreen.kt`, `ui/HomeViewModel.kt`, `ui/PermissionGate.kt`
- Test: `app/src/androidTest/.../HomeScreenTest.kt` (Compose UI test) and `app/src/test/.../HomeViewModelTest.kt`

**Interfaces:** Consumes: `AmpRepository`. Produces: `HomeScreen` with 5 large amp-type buttons (current one highlighted), connection status chip, device picker when multiple devices found, gain/volume sliders.

- [ ] **Step 1:** ViewModel tests: tapping Metal calls `repository.send(SetAmpType(METAL))`; disconnected state exposes `ui.connected == false`; BT-off/permission-denied states map to distinct UI states (Review Focus 4, 1).
- [ ] **Step 2:** Run → FAIL; implement; run → PASS.
- [ ] **Step 3:** Compose test: five buttons with text Clean/Drive/Acoustic/Distortion/Metal exist; clicking one marks it selected.
- [ ] **Step 4:** `PermissionGate` requests `BLUETOOTH_SCAN`/`CONNECT` at runtime and shows a rationale. Commit.

### Task 10: Sound-shaping screen

**Files:**
- Create: `ui/SoundScreen.kt` (EQ ×7, effect chips, reverb, delay), wire navigation from `HomeScreen`
- Test: `ui/SoundViewModelTest.kt`

**Interfaces:** Consumes: `AmpRepository`.

- [ ] **Step 1:** Tests: moving EQ band 3 sends `SetEqBand(3, v)` with `v` clamped to the doc's range (Review Focus 5); choosing Wah sends `SetEffect(WAH)`; slider drags are rate-limited so ≥1 frame per 50 ms is not flooded but the final value is always sent.
- [ ] **Step 2:** Run → FAIL; implement; run → PASS. Commit.

### Task 11: On-device acceptance

**Files:** Modify: `docs/protocol.md` (final verified statuses), create `README.md` (install via `adb install`/sideload of own debug APK, permissions list).

- [ ] **Step 1:** `./gradlew installDebug` to the test phone. Checklist with the amp: connect from cold start; switch Clean/Drive/Distortion/Metal 20× rapidly, amp ends on the last tapped; power amp off and on, app reconnects; turn phone Bluetooth off/on; rotate/background the app and return; every Phase 1 control audibly/visibly works.
- [ ] **Step 2:** Verify installed permissions: `adb shell dumpsys package <package> | grep permission`. Expected: only the two Bluetooth permissions.
- [ ] **Step 3:** Record failures as issues in `docs/protocol.md`/README; commit.
