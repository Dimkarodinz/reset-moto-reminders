# General Motorcycle Research Android plan

## Outcome

Build a separate Android application named **Motorcycle Research** with package
`dev.resetlight.research.general`. A tester enters manufacturer, model and model
year, selects a paired vLinker MC-Android, and runs one bounded, read-only first
pass. The app records adapter/protocol evidence plus standard OBD capability and
DTC responses, then exports a sanitized JSONL report for authoring a later,
manufacturer-specific research profile.

This APK does not discover writes and does not claim compatibility. Exact DTC
clear or service-reset candidates must come from a passive capture, public
technical source, open implementation, or controlled CAN observation. They are
tested later through an explicit, exact-allowlisted profile.

## Bounded scan

1. Validate and normalize manufacturer, model and year locally.
2. Create an app-private report; never record the Bluetooth address.
3. Connect only to a bonded device matching the validated MC-Android profile.
4. Identify and initialize the adapter with the existing typed adapter map.
5. Record adapter voltage (`ATRV`).
6. Select automatic protocol detection and keep ELM headers visible.
7. Probe standard supported-PID bitmaps (`0100` through `01A0`), then record the
   selected protocol (`ATDP`, `ATDPN`) after detection has been triggered.
8. Read standard stored, pending and permanent DTC responses (`03`, `07`, `0A`).
9. Probe non-personal Mode 09 capabilities, calibration ID, CVN and ECU name;
   explicitly exclude VIN (`0902`).
10. Continue after `NO DATA`, `?`, or unsupported individual probes; stop on
    transport failure, timeout, cancellation or adapter identity mismatch.
11. Disconnect, flush the terminal event, and expose the JSONL report only
    through an explicit Android share action.

## TDD slices

1. Test manufacturer/model/year normalization, bounds and control characters.
2. Test the finite profile loader and reject duplicate, malformed or prohibited
   requests.
3. Test a semantic runtime policy that admits only adapter metadata plus Modes
   `01`, `03`, `07`, `09` (excluding PID `02`) and `0A`; reject Mode `04`, UDS
   writes/security/routines, monitoring and arbitrary AT commands.
4. Test a full successful transcript and deterministic phase order.
5. Test `NO DATA`/malformed individual probes continuing to later phases.
6. Test cancellation, single active session, terminal report preservation and
   partial-report availability after failure.
7. Test pure screen presentation before implementing Compose UI.
8. Add manifest, FileProvider, no-backup rules, theme, icon and Gradle wiring.
9. Refactor duplicated mechanics only where a stable shared abstraction is
   clearer than two platform-specific implementations.
10. Run main Android, Triumph Research and General Research tests; run lint and
    assemble all Android APKs; install and visually verify without starting a
    motorcycle scan.

## Critical/high review

### Critical: a data file could introduce a write

Every request must pass both schema/loader validation and a semantic runtime
policy. The policy parses diagnostic bytes and rejects clear, SecurityAccess,
generic write, RoutineControl, unknown services, `ATMA`, and VIN. Tests enumerate
adjacent prohibited commands.

### Critical: automatic protocol detection is not universal

The scan uses ELM automatic protocol selection and standard functional OBD
requests only. A failed or silent scan is useful negative evidence, not proof
that the motorcycle lacks diagnostics. No CAN-address sweep or proprietary
header guessing follows.

### High: calibration responses could contain unexpected identity data

VIN is never requested. Existing journal redaction remains active on text and
ASCII encoded in raw hex. Reports stay private until explicitly shared and must
be reviewed before a minimized fixture is committed.

### High: a generic report could be mistaken for write compatibility

Results are labelled as standard-read evidence only. The app contains no DTC
clear, service reset, SecurityAccess or manufacturer-specific write path.

### High: broad probing could overload a marginal bus

The profile is finite, serialized, contains no loops over addresses or DIDs,
uses existing command timeouts/response limits, and sends each request once.

## Completion gates

- [x] Separate package installs beside both existing applications.
- [x] Profile and semantic policy reject every prohibited request in tests.
- [x] Reports contain model metadata, profile hash, raw bounded traffic and a
      terminal outcome without requested VIN, serial or Bluetooth address.
- [x] Main, Triumph and General Android tests pass with zero failures.
- [x] All Android lint and debug APK builds pass.
- [x] APK is installed and its full UI is visually verified on the test phone.
- [x] No hardware scan starts automatically.

Verified 2026-08-17 on Samsung SM-A202F / Android 11: 167 main Android,
25 Triumph Research, 12 General Research and 13 iOS ProbeKit tests passed. All
three Android modules passed lint and debug assembly. General APK v0.1.0
(`versionCode 1`, SHA-256
`406dc36a93ad14713434b613a2102c77b8c201d4008d48cfb8011965c18c06d2`)
was installed, cold-launched and scrolled through without starting a scan.
