# Reset Moto Reminders for Android

Android app for reading motorcycle information, reading and clearing DTCs, and resetting the service reminder.

Currently supported: Triumph Tiger 900 GT Pro (2021) with vLinker MC+. OBDLink CX support is included as experimental until a physical adapter/motorcycle test is completed.

## Build from source

### Android Studio

1. Clone or download this repository.
2. Open the `android` folder in Android Studio.
3. Connect an Android phone with USB debugging enabled.
4. Select the `app` configuration and press **Run**.

Android Studio will build and install the app.

### Terminal

You need JDK 17 and the Android SDK.

```sh
cd android
./gradlew :app:assembleDebug
```

The APK will be created at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected phone:

```sh
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Use the app

1. For vLinker, pair `vLinker MC-Android` in Android settings with PIN `1234`. OBDLink CX pairs from the app when connected; its fallback PIN is `123456`.
2. Connect the adapter to the motorcycle.
3. Turn the ignition on and keep the engine off.
4. Open the app, select the adapter, and tap **Connect**.
5. Read the motorcycle before using a write action.

DTC clearing is marked **Beta**. Record the codes before clearing them.

For a ready-to-install signed build, use [GitHub Releases](https://github.com/Dimkarodinz/reset-moto-reminders/releases).
