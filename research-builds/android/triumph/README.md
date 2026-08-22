# Triumph Research for Android

Triumph Research is a separate experimental companion application for collecting the evidence needed to evaluate another Triumph motorcycle for Reset Moto Reminders. It installs alongside the main app as `dev.resetlight.research.triumph` and uses the visible name **Triumph Research**.

The research UI uses the same serious high-contrast dark visual direction as Reset Moto Reminders, including dark system bars and light navigation/status icons.

Current build: **v0.4.0** (`versionCode 4`). It adds the captured miles service command, preserves the selected dashboard unit through test and restoration, and uses the cross-validated single-send DTC-clear transaction. It installs alongside the main app; no motorcycle scan has yet validated this version on another Triumph.

The tester enters the motorcycle model and model year, selects a paired `vLinker MC-Android`, and runs one session. The app always completes and journals the bounded read phase first. The tester may explicitly add the known service reset in the unit currently shown on the motorcycle, DTC clear, or both to the same session. The report is evidence for review, not an automatic compatibility decision.

## What the scan collects

- vLinker ELM/STN identity, voltage, and active-protocol description.
- Responses from the known Triumph Keihin engine route.
- Every non-sensitive identifier read packaged in the Tiger 900 ECU profile.
- Confirmed-DTC count and, when non-zero, DTC detail records.
- One bounded extended-session attempt if the default-session DTC count is unavailable.
- Responses from the known Tiger TFT route: instrument status and raw kilometre odometer.
- Independent candidate classifications for DTC clear and service reset.
- When explicitly selected and eligible, a minimal service-reset round trip: write `previous + 100` in the selected km/miles unit and `previous date + 1 day`, verify the response echoes, then restore the entered previous values in the same unit and report restoration independently.
- When explicitly selected and eligible, the exact engine DTC-clear sequence, sent once, including response-pending/final handling and post-clear count verification.
- Profile hashes, probe outcomes, exact ELM request/response traffic, timing, and the terminal scan outcome.

The application never requests VIN or ECU serial data and never performs arbitrary identifier/address scans, generic writes, RoutineControl, ECU configuration, or streaming bus monitoring. Optional writes use a second exact allowlist: the known `1003`/SecurityAccess/DTC-clear/count-verify path and only the observed `33xx` km or `34xx` miles interval plus `5Cxx` date path. They are attempted only after the corresponding reads match and explicit acknowledgement. A lost/ambiguous test write is never followed by an automatic restore or retry because the write may already have succeeded; the report marks restoration unknown.

## Motorcycle test

1. Pair `vLinker MC-Android` in Android Bluetooth settings with PIN `1234`.
2. Connect the adapter to the motorcycle.
3. Keep the motorcycle stationary, engine off, ignition on and battery stable. Confirm the motorcycle date/time and note whether the dashboard is showing kilometres or miles.
4. Open **Triumph Research**, enter the exact model and model year, and select the paired adapter.
5. Leave both optional write tests unchecked for a read-only report, or select the operations needed for this visit. For the service test, select the unit currently shown on the dashboard and enter the **exact interval and date currently stored/displayed by the motorcycle**. The app cannot read those two values. It temporarily writes `+100` in the selected unit and `+1 day`, then restores what you entered without conversion. DTC clear permanently erases the stored fault evidence after it has been recorded in the report.
6. Read and tick the write acknowledgement if either operation is selected.
7. Tap **Connect and run scan** once. Do not switch off the ignition until the app reports completion or failure.
8. Tap **Share JSONL report** and save the report locally for project analysis.

A useful report contains `adapter_ready`, paired `elm` outbound/inbound events, module probe outcomes, `scan_finished`, optional `*_validation_finished` events, and `research_session_finished`. A bonded device shown in the UI is not evidence that the adapter or motorcycle responded.

Restoration is best-effort, not transactional: an explicit ECU rejection with a healthy connection is followed by a restore attempt, but disconnect, timeout or ambiguous delivery stops the session. In that case inspect the dashboard and report before trying anything again. DTC clear cannot restore erased faults and is intentionally clear-only.

Reports remain under the app-private `files/research-reports/` directory until explicitly shared. Cancellation and diagnostic failure preserve a partial report. Do not commit a collected report until it has been manually reviewed for identifiers and minimized into a safe fixture.

## Build and test

Run from `android/` with JDK 17 and the Android SDK configured:

```sh
./gradlew :triumphResearch:testDebugUnitTest
./gradlew :triumphResearch:lintDebug :triumphResearch:assembleDebug
```

The APK is generated at:

```text
research-builds/android/triumph/build/outputs/apk/debug/triumphResearch-debug.apk
```

Install it without replacing the main app:

```sh
adb install -r ../research-builds/android/triumph/build/outputs/apk/debug/triumphResearch-debug.apk
```

The reviewed implementation and safety plan is in [`PLAN.md`](PLAN.md). Folder-specific agent guidance is in [`AGENTS.md`](AGENTS.md).
