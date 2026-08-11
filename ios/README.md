# Reset Moto Reminders for iPhone

Native SwiftUI/CoreBluetooth app for iOS 16 and later. It reads confirmed DTCs, clears them with a visible Beta warning, and resets service reminders in kilometres or miles. Dashboard/odometer reading is also available for compatible first-generation Tiger instruments.

## Current status

Version 0.4.0 (`build 9`) includes experimental OBDLink CX support alongside the validated vLinker MC-IOS transport. It also includes the shared Triumph motorcycle selector and the original, updated, hybrid and live-detected combined service-reset families listed in [`../ecu-maps/README.md`](../ecu-maps/README.md). The validated Tiger 900 GT Pro remains the default; every added motorcycle is visibly marked experimental.

The corrected connection and default Tiger dashboard-read path is physically validated. Every connection continues to perform one harmless adapter-only `ATI` identity check. If the expected notification/write layout, complete prompt or adapter identity is missing, the app disconnects before it sends a motorcycle command. Unknown combined-service response shapes stop before the reminder write; writes are sent once and verified without trying an alternate format. The main app does not export logs or record raw replies, VINs or Bluetooth identifiers. DTC clear remains Beta, and every added motorcycle/adapter combination requires a physical test.

## Build and install with a free Apple ID

1. Open [`ResetMotoReminders.xcodeproj`](ResetMotoReminders/ResetMotoReminders.xcodeproj) in Xcode.
2. Connect and unlock the iPhone, trust the Mac if prompted, and select the iPhone as the run destination.
3. In the `ResetMotoReminders` target under **Signing & Capabilities**, select your Personal Team. If Xcode reports that the bundle identifier is unavailable, change `dev.resetlight.ios` to a unique identifier such as `com.yourname.resetmotoreminders`.
4. Press **Run**. Accept the Bluetooth permission prompt on first connection.

A free Personal Team signature normally needs redeployment after about seven days. Do not publish or share a Personal-Team/development-signed IPA.

After the iPhone trusts the Personal Team once, a maintainer can build, sign,
install and launch later updates from the command line while the phone is
connected and unlocked; pressing Xcode's Run button is not required each time.

## First phone test

Use ignition on and engine off unless the motorcycle procedure requires otherwise.

1. Before connecting, select the exact motorcycle family. Power the motorcycle and either `vLinker MC-IOS` or `OBDLink CX`, then tap **Connect**.
2. Confirm the app reaches **Motorcycle connected** and shows an adapter identity. If it stops earlier, preserve the exact on-screen error; do not keep retrying write features.
3. If **Read motorcycle** is available, tap it and confirm the odometer is plausible.
4. Tap **Read trouble codes**. Confirm the count/list is plausible.
5. Only after the two reads succeed, test service reset with the intended dashboard unit and date. Check that the motorcycle date is correct and verify the dashboard afterward.
6. Treat **Clear trouble codes (Beta)** as destructive diagnostic evidence removal. Read and record the codes first, then clear only if that is intentional.

If a read still fails, preserve the exact on-screen message and keep the phone connected to the Mac so the bounded system log can be inspected.

## Developer checks

From the repository root:

```sh
swift test --package-path ios/ResetMotoCore
xcodebuild -project ios/ResetMotoReminders/ResetMotoReminders.xcodeproj \
  -scheme ResetMotoReminders -sdk iphonesimulator \
  -destination 'generic/platform=iOS Simulator' CODE_SIGNING_ALLOWED=NO build
xcodebuild -project ios/ResetMotoReminders/ResetMotoReminders.xcodeproj \
  -scheme ResetMotoReminders -sdk iphoneos \
  -destination 'generic/platform=iOS' CODE_SIGNING_ALLOWED=NO build
```

After changing the shared adapter, ECU or DTC YAML maps, regenerate the typed iOS resource and rerun the checks:

```sh
ruby ios/tools/sync_profiles.rb
ruby ios/tools/sync_localizations.rb
```

The generated JSON and `.strings` bundles are bundled data, not separate sources of truth. The localization generator reuses the matching Android wording and adds iPhone-only text for English, German, Spanish, French and Ukrainian.
