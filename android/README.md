# Reset Moto Reminders for Android

Reset the service light, read trouble codes, and clear DTCs on a **Triumph Tiger 900** from an Android phone, using a Bluetooth OBD adapter. This is the Android app; see the [root README](../README.md) for the user-facing overview and supported models.

Current build: **v0.6.3** (`versionCode 10`). Reset Moto Reminders connects to a bonded `vLinker MC-Android` (tested with the **vLinker MC+** OBD adapter / ECU linker on Android and iPhone), verifies the captured ELM/STN identity, applies the common adapter initialization sequence and disconnects cleanly.

**Trouble-code reading** is a mainline feature. Once the adapter is ready, **Read trouble codes** performs the observed engine-ECU confirmed-DTC read in the default session and decodes each code against the packaged DTC dictionary. It is strictly a read: it never requests SecurityAccess, clears DTCs or sends any write. If the ECU returns a code count that does not match the decoded codes, the read reports a recoverable failure without dropping the connection.

Four research-only (debug build) paths sit behind `RESEARCH_BUILD` and are absent from release builds:

- **Read-only ECU capture** — single-attempt, non-sensitive identifiers plus DTC count/details, with one conditional extended-session retry. No write.
- **Instrument read** — first contact with the cluster, sends only the two observed reads (`5E01`, `0D01`) and decodes the odometer. No write.
- **Clear trouble codes (v0.6.0, write)** — runs the observed extended-session → security-access → clear → count-verify sequence. The engine seed/key derivation executes only on this engine-ECU path. Requires an explicit arm→confirm.
- **Reset service reminder (v0.6.0, write)** — writes a new interval and next-service date to the instrument, replaying only observed bytes. Gated by a fingerprint check (exact motorcycle profile + observed transport route + live `5E01` `043` status) that fails closed before any write byte. Requires an explicit arm→confirm.

The **service reset is hardware-validated for the km path** (full commit on the motorcycle on 2026-08-13); **DTC clear is still hardware-unvalidated** and waits for the fault-provocation trip below. Neither write can appear in a release build.

Checkpoint: v0.4.0 (`versionCode 5`) completed the consolidated motorcycle research test on 2026-08-10 — adapter initialization, identity match, all identifier reads and a default-session zero-DTC count read, with every command inside the read-only allowlist (journal and analysis in `logs/2026-08-10/`, root `AGENTS.md` checkpoint updated). v0.5.0 (`versionCode 6`) added the mainline DTC read and the research instrument read on top of that validated path. v0.6.0 (`versionCode 7`) added the two gated writes. v0.6.1 (`versionCode 8`) fixed the four bugs the 2026-08-12 trip exposed (live CAN framing, stale route, session teardown, pre-I/O validation). v0.6.2 (`versionCode 9`) added service-reset input validation and a next-service date picker, plus full five-language UI localization. v0.6.3 (`versionCode 10`) recorded the 2026-08-13 dash-flip evidence — the km-mode service reset **committed on hardware** (first project-app write validated end-to-end), while the identical write with the dashboard in miles was rejected by the cluster and correctly blocked, now with an actionable message. All paths are covered by transcript/scripted unit tests (165 tests), including replays of the real km commit and miles rejection. Still unexercised against a motorcycle: the DTC detail read with a nonzero count, the extended-session branch, DTC clear, and any miles-mode write.

## Build

Requirements:

- JDK 17
- Android SDK platform 36 and Build Tools 35.0.0
- A physical Android 8+ device; the current hardware target is the Samsung Android 11 phone

From this folder:

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew --offline --no-daemon clean testDebugUnitTest lintDebug assembleDebug assembleRelease
```

The installable debug APK is generated at `app/build/outputs/apk/debug/app-debug.apk`. The release task also verifies that the optimized release variant can be assembled; it is not a distributable signed release.

## Harvest-sweep motorcycle trip (v0.6.0)

> **Status (2026-08-13):** Run 1 (harvest + service reset) is **done** — the km-mode reset committed on hardware. The Run 3 units experiment is **done** in a stronger form: the rider reset in km, flipped the dash to miles and reset again, proving reads are canonical while the interval write is unit-dependent (the km-scaled write was rejected in miles mode). **Still pending: Run 2** — the DTC fault-provocation read + clear (steps 11–14). The instructions below remain the plan for that remaining run.

This build is designed so one trip gathers everything and validates both writes. Keep ignition on and engine off throughout each connection. Setup:

1. Connect the vLinker to the motorcycle diagnostic port so the adapter is powered.
2. In Android Bluetooth settings, pair `vLinker MC-Android`; use PIN `1234` when requested.
3. Install the debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Open Reset Moto Reminders, grant Bluetooth access if Android asks, and choose **Pair or select adapter**, then the bonded adapter, then **Connect**.
5. Confirm the screen reaches **Adapter ready** and shows `ELM327 v2.2`, `STN1151 v4.3.2` and map `vlinker-mc-android`.

**Run 1 — healthy state (harvest + service reset):**

6. Choose **Capture read-only ECU data** once; wait for its terminal state. Do not repeat it in the same connection.
7. Choose **Read instrument data** and note the odometer/status.
8. Choose **Read trouble codes** (expect zero on a healthy bike).
9. **Reset service reminder:** enter the interval (km) and next-service date, choose **Reset reminder**, then **Confirm reset**. Confirm the card shows **Committed**.
10. Choose **Disconnect**, and copy the newest private journal (commands below).

**Trigger a test fault (no riding):**

11. Turn the ignition **off**. Unplug the **front ABS / wheel-speed sensor** connector (accessible at the fork), then turn ignition **on** and wait ~1 minute for the fault to register.

**Run 2 — with fault (read + clear):**

12. Reconnect the app (steps 4–5), choose **Read trouble codes**, and confirm a nonzero, decoded code appears.
13. **Clear trouble codes:** choose **Clear trouble codes**, then **Confirm clear**. Confirm the card reports the remaining count (expect the fault to persist until the sensor is restored — that is fine; it proves the clear/verify path).
14. Choose **Disconnect** and copy that journal too.

**Run 3 — units experiment (healthy, dash in miles):**

15. Turn ignition **off**, **replug the ABS sensor**, then in the bike's dashboard menu **switch the display units to miles**. Ignition **on** and reconnect.
16. Choose **Capture read-only ECU data** and **Read instrument data**. This second read of the same odometer, with only the dash unit changed, is the km-vs-miles comparison against Run 1 — read-only, no write is needed.
17. Choose **Read trouble codes**, then **Clear trouble codes** / **Confirm clear** once more to leave a clean fault memory. Disconnect and copy the final journal.

**Restore before leaving:** switch the dashboard back to **km**, and confirm the ABS sensor is reconnected with no warning lights remaining.

Stop at any point if the identity differs, the adapter disconnects repeatedly, the fingerprint gate blocks a write, or an unexpected error appears. Writes only ever send observed bytes and never auto-retry after a disconnect or ambiguous result. This trip should validate: the DTC detail read + nonzero count/format, whether DTC clear needs SecurityAccess (and the seed/key derivation on hardware), the instrument path, the gated service reset, and — by diffing the Run 1 (km) and Run 3 (miles) instrument bytes — whether the cluster stores a canonical unit or the rider's chosen unit.

The phone is not required for the local unit, lint and APK build. Use ADB only when the phone is connected again and you intentionally start this trip.

To list the private debug journals while the phone is connected:

```sh
adb shell run-as dev.resetlight ls files/diagnostic-logs
```

Copy one journal by replacing `<name>` with the listed filename:

```sh
adb exec-out run-as dev.resetlight cat files/diagnostic-logs/<name> > ecu-capture.jsonl
```

Choose the newest journal containing `connection_state` and `elm` events. A 302-byte journal containing only `adapter_profile_loaded` is merely an app launch and has no connection evidence.

Analyze the pulled journal against the maps with the offline analyzer from the repository root:

```sh
./tools/journal-analyze.py ecu-capture.jsonl
```

It reports adapter identity, the outbound-command audit against the read-only allowlist, identifier/DTC decoding and a findings list; exit code 2 means at least one critical finding. It reads local files only and never modifies a map.

## Private logs

Ordered JSONL diagnostic journals are stored in the app-private `diagnostic-logs/` directory. Bluetooth addresses are not written to them. VIN, serial-number-like fields and UDS SecurityAccess payloads are redacted before persistence. The directory is excluded from Android backup and device transfer.

Log viewing, redacted export and retention controls remain work for the next implementation checkpoint. Do not publish app data or full system/HCI captures.

## Planned work

- ~~**Localization.**~~ **Done.** The UI is localized into English, Spanish, Ukrainian, French and German, chosen automatically from the phone's language setting with English as the default and fallback (`values-es`/`-uk`/`-fr`/`-de` overlays). DTC messages use language-tagged maps with per-locale translation overlays falling back to the authoritative English. Only UI text is translated — protocol bytes, ECU/DTC map data and journal contents stay untouched. Adding a locale is one strings overlay plus one DTC translation overlay.
- **Support footer.** Show a small, always-visible "Buy Me a Coffee" / Ko-fi footer at the bottom of the screen (visible without scrolling) that opens the support links below in a browser. Deliberately **not** tied to a successful write: coupling a donation to the single riskiest operation reads as pressure at an emotional peak and sits closest to any liability claim. Keep it a passive footer on general app use — never a modal, never triggered by an operation outcome. No tracking, no in-app purchase.
- **Discoverability.** Optimize the project so it surfaces in both classic search engines and LLM answers. Keyword-rich README (motorcycle/model, "reset service reminder", "clear DTC", adapter/protocol terms) with clear headings and a plain "what it does / what it does not" summary; a description on every GitHub release (changes, supported motorcycle/adapter, APK asset); a GitHub project page with topics, About and all relevant attributes; and factual, quotable phrasing an LLM can cite accurately. Never publish captures, VINs or protocol dumps for reach — the private-logs boundary and safety accuracy come first.
- **Miles support.** Make the app represent the bike's own unit: it shows and accepts whatever unit the dashboard uses and never displays or writes a value that disagrees with it. There is deliberately no phone-locale conversion — the bike is the only source of truth. The 2026-08-13 dash-flip experiment answered the unit model and it is **mixed**: reads are canonical (odometer/status bytes identical in km and miles), but the interval **write is unit-dependent** — the km-scaled write accepted in km mode was rejected with the dash in miles, and no field reporting the current dash unit has been found. The app therefore keeps the km path, and on a miles-mode rejection tells the user to switch the dash to km and retry. Unblocking real miles writes requires one capture of a **successful** miles-mode interval write (via the third-party source with the dash in miles, harvested over HCI snoop like the original observations) to derive the miles scaling — it must not be guessed. Then fold the unit into the fingerprint gate and refuse the write whenever the unit cannot be established.
- **Consent / right-to-repair gate.** Before the first write-capable release, add a one-time in-app consent screen (unofficial project, no warranty, a reminder reset is not maintenance, a cleared code is not a repair, use is at the owner's own risk) plus the right-to-repair posture from the root README. This is a real acknowledgement, not a buried EULA, and requires the Spanish/EU legal review noted in `../LEGAL_RESTRICTIONS.md`.
- **Distribution.** Publish signed GitHub release APKs with a SHA-256 checksum, built from a public source tag (the flow in `../LEGAL_RESTRICTIONS.md`). No CI — this is a single-developer project with no remote, so a tagged local build is the release mechanism.

## Support

The GitHub community build is free for personal, noncommercial use. A separately
licensed official paid store build may be offered later after the release gates
are met. If the community build saved you a dealer visit, you can support
development:

- Buy Me a Coffee: <https://www.buymeacoffee.com/CHANGE_ME>
- Ko-fi: <https://ko-fi.com/CHANGE_ME>
- GitHub Sponsors: <https://github.com/sponsors/CHANGE_ME>

Support is entirely optional and buys no features, priority, warranty, or
commercial license rights. See the repository's [`LICENSE`](../LICENSE) and
[`COMMERCIAL_LICENSING.md`](../COMMERCIAL_LICENSING.md).
