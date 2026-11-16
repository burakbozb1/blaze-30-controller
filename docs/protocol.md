# Blaze 30 BLE protocol (from static analysis of the vendor APK)

Status legend: `unverified` = read from vendor code only; `verified` = confirmed on the real amp (Task 3).
Source: vendor APK `cn.com.kozmos` jadx output (kept locally, not in repo). Only protocol facts are recorded here.

## Model identification
- The vendor app picks a model from a hand-selected product button, not from the BLE name. Blaze-30 corresponds to
  **`ChoiceActivity.model = 30`**, handled by package **`cn.com.kozmos.model.a4b`** (shares `newx6s.NetData`).
  Evidence: a4b screens expose exactly the product page's lists — amp types Clean/Acoustic/Driver/Distortion/Metal,
  effects Bypass/Flanger/Chours/Phaser/Wah/Vibrato, reverb Plate/Room/Spring, 7-band EQ, tabs Guitar/Aux/Effect.
  Other packages with the same amp list (`guitar_control`, `newx6s`) differ in effect/reverb lists. Confidence: high, not 100% —
  Task 3 confirms with the real amp.

## Transport
- **Observed on hardware (nRF Connect, 2026-11-15):** advertised/GAP device name `BLAZE-30 APP`; service `0xFFF0` present with
  `0xFFF3` (READ, WRITE, WRITE NO RESPONSE), `0xFFF2` (NOTIFY) and `0xFFF1` (NOTIFY, not used by vendor code). Last `0xFFF2` value seen:
  `FE 0F 41 00 00 03 00 00 00 00 00 00 00 00 00 FF` — **16 bytes**, header `FE 0F` (amp→phone), not `FE 0D`. This confirms 16-byte notify
  chunks (vendor parser drops 16). Write frames stay 10 bytes `FE 0D ...`. Status: observed, commands still unverified.
- Scan: vendor app uses plain `startLeScan`, **no name or service filter** (lists every named device). The advertised name of
  the Blaze is unknown → discover in Task 3 with nRF Connect; our app should filter by service `0000fff0-...` after connect or by name.
- Service `0000fff0-0000-1000-8000-00805f9b34fb`
  - Write characteristic `0000fff3-0000-1000-8000-00805f9b34fb` (vendor sets value + `writeCharacteristic`, default write type)
  - Notify characteristic `0000fff2-0000-1000-8000-00805f9b34fb` (enable notifications / CCCD on connect)
- Vendor sends at most one pending frame every **300 ms** (timer 100 ms delay, 300 ms period), and disables notifications on fff2
  before a write (odd; we should not copy this unless Task 3 shows it is required).
- Reconnect: vendor reconnects on disconnect unless the user disconnected manually.

## Frame layout (10 bytes, write and notify)
```
FE 0D  G  I  S  V  00 00 00 FF
 0  1   2  3  4  5  6  7  8  9
```
`G` group (0x02 = guitar, 0x03 = mic, 0x04 = aux, 0x05 = effect page), `I` item, `S` sub-item, `V` value. No checksum.
Bytes 6-8 are 00 for all single-value commands. Value is one byte (0-255, practical ranges below).

## Connect handshake
**Confirmed on hardware 2026-11-15:** after an amp power cycle the amp ignored every command until the session was opened. Our app
now enables notifications on `0xFFF2` and sends the sync frame on every new link (first, before anything else); commands then worked.
Without it, commands silently do nothing even though the BLE link shows "connected".

After notifications are enabled the vendor app sends the sync frame `FE 0D AB 41 34 42 00 00 00 FF` (retried after 3 s if no
answer). The amp then reports its full state via notify frames. Status: unverified. Note frame `FE 0D AA 00 00 00 00 00 00 FF`
also exists (purpose unknown, not used in Phase 1).

## Phase 1 commands (write). Hex shown with value byte `VV`
| Control | Frame | Range / enum | Status |
|---|---|---|---|
| Amp type | `FE 0D 02 03 00 VV 00 00 00 FF` | 0 Clean, 1 Acoustic, 2 Driver(Drive), 3 Distortion, 4 Metal | **verified** 2026-11-15 (Clean 0 and Metal 4 via nRF Connect, default write type, no handshake/notifications needed in practice) |
| Gain | `FE 0D 02 03 02 VV 00 00 00 FF` | 0-30 | **verified** 2026-11-15 (nRF Connect, then the app) |
| Aux (music/Bluetooth) volume | `FE 0D 04 01 00 VV 00 00 00 FF` | 0-50 | **verified** 2026-11-16 (accepted by the amp, status 0; the amp reports it back in the `BF` dump and live `04 01 00`; the user confirmed by ear that it changes the Bluetooth music level while GUI VOL does not) |
| Amp level (hidden vendor control "guitar_a_level") | `FE 0D 02 03 01 VV 00 00 00 FF` | 0-50 | unverified (not Phase 1 UI) |
| Master volume (guitar) | `FE 0D 02 01 00 VV 00 00 00 FF` | 0-50 | **verified** 2026-11-15 (nRF Connect, then the app) |
| **Master delay** = guitar-tab "echo" (amp OLED: "GUI ECHO") | `FE 0D 02 02 00 VV 00 00 00 FF` | 0-50 | **verified** 2026-11-15 (user: GUI ECHO rises on the amp, delay audible). Per the user it is the master: with it at 0 the other delay settings are inaudible |
| Master reverb (guitar tab) | `FE 0D 02 02 01 VV 00 00 00 FF` | 0-50 | **verified** 2026-11-15 (user report) |
| EQ band N (N=1..7) | `FE 0D 02 04 0N VV 00 00 00 FF` | 0-20, 10 = flat (vendor default 10) | **verified** 2026-11-15 (audible via the app's EQ screen) |
| EQ reset | `FE 0D 02 04 00 00 00 00 00 FF` | – | unverified (vendor "reset EQ"; not Phase 1) |
| Guitar effect | `FE 0D 02 05 00 VV 00 00 00 FF` | 0 Bypass, 1 Flanger, 2 Chorus, 3 Phaser, 4 Wah, 5 Vibrato | **verified** 2026-11-15 (nRF Connect, then the app) |
| Reverb type | `FE 0D 05 01 00 VV 00 00 00 FF` | 0 Plate, 1 Room, 2 Spring | **verified** 2026-11-15 (nRF Connect, then the app) |
| Reverb level (effect tab) | `FE 0D 05 02 00 VV 00 00 00 FF` | 0-20 | **verified** 2026-11-15 (nRF Connect, then the app) |
| Reverb tone | `FE 0D 05 03 00 VV 00 00 00 FF` | 0-20 (assumed) | unverified |
| Reverb time | `FE 0D 05 04 00 VV 00 00 00 FF` | 0-20 (assumed) | unverified |
| Delay level (effect tab) | `FE 0D 05 05 00 VV 00 00 00 FF` | 0-20 | verified-by-user-report 2026-11-15 (all three delay knobs audibly work; they are inaudible while master delay = 0) |
| Delay feedback | `FE 0D 05 06 00 VV 00 00 00 FF` | 0-20 | verified-by-user-report 2026-11-15 (all three delay knobs audibly work; they are inaudible while master delay = 0) |
| Delay time | `FE 0D 05 07 00 VV 00 00 00 FF` | 0-20 | verified-by-user-report 2026-11-15 (all three delay knobs audibly work; they are inaudible while master delay = 0) |

Note: guitar-tab delay/reverb "level" (0-50, group 02) and effect-tab levels (0-20, group 05) are separate controls; Phase 1 UI
uses the effect-tab set for reverb/delay and the guitar-tab set only if Task 3 shows both are meaningful.

## Commands deliberately excluded
- `FE 0D 01 01 00 00 00 00 00 FF` — vendor "restore factory mode". **Never send.**
- All AUX playback/USB/mic/tuner/headphone/live-mode/noise-gate/group frames: out of scope (the aux **volume** is supported since 2026-11-16).
- Anything not in the table above.

## Notify (amp → phone) frames
Amp reports state with the same group/item/sub/value layout, e.g. `FE 0D 02 03 00 VV ...` = amp type, `02 01 00` volume,
`02 03 02` gain, `02 05 00` effect, `02 04 ...` EQ block (bands in `D[8]..D[14]` of an extended frame), effect-page block
`D[3]=reverb type, D[4]=reverb level, D[5]=tone, D[6]=time, D[7]=delay level, D[8]=feedback, D[9]=delay time`.
Vendor parser accumulates bytes and drops **16 bytes** per recognised single value (and 20/10 for other frames), so the amp
may send 16-byte chunks; real framing must be confirmed with nRF Connect in Task 3. Status: unverified. Until verified, the
app treats notify data as optional and keeps its own last-sent state.

## Notifications decoded by the app (verified on hardware 2026-11-15)
All notifications on `0xFFF2` are 16 bytes `FE 0F ... FF` (index = byte position). The app (`ProtocolCodec.decode`) understands:

| Frame | Content |
|---|---|
| Dump `BB` | volume `[3]`, amp type `[5]`, amp level `[6]` (unused), gain `[7]`, EQ bands 1..7 = `[8]..[14]` |
| Dump `BC` | guitar effect `[3]`, master delay `[13]`, master reverb `[14]` |
| Dump `C0` | reverb type `[3]`, level `[4]`, tone `[5]`, time `[6]`, delay level `[7]`, feedback `[8]`, delay time `[9]` |
| Dump `BF` | aux (music) volume `[3]` (verified 2026-11-16: it followed the aux value the app had written while GUI VOL in `BB` stayed different; `[14]` varies, meaning unknown, ignored) |
| Live `02 01 00 V` | volume (GUI VOL; the master pot also sends `04 01 00 V`, the aux volume) |
| Live `04 01 00 V` | aux (music) volume (also sent by the amp's master pot together with GUI VOL) |
| Live `02 03 00 V` / `02 03 02 V` | amp type / gain |
| Live `02 02 00 V` / `02 02 01 V` | master delay (GUI ECHO) / master reverb |
| Live `02 05 00 V`, `02 04 0N V` | effect, EQ band N (**not yet observed live**, decoded by analogy with the write frames) |
| Ignored | heartbeat `FE 0F 41 ...` (about 1 per second), dumps `BD`, `BE`, other `04 ..` items, anything malformed |

The dump arrives right after the sync write (on connect and on every `AmpRepository.refresh()`); the amp does not echo the app's own writes.
Read values are applied to the app state only; they are never written back. After a reconnect the app sends only the sync frame and
adopts the amp's dump; it does not replay what the user set earlier (that could overwrite changes made on the amp meanwhile).

User check on the phone with the real amp (2026-11-15): at start the screen shows the amp's real values; the master pot moves the
**Ses** slider live; Gain pot moves **Gain**; Amp Type pot moves the selected tone; GUI ECHO on the amp moves the delay master slider; returning to
the app after changing a value on the amp matches the amp; commands from the app still work.

## Open questions for Task 3
1. Advertised BLE device name of the Blaze 30.
2. Does a write need the connect handshake / notifications enabled first?
3. Exact notify framing (10 vs 16 byte chunks).
4. Write type (with/without response) accepted; minimum spacing between writes (vendor uses 300 ms).
5. Reverb tone/time and effect-page ranges (assumed 0-20).

## Tuner / pitch from the amp: investigated, not available (2026-11-15)
- Vendor tuner command `FE 0D 06 01 00 01` (tuner on) / `... 00` (off) was sent to the Blaze 30 and accepted (write status 0), but with guitar strings
  played for minutes in two runs the amp sent **no frame at all** (heartbeat unchanged, no `06 00 00 <string> <needle>` frame).
- The unused notify characteristic `0xFFF1` (the vendor app never touches it) was subscribed for one minute while playing: **no data**.
- Classic Bluetooth profiles the phone sees for the amp: `AudioSink`, `Avrcp` only (no mic/source), so no audio from the guitar input reaches the phone.
- Conclusion: the amp offers neither a tuner nor the guitar signal to the phone. A tuner has to listen with the phone microphone.

