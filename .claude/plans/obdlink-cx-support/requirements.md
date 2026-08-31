# OBDLink CX support

## Context

Reset Moto Reminders currently supports the vLinker MC+ through Bluetooth Classic on Android and the vLinker MC-IOS through BLE on iOS. OBDLink publishes the CX BLE UART layout and connection rules, so CX transport support can be implemented without capturing another application's traffic.

## Goals

- Add OBDLink CX discovery, connection, identity validation and prompt-framed ELM communication on Android and iOS.
- Reuse the existing, exact Tiger 900 dashboard, DTC and service-reminder operations without changing motorcycle commands.
- Keep all existing serialization, explicit-confirmation, profile, ambiguity and no-retry protections.
- Add an adapter-map record backed by OBDLink's published interface.
- Prepare non-executable Android MX+/LX compatibility metadata for later hardware validation; do not claim or enable those adapters now.
- Ship tested Android and unsigned iOS release artifacts through GitHub Releases and update the public website only after the files are available.

## Constraints

- Use strict red-green-refactor TDD for production behavior.
- Do not use the CX internal `FEF5` service.
- CX uses service `FFF0`, notify characteristic `FFF1` and write characteristic `FFF2`.
- BLE writes must respect the negotiated maximum, be serialized and wait for acknowledgement; notification fragments must be assembled through the existing ELM prompt boundary.
- Pairing/bonding stays with the operating system. Do not use hidden Bluetooth APIs.
- Unknown names, services, characteristics, properties or adapter identities fail closed.
- A disconnected or timed-out state-changing operation remains ambiguous and is never retried automatically.
- No hardware test is available in this implementation session. CX must be presented as experimental/unverified until a powered-adapter and motorcycle test succeeds.
- Never publish private captures, credentials, device addresses, signing material or retained third-party files.

## Success criteria

- Map and schema tests cover CX and reject unsafe layouts.
- Android unit tests cover adapter selection, permissions, BLE discovery, chunking, notification reassembly, timeout, cancellation and disconnect behavior.
- iOS core/presentation tests cover multi-adapter selection, layout validation, chunking and identity gates.
- Existing Android and iOS tests remain green.
- Android lint, signed release build and iOS generic-device/simulator/unsigned release builds pass.
- Public release pages provide versioned artifacts and checksums.
- Website and README name CX as experimental and do not broaden motorcycle compatibility.
