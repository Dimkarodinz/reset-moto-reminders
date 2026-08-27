# Reset Moto Reminders

[![Support on Ko-fi](https://img.shields.io/badge/Support%20on-Ko--fi-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/pippicat)

Reset the service light and clear diagnostic trouble codes (DTCs) on a Triumph Tiger 900 GT Pro (2021–2023) from your phone. Free community build, source available, no dealer visit.

Project website and installation guides:
<https://dimkarodinz.github.io/reset-moto-reminders/>

The website and both main apps support English, German, Spanish, French and Ukrainian. English is the default and fallback.

Reset Moto Reminders is an unofficial native Android/iOS app for Triumph Tiger 900 owners. It connects to the bike through a Bluetooth OBD adapter to read the dashboard odometer, read or clear trouble codes, and reset the service reminder. Nothing else.

## Why this exists

An official Madrid Triumph dealer did a valve adjustment on my engine. Then they refused to reset the service light, because I had not bought their full service package. That is manipulation, not mechanics.

Most routine service is an oil change and an air filter. You can do it yourself or at an independent shop. This app resets the reminder, so a DIY or independent repair is actually finished. Right to repair, in one small app.

## What it does

- **Read dashboard information.** Reads the odometer from the TFT instrument as a simple, read-only proof that the motorcycle responded.
- **Read trouble codes.** Reads confirmed DTCs from the engine ECU and decodes them into plain descriptions.
- **Clear trouble codes.** Shows the current codes, then clears them only after you confirm. Clearing removes the stored fault record; it does not repair the fault.
- **Reset the service reminder.** Sets the next-service distance and date on the instrument cluster. Resetting the reminder does not perform any maintenance.

## What it does not do

- It does not reflash, recode, remap, or recalibrate your ECU.
- It does not change emissions, immobilizer, or security settings.
- It does not run actuator tests or arbitrary diagnostic scans.
- It does not tune or increase power.
- It does not collect your data or require an account.

## It cannot break your ECU

Safety is built into the design, not bolted on afterward:

- The app performs **only the bounded operations above** — there is no code path that flashes firmware, writes calibration, or reprograms a module.
- Every command it sends is a **byte sequence observed from a real Tiger 900**. It never guesses, fuzzes, or probes unknown commands.
- Writes are **fail-closed and gated**: the app refuses to write unless it recognizes the exact motorcycle profile, and it asks you to confirm each write. An unknown or mismatched bike stays read-only.
- It **never retries a write** after a disconnect or an unclear result.

Reading trouble codes and the kilometre-mode service reset are validated on a real Tiger 900. The miles-mode reset is implemented from a successful captured reset and deterministic replay tests. DTC clear is available, clearly marked Beta, and still awaits its first controlled project-app motorcycle test.

## Supported models

### Tested

| Motorcycle | Adapter | Status |
| --- | --- | --- |
| Triumph Tiger 900 GT Pro (2021–2023) | vLinker MC+ | Read operations and the service-reminder reset (km mode) validated through this app. Miles reset is capture-validated and implemented, pending its first project-app test. DTC clear is implemented, gated and marked Beta, with hardware validation pending. |

### Potentially supported (not tested)

These share the same Keihin ECU family and TFT instrument generation as the tested bike, so they are candidates — but **each needs verification before it is declared compatible**:

| Motorcycle | Why it is a candidate | Status |
| --- | --- | --- |
| Other first-generation Triumph Tiger 900 variants (Tiger 900, GT, GT Low, Rally, Rally Pro), roughly 2020–2023 | Same Keihin engine ECU and same TFT instrument as the tested bike — almost certainly compatible | Very likely — a single capture would confirm it |
| Other modern Triumphs with a TFT dash (e.g. Street Triple, Speed Triple, Trident 660, Tiger Sport 660, Tiger 1200, Speed Twin, Bonneville / Scrambler 1200, Rocket 3) | Share the same Keihin UDS diagnostic family; reading trouble codes is the portable part | Unverified — clearing codes and the service reset need a per-model capture |

A shared ECU supplier or the name "Keihin" does not by itself prove compatible commands. MY2024-onward Tiger 900 models are a separate, unverified boundary. If you own one of these bikes and want to help verify it, see the repository issues.

## Requirements

- **A Bluetooth OBD-II adapter** based on the ELM327 / STN command set (an "ECU linker" / OBD tool). It must expose the bike's diagnostic CAN bus.
- Tested with the **vLinker MC+** OBD adapter (it pairs as `vLinker MC-Android` over Bluetooth Classic on Android, and advertises as `vLinker MC-IOS` over BLE on iPhone). This adapter profile has been captured on both iPhone and Android hardware.
- An Android 8+ phone, or an iPhone running iOS 16+ for the source/self-build version.

*Personal note: the vLinker MC+ costs about €40, has no subscription, and does not wear out. It works with almost any car or motorcycle OBD-II port. Worth buying well beyond this app — a general diagnostic tool you keep for years.*

## Install

Download the signed Android APK and its SHA-256 checksum from
[GitHub Releases](https://github.com/Dimkarodinz/reset-moto-reminders/releases/latest).
The APK is built from its public source tag and signed with the project release
key.

Android build instructions and the full hardware-test procedure are in
[`android/README.md`](android/README.md). The iPhone app is installed from the
public Xcode project with the rider's own Apple Account; see the
[step-by-step iPhone guide](https://dimkarodinz.github.io/reset-moto-reminders/install-ios.html)
and [`ios/README.md`](ios/README.md).

## Help test another Triumph

Own a Triumph that is not listed above? The Android-only **Triumph Research** app
runs one compatibility scan and creates a report for your exact model and year:

1. Enter the motorcycle model and year.
2. Connect the vLinker with the ignition on and engine off.
3. Run the scan once, then tap **Share JSONL report**.
4. Attach the report to a [GitHub issue](https://github.com/Dimkarodinz/reset-moto-reminders/issues/new).

The scan is read-only by default. Optional service-reset and DTC-clear tests can
change stored motorcycle data and must be enabled explicitly. See the
[Triumph Research instructions](research-builds/android/triumph/README.md).

For other motorcycle brands, use the separate read-only
[Motorcycle Research collector](research-builds/android/general/README.md).

## Platforms

- **Android** — current target, built with Kotlin and Jetpack Compose over Bluetooth Classic (RFCOMM).
- **iOS** — native app for iOS 16+. Install it from Xcode with your own Apple
  Account. Connection and dashboard reading are validated on an iPhone 12.

## Unofficial project

Unofficial project. Not affiliated with or endorsed by Triumph Motorcycles. "Triumph" and "Tiger" are used only to describe compatibility. This software comes with no warranty. You are responsible for how you use it on your own vehicle.

## Support

The GitHub community build is free for personal, noncommercial use, with all of
its features available without payment. A separately licensed official paid
store build may be offered later, after the safety and release gates are met.
If the community build saved you a dealer visit, you can chip in — entirely
optional and unrelated to features, support, or license rights:

- Ko-fi: <https://ko-fi.com/pippicat>

## License

The project is source-available under the
[PolyForm Noncommercial License 1.0.0](LICENSE), Copyright 2026 Dmytro Rodin.
You may inspect the code and use, modify, or redistribute it only for purposes
permitted by that license. Commercial use requires a separate written license;
see [`COMMERCIAL_LICENSING.md`](COMMERCIAL_LICENSING.md). This is a
noncommercial source-available license, not an OSI-approved open-source license.

External copyrightable contributions are temporarily closed until a contributor
agreement is available. See [`CONTRIBUTING.md`](CONTRIBUTING.md) and
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
