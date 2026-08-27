# Reset Moto Reminders for iOS — implementation plan

## Goal

Build a native SwiftUI/CoreBluetooth counterpart to the main Android app for the supported 2021 Triumph Tiger 900 GT Pro profile:

- connect to `vLinker MC-IOS`;
- read dashboard status and odometer;
- read confirmed DTCs;
- clear confirmed DTCs with a visible **Beta** label and explicit confirmation;
- reset the service reminder in kilometres or miles with explicit confirmation.

The app is English-only in this first iOS version. It contains no map flashing, ECU configuration, adaptations, or general-purpose command console.

## Safety boundary

The production app has completed its bounded adapter-only `ATI` check over the primary `18F0` channel on a powered MC-IOS. Every live session still repeats that fail-closed check and sends no motorcycle command when the expected service layout, notification path, write acknowledgement, ELM prompt, or usable adapter identity is missing.

Only the primary channel is used. The alternate characteristic remains exclusive to the maintainer probe and is never auto-probed by the production app.

Motorcycle commands come from a typed, bundled profile. Service writes additionally require the exact motorcycle profile and observed instrument status fingerprint. All complete operations are serialized. A lost response after a write is reported as ambiguous and is never retried automatically.

## TDD increments

1. Add failing tests for profile decoding, schema rejection, and command-channel validation; then implement the typed profile loader.
2. Add failing fragmented ELM-response tests; then implement prompt assembly and echo removal.
3. Add failing CAN/ISO-TP extraction tests; then implement strict response extraction.
4. Add failing dashboard and DTC transcript tests; then implement read decoders and use cases.
5. Add failing service-command and security-key tests; then implement the bounded builders.
6. Add failing DTC-clear and service-reset transcript tests, including response-pending, rejected prerequisites, partial service writes, ambiguous writes, and no automatic retry; then implement both write use cases.
7. Add failing operation-serialization tests; then implement the whole-operation gate.
8. Build the SwiftUI UI and CoreBluetooth transport around the tested core. Keep UUIDs and motorcycle bytes in the bundled profile rather than feature views.
9. Build without signing for the simulator and generic iOS device. Physical installation, launch, primary-channel adapter identification and the corrected dashboard read are complete. v0.1.4 addressed the form issues found in that follow-up test; v0.1.5 adds the five supported localizations without changing diagnostic commands.

## Review checklist

- No feature can write before adapter identity completes.
- No second operation can change the CAN route while another is active.
- DTC clear sends `14FFFFFF` once and waits through response-pending.
- Service reset validates inputs before traffic, fingerprints the cluster before writes, and distinguishes rejected, partially applied, ambiguous, and committed outcomes.
- Disconnect is disabled while an operation is running.
- No VIN, adapter identifier, or serial is stored or displayed.
- The UI says that clearing codes does not repair faults and resetting a reminder does not perform maintenance.
- An unsupported GATT layout fails closed; captured ATT handles are never used.

## Critical/high-risk review

The primary MC-IOS command channel, corrected initialization and dashboard read are physically observed through the production app. The alternate channel remains out of the production app; DTC clear and service modes retain their operation-specific validation gates.

The 2026-08-23 code review closed the identified high-risk implementation gaps: stale DTC authorization after a failed refresh/clear attempt, accepting an ELM prompt before GATT acknowledged the write, accepting diagnostic data without the configured CAN response ID, stale BLE callbacks mutating a newer session, and leaving a diagnostic session active when the app backgrounds. The first live run then exposed a deterministic interoperability bug: `ATWS` returns an `ELM327` banner, not bare `OK`. A regression test now protects that special case while ordinary setup commands still require `OK`. Whole-operation serialization, single-send writes, live instrument fingerprinting and explicit partial/ambiguous outcomes remain mandatory. No known critical or very-high code issue remains; corrected motorcycle behavior still requires physical validation.
