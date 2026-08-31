# Original OBDLink MX support for Android

## Context

Reset Moto Reminders supports the physically validated vLinker MC+ Bluetooth Classic profile and an experimental OBDLink CX BLE profile. The discontinued original OBDLink MX Bluetooth adapter is Android/Windows only, uses Android system pairing, presents as `OBDLink MX`, and is ELM327-command compatible according to OBDLink's product and support documentation.

## Goals

- Add the original OBDLink MX Bluetooth adapter as a separate experimental Android profile.
- Pair it in Android settings after pressing the adapter's physical Connect button, then use Bluetooth Classic SPP/RFCOMM.
- Require the exact bonded Bluetooth name `OBDLink MX`, the manufacturer-defined `STDI` hardware identity, and the original-MX `STN115` firmware family before any motorcycle request.
- Reuse the existing, exact Tiger 900 dashboard, DTC and service-reminder operations without changing a motorcycle command.
- Preserve vLinker MC+ and OBDLink CX behavior and safety gates.
- Publish Android v0.10.0 from the merged `develop` commit, then update the public website after the release artifact exists.

## Constraints

- Use red-green-refactor TDD for production behavior.
- Android only. Do not add original MX to the iPhone profile or claim iOS compatibility.
- Do not treat original MX as MX+, LX, CX or a generic OBD adapter.
- Do not use a guessed PIN. OBDLink documents a time-limited physical-button pairing flow.
- Use only the standard SPP service UUID and the existing prompt-framed command queue.
- Exact Bluetooth name/profile selection must occur before adapter identity and before motorcycle traffic.
- Unknown devices, profile identities or initialization replies fail closed.
- Existing ambiguous-write/no-automatic-retry behavior remains unchanged.
- No original MX hardware is available in this implementation session. Label support experimental until a powered adapter/Tiger test succeeds.
- Never publish private captures, credentials, signing material or device identifiers.

## Success criteria

- Schema and loader tests cover the new map.
- Session tests prove exact MX selection, correct profile-specific RFCOMM construction, successful identity/initialization replay and rejection of MX+/nearby names.
- Regression tests retain vLinker Classic and CX BLE selection and readiness behavior.
- Main Android unit tests, lint and release build pass.
- A reviewed feature PR is merged into `develop`.
- A maintainer-signed Android v0.10.0 APK and checksum are published from the exact merged/tagged commit.
- Website and README links are updated only after that release is available.
