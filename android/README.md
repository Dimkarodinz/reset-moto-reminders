# Reset Moto Reminders for Android

Reset the service light, read trouble codes, and clear DTCs on a **Triumph Tiger 900** from an Android phone, using a Bluetooth OBD adapter. This is the Android app; see the [root README](../README.md) for the user-facing overview and supported models.

Current build: **v0.7.0** (`versionCode 11`). Reset Moto Reminders connects to a bonded `vLinker MC-Android` (tested with the **vLinker MC+** OBD adapter / ECU linker on Android and iPhone), verifies the captured ELM/STN identity, applies the common adapter initialization sequence and disconnects cleanly.

The rider-facing app uses a fixed, high-contrast dark theme with dark system bars; it does not follow the phone into a light presentation.

**Trouble-code reading** is a mainline feature. Once the adapter is ready, **Read trouble codes** performs the observed engine-ECU confirmed-DTC read in the default session and decodes each code against the packaged DTC dictionary. It is strictly a read: it never requests SecurityAccess, clears DTCs or sends any write. If the ECU returns a code count that does not match the decoded codes, the read reports a recoverable failure without dropping the connection.

Once the adapter is ready, the main screen exposes four focused motorcycle operations:

- **Dashboard information** — reads the odometer and instrument status as read-only proof that the motorcycle responded.
- **Read trouble codes** — reads and decodes confirmed engine DTCs.
- **Clear trouble codes (Beta)** — runs the observed extended-session and SecurityAccess sequence, sends the all-groups clear exactly once, consumes response-pending plus the final response, then verifies the remaining count. Requires explicit arm→confirm.
- **Reset service reminder** — asks for the unit currently selected on the motorcycle, interval and date, then writes either the observed km (`33xx`) or miles (`34xx`) interval followed by the date. A cluster fingerprint check fails closed before any write, and the UI reminds the rider to set motorcycle date/time correctly.

The **service reset is project-app hardware-validated for the km path** (full commit on 2026-08-13). The miles path is supported by a successful 2026-08-22 HCI capture and deterministic replay tests, but still needs its first project-app motorcycle run. **DTC clear is still hardware-unvalidated** and waits for the fault-provocation trip below. Neither write appears in the release build yet.

Checkpoint: v0.4.0 completed the first consolidated motorcycle read; v0.5.0 added the main DTC read; v0.6.0 added the gated writes; v0.6.1 fixed live CAN framing, stale routing, failure handling and pre-I/O validation; v0.6.2 added reset input/date validation and localization; v0.6.3 recorded the first project-app km reset. **v0.7.0** adds the captured miles command, a km/mi selector, the motorcycle date/time reminder, a user-facing read-only dashboard proof, and a no-resend fix for DTC response-pending. Still unexercised through this app: nonzero DTC detail decoding, DTC clear and the miles reset.

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

## Remaining motorcycle validation (v0.7.0)

> **Status (2026-08-22):** km reset is validated through this app. Miles reset is capture-validated and implemented. The remaining physical checks are one miles reset through v0.7.0 and one nonzero DTC read/clear.

The 2026-08-23 safety pass keeps the same rider flow but prevents operations from interleaving: while one dashboard/DTC/service action is running, the other action buttons and Disconnect are temporarily disabled. If the adapter loses a response after a write, the app does not retry or claim failure; it asks you to inspect the motorcycle or reconnect and read again. If the service interval was accepted but its date was not confirmed, the app reports the partial update explicitly.

Keep ignition on and engine off throughout each connection. Setup:

1. Connect the vLinker to the motorcycle diagnostic port so the adapter is powered.
2. In Android Bluetooth settings, pair `vLinker MC-Android`; use PIN `1234` when requested.
3. Install the debug APK: `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
4. Open Reset Moto Reminders, grant Bluetooth access if Android asks, and choose **Pair or select adapter**, then the bonded adapter, then **Connect**.
5. Confirm the screen reaches **Adapter ready** and shows `ELM327 v2.2`, `STN1151 v4.3.2` and map `vlinker-mc-android`.

**Run 1 — service reset:**

6. Choose **Read trouble codes** (expect zero on a healthy bike).
7. Make sure the motorcycle date/time is correct. Under **Reset service reminder**, select the unit currently shown on the motorcycle, enter the interval and next-service date, then choose **Reset reminder** and **Confirm reset**.
8. Confirm the card shows **Committed**, then choose **Disconnect**.

**Trigger a test fault (no riding):**

9. Turn the ignition **off**. Unplug the **front ABS / wheel-speed sensor** connector (accessible at the fork), then turn ignition **on** and wait ~1 minute for the fault to register.

**Run 2 — with fault (read + clear):**

10. Reconnect the app (steps 4–5), choose **Read trouble codes**, and confirm a nonzero, decoded code appears.
11. **Clear trouble codes (Beta):** choose **Clear trouble codes**, then **Confirm clear**. Confirm the card reports the remaining count (expect the fault to persist until the sensor is restored — that is fine; it proves the clear/verify path).
12. Choose **Disconnect**.

**Restore before leaving:** turn ignition off, reconnect the ABS sensor, then turn ignition on and confirm no warning lights remain. A miles-mode reset may be tested as Run 1 by selecting **mi**; do not select a unit different from the motorcycle display.

Stop at any point if the identity differs, the adapter disconnects repeatedly, the fingerprint gate blocks a write, or an unexpected error appears. Writes only ever send observed bytes and never auto-retry after a disconnect or ambiguous result. If the app says the result needs inspection, check the motorcycle and reconnect/read before considering another write. This trip should validate the nonzero DTC detail format, DTC clear, and optionally the captured miles service-reset path.

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

These journals are developer diagnostics only. The main app does not expose report/log sharing, and app data or full system/HCI captures must not be published.

## Planned work

- ~~**Localization.**~~ **Done.** The UI is localized into English, Spanish, Ukrainian, French and German, chosen automatically from the phone's language setting with English as the default and fallback (`values-es`/`-uk`/`-fr`/`-de` overlays). DTC messages use language-tagged maps with per-locale translation overlays falling back to the authoritative English. Only UI text is translated — protocol bytes, ECU/DTC map data and journal contents stay untouched. Adding a locale is one strings overlay plus one DTC translation overlay.
- **Support footer.** Show a small, always-visible "Buy Me a Coffee" / Ko-fi footer at the bottom of the screen (visible without scrolling) that opens the support links below in a browser. Deliberately **not** tied to a successful write: coupling a donation to the single riskiest operation reads as pressure at an emotional peak and sits closest to any liability claim. Keep it a passive footer on general app use — never a modal, never triggered by an operation outcome. No tracking, no in-app purchase.
- **Discoverability.** Optimize the project so it surfaces in both classic search engines and LLM answers. Keyword-rich README (motorcycle/model, "reset service reminder", "clear DTC", adapter/protocol terms) with clear headings and a plain "what it does / what it does not" summary; a description on every GitHub release (changes, supported motorcycle/adapter, APK asset); a GitHub project page with topics, About and all relevant attributes; and factual, quotable phrasing an LLM can cite accurately. Never publish captures, VINs or protocol dumps for reach — the private-logs boundary and safety accuracy come first.
- ~~**Miles command discovery.**~~ **Done 2026-08-22.** Reads remain canonical kilometres, while interval writes use service `0x33` for hundreds of kilometres and `0x34` for hundreds of miles. Because no reliable read identifies the dashboard setting, v0.7.0 asks the rider to select the unit currently shown on the motorcycle and echoes it in the confirmation. The remaining step is one project-app miles reset on the motorcycle.
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
