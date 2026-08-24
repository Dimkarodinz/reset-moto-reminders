# Reset Moto Reminders

Reset the service light and clear diagnostic trouble codes (DTCs) on a Triumph Tiger 900 GT Pro (2021–2023) from your phone. Free community build, source available, no dealer visit.

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

Reading trouble codes and the kilometre-mode service reset are validated on a real Tiger 900. A successful miles-mode reset has now been captured and implemented, but still needs its first validation through this app. DTC clear is built, marked Beta and gated, but still awaits its first project-app motorcycle test. Write features remain debug-only until their release gates are satisfied.

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

Download the APK from the project releases and install it on your Android phone. Every release APK is built from a public source tag and published with a SHA-256 checksum so you can verify it.

Android build instructions and the full hardware-test procedure are in [`android/README.md`](android/README.md). The iPhone app is currently a source/self-build preview; see [`ios/README.md`](ios/README.md).

## Help test another Triumph

The separate **Triumph Research** Android app performs one bounded compatibility session. It asks for model/year, saves the known Triumph engine/TFT read evidence first, and can optionally test the exact selected-unit service reset and DTC-clear sequences in that same connection after an explicit warning and acknowledgement. The service test uses the entered current values, temporarily writes `+100` in km or miles plus `+1 day`, then restores the entered baseline in the same unit. It never requests VIN/serial data, fuzzes commands, or tries generic writes.

Build and motorcycle-test instructions are in [`research-builds/android/triumph/README.md`](research-builds/android/triumph/README.md). A successful report identifies evidence for the entered motorcycle; it does not by itself declare an entire model family supported. Optional writes can alter stored data: DTC clear erases fault evidence, and service restoration cannot be guaranteed after disconnect or an ambiguous result.

The separate **Motorcycle Research** Android app is the read-only first pass for
other motorcycle families. It records a finite set of standard OBD capabilities
and DTC responses without requesting VIN, clearing codes, resetting service data,
scanning addresses or guessing commands. See
[`research-builds/android/general/README.md`](research-builds/android/general/README.md).
Its v0.2.0 interface now matches the fixed dark presentation of the other apps;
the scan itself remains intentionally unchanged and read-only.

## Platforms

- **Android** — current target, built with Kotlin and Jetpack Compose over Bluetooth Classic (RFCOMM).
- **iOS** — native SwiftUI/CoreBluetooth preview implemented for iOS 16+. Version 0.1.4 (`build 5`) follows a successful connection and dashboard read on an iPhone 12. It includes the `ATWS` response fix, strict 100-unit service input, a dismissible numeric keyboard, corrected motorcycle-date guidance and a human explanation of the dashboard compatibility fingerprint. The transport requires the GATT acknowledgement and complete ELM response, rejects wrong-module replies and closes on backgrounding without retrying an ambiguous write. Distribution is source plus Xcode self-build instructions.

## Unofficial project

Unofficial project. Not affiliated with or endorsed by Triumph Motorcycles. "Triumph" and "Tiger" are used only to describe compatibility. This software comes with no warranty. You are responsible for how you use it on your own vehicle.

## Support

The GitHub community build is free for personal, noncommercial use, with all of
its features available without payment. A separately licensed official paid
store build may be offered later, after the safety and release gates are met.
If the community build saved you a dealer visit, you can chip in — entirely
optional and unrelated to features, support, or license rights:

- Buy Me a Coffee: <https://www.buymeacoffee.com/CHANGE_ME>
- Ko-fi: <https://ko-fi.com/CHANGE_ME>
- GitHub Sponsors: <https://github.com/sponsors/CHANGE_ME>

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
