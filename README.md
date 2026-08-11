# Reset Moto Reminders

[![Support on Ko-fi](https://img.shields.io/badge/Support%20on-Ko--fi-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/pippicat)

Reset Moto Reminders:

- Resets the service reminder after you do your own maintenance
- Reads, explains and clears error codes so you know what is happening with your bike

Free community build, source available, no account or subscription, no dealer visit.

Project website and installation guides:
<https://dimkarodinz.github.io/reset-moto-reminders/>

## Why this exists

An official dealer did a valve adjustment on my engine. Then they refused to reset the service light, because I had not bought their full service package. That is manipulation, not mechanics.

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

## How writes are constrained

Safety is built into the design, not bolted on afterward:

- The app performs **only the bounded operations above** — there is no code path that flashes firmware, writes calibration, or reprograms a module.
- Every write format is a **fixed, mapped diagnostic sequence**. The app never fuzzes or probes unknown write commands.
- Writes are **fail-closed and gated**: the app requires the selected motorcycle profile and a recognized live instrument response, and it asks you to confirm each write. An unknown response shape stops before the reminder write.
- It **never retries a write** after a disconnect or an unclear result.

Reading trouble codes and the service-reminder reset are validated on a real Tiger 900. DTC clear is available, clearly marked Beta, and still awaits its first controlled project-app motorcycle test.

## Supported models

| Motorcycle | Status | Service reset | DTC read / clear |
| --- | --- | --- | --- |
| Triumph Tiger 900 GT Pro, 2021 | Tested | Yes, tested | Read tested; clear Beta |
| Tiger 900 / Tiger 850 Sport (gen I) | Experimental | Supported, not tested | Supported, not tested |
| Tiger 900 (gen II) | Experimental | Supported, not tested | Supported, not tested |
| Tiger Sport 660 | Experimental | Supported, not tested | Supported, not tested |
| Street Triple 765 R / RS / Moto2 | Experimental | Supported, not tested | Supported, not tested |
| Bonneville / Bobber / Speed Twin / Thruxton — modern models | Experimental | Supported, not tested | Supported, not tested |
| Scrambler 900 / 1200 | Experimental | Supported, not tested | Supported, not tested |
| Trident 660 / Daytona 660 / Tiger Sport 800 | Experimental | Supported, not tested | Supported, not tested |
| Speed Triple 1200 RS / RR | Experimental | Supported, not tested | Supported, not tested |
| Tiger 1200 GT / Rally | Experimental | Supported, not tested | Supported, not tested |

Experimental profiles have not been physically validated yet. [Share your test result.](https://github.com/Dimkarodinz/reset-moto-reminders/issues/new?template=motorcycle-test.yml)

See [`ecu-maps/README.md`](ecu-maps/README.md) for the exact profile and capability matrix. A shared ECU supplier or model name alone never proves compatibility.

## Requirements

- Android 8+ or iOS 16+
- A supported Bluetooth OBD adapter

| Adapter | Android | iPhone |
| --- | --- | --- |
| vLinker MC+ | Tested | Tested |
| OBDLink CX | Experimental | Experimental |
| OBDLink MX / LX / MX+ | Experimental | Not supported |

*Personal note: the vLinker MC+ costs about €40, has no subscription, and does not wear out. It works with almost any car or motorcycle OBD-II port. Worth buying well beyond this app — a general diagnostic tool you keep for years.*

## Install

Download the signed Android APK and its SHA-256 checksum from
[GitHub Releases](https://github.com/Dimkarodinz/reset-moto-reminders/releases/tag/android-v0.12.0).
The APK is built from its public source tag and signed with the project release
key.

Android build instructions and the full hardware-test procedure are in
[`android/README.md`](android/README.md). The iPhone release provides an unsigned
IPA for local signing and the public Xcode project for self-build; both use the
rider's own Apple Account. See the
[step-by-step iPhone guide](https://dimkarodinz.github.io/reset-moto-reminders/install-ios.html)
and [`ios/README.md`](ios/README.md).

## Platforms

- **Android** — current target, built with Kotlin and Jetpack Compose over Bluetooth Classic (RFCOMM).
- **iOS** — native app for iOS 16+. Locally sign the release IPA or build it in
  Xcode with your own Apple Account. Connection and dashboard reading are
  validated on an iPhone 12.

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
