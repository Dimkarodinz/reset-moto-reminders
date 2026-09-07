# Reset Moto Reminders

[![Support on Ko-fi](https://img.shields.io/badge/Support%20on-Ko--fi-FF5E5B?logo=kofi&logoColor=white)](https://ko-fi.com/pippicat)

Reset Moto Reminders is an unofficial native Android/iOS app for Triumph Tiger 900 GT Pro (2021–2023) owners. From your phone, it connects through a Bluetooth OBD adapter to read the dashboard odometer, read or clear diagnostic trouble codes (DTCs), and reset the service reminder. Free community build, source available, no account or subscription, no dealer visit.

Project website and installation guides:
<https://dimkarodinz.github.io/reset-moto-reminders/>

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

Reading trouble codes and the service-reminder reset are validated on a real Tiger 900. DTC clear is available, clearly marked Beta, and still awaits its first controlled project-app motorcycle test.

## Supported models

### Tested

| Motorcycle | Adapter | Status |
| --- | --- | --- |
| Triumph Tiger 900 GT Pro (2021) | vLinker MC+ | Read operations and the service-reminder reset are validated through this app. DTC clear is implemented, gated and marked Beta, with hardware validation pending. |
| Triumph Tiger 900 GT Pro (2021) | OBDLink CX | Experimental BLE support is included on Android and iPhone. A physical CX + motorcycle test is still required. |
| Triumph Tiger 900 GT Pro (2021) | OBDLink MX, LX or MX+ | Separate experimental Android Classic profiles. A physical adapter + motorcycle test is still required. |

### Experimental motorcycle profiles (not tested)

The current experimental Android branch also lets you select the motorcycles below. Their protocol families match known implementations, but none of these combinations has been tested through this app on a real motorcycle yet.

| Possibly supported motorcycles | Model codes | Functions currently available |
| --- | --- | --- |
| Tiger 900, Tiger 900 GT, Tiger 900 Rally, Tiger 900 GT (LRH), Tiger 900 Rally Pro and Tiger 850 Sport — first generation | E60, E62, E63, E65, E67, E68 | Dashboard read, DTC read/clear and service reset |
| Tiger 900 — updated generation | C81, C82, C83 | DTC read/clear and updated-TFT service reset |
| Tiger Sport 660 | L20, L22 | DTC read/clear and hybrid-display service reset |
| Street Triple RS 765, Street Triple R, Street Triple RS and Street Triple Moto2 | A55, A60, A61, A62, D31 | DTC read/clear only |
| Bonneville T120, Bonneville Speedmaster, Bonneville Bobber, Speed Twin 900/1200/1200 RS, Thruxton RS and Bobber TFC | D40, D46, D53, D54, D56, DD0, DP0, DX0 | DTC read/clear only |
| Scrambler 900, Scrambler 1200 X and Scrambler 1200 XE | D44, DR0, DS0 | DTC read/clear only |
| Trident 660, Daytona 660 and Tiger Sport 800 | L10, L21, L23, L25 | DTC read/clear only |
| Speed Triple 1200 RS and RR | P01, P02, P11 | DTC read/clear only |
| Tiger 1200 GT and Rally family | P20, P21, P22, P23, P24 | DTC read/clear only |

Every row in this table is **experimental and not yet tested**. A listed model may still use a different ECU, instrument cluster or diagnostic-port wiring depending on its year and market. The app must recognize the expected live profile before it offers an operation.

See [`ecu-maps/README.md`](ecu-maps/README.md) for the exact profile and capability matrix. A shared ECU supplier or model name alone never proves compatibility.

## Requirements

- **A Bluetooth OBD-II adapter** based on the ELM327 / STN command set (an "ECU linker" / OBD tool). It must expose the bike's diagnostic CAN bus.
- Tested with the **vLinker MC+** OBD adapter (it pairs as `vLinker MC-Android` over Bluetooth Classic on Android, and advertises as `vLinker MC-IOS` over BLE on iPhone).
- **OBDLink CX** support is included on Android and iPhone using OBDLink's published BLE interface. It is marked experimental until a powered CX and Tiger 900 test is completed.
- The discontinued original **OBDLink MX Bluetooth** adapter is supported experimentally on Android only. Press its physical Connect button and pair `OBDLink MX` in Android settings; it does not use a fixed PIN.
- **OBDLink LX and MX+** have separate experimental Android profiles. Press Connect, pair the exact product name in Android settings, then select it in the app.
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
