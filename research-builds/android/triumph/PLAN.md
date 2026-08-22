# Triumph Research Android plan

## Outcome

Build a separate, installable Android application named **Triumph Research**. A tester enters the Triumph model and model year, selects a paired vLinker MC-Android adapter, and starts one bounded research session. The application connects, records adapter and protocol evidence, probes the known Triumph Keihin engine route and Tiger TFT instrument route, reads DTC information when available, optionally validates the two known writes, and exports a privacy-sanitized JSONL report.

The read phase is always first and remains bounded/read-only. The user may explicitly select a service reset in the unit currently shown on the motorcycle, DTC clear, or both before starting. A matching read is required before either write sequence is eligible. Results remain per-motorcycle research evidence, not a family-wide support claim.

## Scan contract

The one-go scan performs these bounded phases in order:

1. Validate model/year input and create a new app-private report.
2. Connect to the selected bonded vLinker over the proven RFCOMM transport.
3. Identify and initialize the adapter from the validated adapter map.
4. Record adapter voltage and active-protocol descriptions with `ATRV`, `ATDP`, and `ATDPN`.
5. Apply the known Triumph engine route.
6. Run every non-sensitive identifier read packaged by the ECU profile. Explicitly exclude VIN and serial-number identifiers.
7. Read confirmed-DTC count; read details only when the count is non-zero. If the default-session count is unavailable, make one observed `1003` extended-session attempt and retry the count once.
8. Apply the known Tiger TFT instrument route and read only the observed `5E01` status and `0D01` odometer values.
9. Classify DTC-read, DTC-clear-candidate, service-read, and service-reset-candidate evidence independently, with all precursor traffic already journaled.
10. If explicitly selected and eligible, validate the service reset first. The tester selects the dashboard unit and enters the exact currently stored interval/date because no known read exposes them. Write the smallest representable change (`+100` in that unit, `+1 day`) with the exact `33xx` km or `34xx` miles template plus `5Cxx`, and record positive echoes or rejection.
11. While the connection remains healthy, write the entered previous interval/date back after either a confirmed temporary commit or an explicit rejection. Record restoration as restored/rejected independently. If delivery becomes ambiguous or the transport fails, stop without another write and mark restoration unknown.
12. If explicitly selected and eligible, reapply the engine route and validate the exact extended-session/SecurityAccess/DTC-clear sequence. Verify the remaining DTC count. DTC evidence cannot be restored.
13. Disconnect, flush the terminal event, and expose the JSONL report through an explicit Android share action.

No route fuzzing, arbitrary DID scan, generic `WriteDataByIdentifier`, `RoutineControl`, alternative security-key probing, alternative service encoding, or bus monitoring is part of the session. Optional writes are exact allowlisted sequences, not discovery scans.

## TDD implementation steps

Each production step starts with a focused failing test, followed by the smallest implementation and a refactor pass.

1. Add tests for normalized model/year input and bounded validation.
2. Add policy tests proving the read allowlist permits the map-derived reads and rejects VIN, serial, SecurityAccess, DTC-clear, generic write/routine, and service-reset commands.
3. Add privacy tests covering plain text plus ASCII-encoded raw-hex VIN, serial, MAC, seed, and key material.
4. Add scanner transcript tests for the full successful engine/DTC/instrument sequence and capability summary.
5. Add scanner tests proving an unavailable identifier does not prevent later DTC/instrument probes.
6. Add scanner tests for one bounded extended-session fallback, non-zero DTC detail reads, malformed responses, and no-response outcomes.
7. Add session-owner tests for input rejection, single active session, deterministic disconnect, terminal report preservation, and cancellation.
8. Add validation tests for explicit write input, selected-unit distance/date bounds, and disabled-operation fields.
9. Add a second policy with positive tests for only the exact dynamic key, DTC-clear, distance and date requests plus negative tests for all adjacent write families.
10. Add scripted same-session tests proving reads finish before service reset, km and miles both preserve their selected unit through the +100/+1-day test and restoration, explicit test/restore rejections are distinct, ambiguous delivery sends no follow-up write, service reset precedes DTC clear, response-pending never resends DTC clear, precursor mismatch sends no write, and both operations are recorded independently.
11. Implement the Compose input/device/progress/result/share screen after its pure presentation state is tested. Write selection defaults off and requires acknowledgement.
12. Add Android manifest, FileProvider, no-backup rules, theme, icon, app package, and Gradle wiring.
13. Run the research module tests after every slice, then the main Android suite, lint, debug APK builds, iOS Swift tests, and repository map/schema checks.
14. Install the research APK alongside the main application and launch-verify it on the connected phone. No motorcycle scan is run automatically.

## Critical and high-risk review

### Critical: automatic discovery could accidentally send a write

**Resolution:** the initial scan and optional write validation use separate runtime policies. The read policy rejects every write-capable service. The write policy admits only the exact mapped setup/verification reads and four write shapes: derived two-byte key, exact DTC clear, one-byte interval under the captured km or miles service prefix, and structured date. Unit tests enumerate allowed and adjacent prohibited commands.

### Critical: raw-hex journals could bypass text redaction

The existing journal redacts readable VIN/security text, but raw ELM bytes are stored as ASCII encoded into hexadecimal. A VIN or seed/key response could therefore evade a text-only check.

**Resolution:** strengthen the shared redactor to inspect both the original hexadecimal and safely decoded ASCII representation. The app never sends VIN or serial requests. SecurityAccess traffic is present only when DTC clear is explicitly selected; seed/key material is redacted. Tests cover both journal fields.

### High: a shared engine supplier could be mistaken for service-reset compatibility

**Resolution:** engine/DTC and instrument/service evidence are classified independently. A result is never labelled `supported`. Service reset requires both known TFT responses and the observed status; DTC clear requires a decoded count response. Even a validated write applies only to the entered motorcycle/year and still needs maintainer review before a main-app profile is added.

### High: one failed probe could waste the entire motorcycle visit

**Resolution:** diagnostic-level `NO DATA`, malformed payloads, and individual unsupported identifiers are recorded and the scan continues to the next independent probe. Transport disconnects, timeouts, permission errors, and adapter-identity mismatches terminate the scan because subsequent evidence would be unreliable.

### High: a scan could overload the bus or grow an unbounded report

**Resolution:** the command list is finite, serialized through one ELM session, and has no address/DID loops. Existing per-command timeouts and 64 KiB response limits remain active. The app never restarts or retries a write sequence after disconnect, timeout, or ambiguous delivery. The only read retry is the explicit DTC-count retry after the observed extended-session transition.

### High: a valid write can alter data even on an otherwise unsupported motorcycle

**Resolution:** write tests default off, show the exact consequences, require explicit acknowledgement, and run only after live precursor reads match. DTC evidence is logged before clear. Service input is restricted to the known one-byte range and a date from today through two years ahead. The tester must deliberately select the unit currently shown on the dashboard; the app performs no conversion. This does not make the experiment harmless; an accepted service value persists and DTC clear erases evidence.

The service test uses the minimum known delta and restores the tester-entered baseline immediately. Restoration is not guaranteed after transport loss or ambiguous delivery, and the app never hides that limitation or retries automatically.

### High: report sharing could expose vehicle or phone identifiers

**Resolution:** reports remain in app-private storage until the tester explicitly shares one. MAC addresses are never journaled, model text is sanitized, VIN/serial reads are excluded, sensitive patterns are redacted from text and raw bytes, Android backup is disabled, and FileProvider exposes only the report directory.

### High: cancellation or screen recreation could leave the adapter connected

**Resolution:** ownership lives in the `Application`, not a composable. The session has one job, closes the RFCOMM transport in `finally`, writes a terminal outcome, and preserves partial evidence for export.

### High: a phone-only installation could be misreported as motorcycle validation

**Resolution:** installation/launch verification is reported separately. A report counts as motorcycle evidence only when it contains adapter-ready, module probe, and terminal scan events from a powered adapter connected to a motorcycle.

## Completion gates

- [x] The research app has a different application ID and can coexist with Reset Moto Reminders.
- [x] Separate read/write allowlists have automated positive and negative tests for every admitted operation family.
- [x] Reports contain model/year, profile hashes, probe outcomes, raw protocol traffic, optional write results, and a terminal summary, while containing no requested VIN, serial, MAC, seed, or key.
- [x] All new and existing tests pass; Android lint and both debug APK builds pass.
- [x] The APK installs and cold-launches on the connected Android phone without replacing the main app. The foreground Compose screen, model/year input, paired-adapter selection, disabled-until-valid scan action, scrolling layout and system/navigation-bar contrast were visually verified. No motorcycle scan was attempted.
