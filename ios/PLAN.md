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

The MC-IOS GATT command endpoint is not yet proven by a powered-adapter capture. Every live session therefore begins with one bounded adapter-only `ATI` check over the primary `18F0` channel. The app must disconnect and send no motorcycle command when the expected service layout, notification path, write acknowledgement, ELM prompt, or usable adapter identity is missing.

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
9. Build without signing for the simulator and generic iOS device. The first physical-phone test is adapter identification; motorcycle operations run only after that succeeds in the same connection.

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

The critical issue is the unproven MC-IOS command channel. The session-local `ATI` gate and immediate disconnect address it without guessing a fallback channel. The remaining high-risk issues—concurrent route changes, blind write retries, profile mismatch, and partial service writes—are addressed by whole-operation serialization, single-send write semantics, the live instrument fingerprint, and explicit ambiguous/partial outcomes.
