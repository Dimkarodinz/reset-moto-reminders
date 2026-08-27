# Reset Moto Reminders for iPhone

Native SwiftUI/CoreBluetooth preview of the main app for iOS 16 and later. It mirrors the supported Tiger 900 flow: dashboard/odometer read, confirmed-DTC read, DTC clear (Beta), and service-reminder reset in kilometres or miles.

## Current status

The development branch after version 0.1.4 (`build 5`) adds English-default German, Spanish, French and Ukrainian localization for the complete interface, Bluetooth/diagnostic errors and DTC descriptions. It retains number-pad dismissal, exact 100-unit interval validation, motorcycle-date guidance and the human-readable explanation of the `043` compatibility fingerprint. All 36 protocol/use-case tests plus five presentation-contract tests pass.

The corrected connection and dashboard-read path is physically validated. Every connection continues to perform one harmless adapter-only `ATI` identity check. If the expected notification/write layout, complete prompt or adapter identity is missing, the app disconnects before it sends a motorcycle command. The main app does not export logs; it emits bounded Apple system-log events for operation and command outcomes without raw replies, VINs or Bluetooth identifiers. DTC clear remains Beta until a controlled nonzero-DTC test is retained, and individual service-write modes keep their existing evidence gates.

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

1. Power the motorcycle and `vLinker MC-IOS`, open the app and tap **Connect**.
2. Confirm the app reaches **Motorcycle connected** and shows an adapter identity. If it stops earlier, preserve the exact on-screen error; do not keep retrying write features.
3. Tap **Read motorcycle**. Confirm the odometer is plausible.
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
