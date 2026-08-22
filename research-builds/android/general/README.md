# Motorcycle Research for Android

This separate APK collects a bounded, read-only first-pass compatibility report
from motorcycles outside the currently mapped Triumph family. It installs beside
Reset Moto Reminders and Triumph Research as `dev.resetlight.research.general`.

## What one run collects

- tester-entered manufacturer, model and model year;
- validated vLinker MC-Android identity, voltage and selected protocol;
- standard OBD supported-PID pages;
- stored, pending and permanent standard DTC responses;
- Mode 09 capabilities, calibration IDs, CVNs and ECU names.

It never requests VIN, serial data, SecurityAccess, DTC clear, service reset,
generic writes, routines, passive monitoring, address scans or guessed commands.
The report is a discovery input, not proof that any write is compatible.

Version 0.2.0 uses the same fixed dark visual language as the main and Triumph
research apps. This is a presentation refresh only: its scan remains strictly
read-only and does not reuse Triumph-specific commands on other brands.

## Build and install

From `android/`, with JDK 17 and the Android SDK configured:

```sh
./gradlew :generalResearch:testDebugUnitTest :generalResearch:lintDebug :generalResearch:assembleDebug
adb install -r ../research-builds/android/general/build/outputs/apk/debug/generalResearch-debug.apk
```

Pair `vLinker MC-Android` in Android Bluetooth settings first. Power the adapter
from the motorcycle, keep ignition on and engine off, enter the motorcycle
details, select the paired adapter, then start one scan. Share the newest JSONL
report from the terminal screen. Review and minimize it before committing any
derived fixture; never commit a complete real report.

An unsupported request response such as `NO DATA` is expected and does not stop
later independent reads. A transport timeout or disconnect stops the session and
keeps the partial report shareable.

See [`PLAN.md`](PLAN.md) for the reviewed scope and [`AGENTS.md`](AGENTS.md) for
implementation boundaries.
