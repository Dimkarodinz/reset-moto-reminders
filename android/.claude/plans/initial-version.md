# Reset Moto Reminders — Android initial research plan

## Original request

Treat `android/` as the Android project. Plan an initial application that connects to the vLinker adapter, reads and clears DTCs, reads and resets the service reminder, and produces intensive diagnostic logging. Review the completed plan for flaws and critical issues before implementation.

## Decision summary

Build a native Kotlin/Jetpack Compose application in this folder. The initial **research version** includes UI, orchestration, parsing, replay tests and logging for all requested workflows:

1. Connect to and identify `vLinker MC-Android`.
2. Read confirmed DTCs.
3. Clear DTCs and verify the resulting count.
4. Read service-reminder information.
5. Reset service distance/date and verify the result.

“Included” does not mean every operation is immediately enabled against a motorcycle. Each operation advances independently from simulated transcript, to adapter test, to live read, to controlled live write. The code is built early; live activation depends on captured evidence and profile gates.

This distinction is necessary because current evidence still has three blockers:

- The captured engine ECU's two-byte SecurityAccess derivation is known and matches six retained exchanges, but it is not yet proven whether DTC clear requires it. The project-app capture proved the default-session DTC count read does not.
- A read-only request for the complete service-reminder state has not been identified; `0D01` cannot yet be assumed harmless or complete.
- The service date/distance request shape is now statically corroborated, but the fixed date suffix semantics and instrument part/software identity remain unknown.

## Implementation status — 2026-08-10

Android v0.4.0 implements the adapter checkpoint plus a local protocol foundation: reproducible Gradle project, generated canonical map assets, typed/fail-closed adapter, ECU and merged motorcycle DTC-map loaders, JSON Schema and cross-map tests, private ordered JSONL logging with pre-persistence redaction, replay transport, prompt framing, serialized ELM commands, map-driven adapter identity/initialization, capability gates, fake-driven connection presentation, bonded-device refresh/selection and the real Android RFCOMM boundary. Captured DTC count/detail decoders, observed/reference/generic DTC description resolution, a map-driven replay DTC reader with zero/nonzero branching and no retries, UDS positive/negative parsing and replay-only service date/distance builders are covered by unit tests. The app is displayed as **Reset Moto Reminders** while its package remains `dev.resetlight`.

Version 0.4.0 adds a debug-only, single-attempt read-only engine capture after `adapter_ready`. It applies only the observed engine routing, reads six identifiers explicitly marked non-sensitive, requests confirmed-DTC count/details, and conditionally tries the observed temporary extended session once when the default DTC read is unavailable. Tests prove that VIN/serial reads, SecurityAccess, DTC clear and all instrument/service commands are absent. The release presentation omits this research action. The APK is installed and launch-verified on the Samsung Android 11 phone, but every current journal contains only `adapter_profile_loaded`; physical Bluetooth/ECU validation remains the next checkpoint. Any discrepancy must first become a sanitized failing transcript test.

## Sources of truth

| Source | Responsibility |
| --- | --- |
| [`../../AGENTS.md`](../../AGENTS.md) | Android ownership, implementation and safety rules |
| [`../../../AGENTS.md`](../../../AGENTS.md) | Project scope, evidence status and compatibility boundaries |
| [`../../VLINK_CONNECTION.md`](../../VLINK_CONNECTION.md) | Android Bluetooth Classic/RFCOMM behavior |
| [`../../../adapter-maps/adaptermap.schema.json`](../../../adapter-maps/adaptermap.schema.json) | Adapter-map schema |
| [`../../../adapter-maps/vlinker-mc-android.adaptermap.yaml`](../../../adapter-maps/vlinker-mc-android.adaptermap.yaml) | Active adapter profile |
| [`../../../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](../../../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml) | Current motorcycle protocol evidence |
| [`../../../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](../../../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml) | Merged motorcycle DTC dictionary with observed and reference-only entries |
| [`../../../dtc-maps/dtcmap.schema.json`](../../../dtc-maps/dtcmap.schema.json) | DTC-map schema |
| [`../../../LEGAL_RESTRICTIONS.md`](../../../LEGAL_RESTRICTIONS.md) | Privacy, clean-room and distribution policy |

Maps remain the protocol source of truth. Do not copy their request bytes into screen code or create a second hand-maintained command database inside Android.

## Product scope

### Initial research version

- Runs on the Samsung Android 11 test phone and remains compatible with the modern Android Bluetooth permission model.
- Guides first-time pairing through Android Settings using PIN `1234` for the observed adapter profile.
- Selects a bonded `vLinker MC-Android`, connects through SPP/RFCOMM, identifies and initializes it.
- Loads and validates versioned adapter and ECU profiles.
- Implements a single serialized ELM command session with prompt-based response assembly.
- Offers DTC and service-reminder screens backed first by fake/replay transports.
- Supports controlled live operations only when their gates below are satisfied.
- Includes an in-app session log viewer and explicit sanitized export.

### Non-goals

- Automatic pairing through hidden/reflection APIs.
- Generic OBD scanning or arbitrary command entry.
- ECU-map/profile importing, editing, exporting or arbitrary command execution. Maps are internal implementation data only.
- ECU coding, calibration, firmware flashing, actuator tests or SecurityAccess brute forcing.
- Automatic recovery that can repeat an ambiguous write.
- Background vehicle monitoring or unattended operation.
- MC-IOS BLE support in the first Android implementation.
- Public-release enablement of an operation merely because it works once in a research build.

## Feature availability model

Use compile-time build types plus runtime profile gates:

| Capability | Fake/replay tests | Research build on hardware | Public release |
| --- | --- | --- | --- |
| Adapter connect/identify | Enabled | Enabled after adapter replay validation | Enabled for validated adapter identities |
| DTC read | Enabled | Enabled after a read-only prerequisite test | Enabled only for validated module profiles |
| DTC clear | Enabled | Enabled only after session/security prerequisites and target identity are resolved | Disabled until writable profile validation |
| Service read | Enabled with modeled data | Disabled until true read semantics are captured | Disabled until validated |
| Service reset | Enabled with captured transcripts | Disabled until identity and all encoded fields are understood | Disabled until writable profile validation |

The research build is locally signed and never distributed. It may expose experimental actions only after an explicit session-level “Research mode” acknowledgement. Build-type checks must be compiled out of release binaries, not implemented as a hidden UI toggle.

## UX flow

### 1. Home / connection

- Show Bluetooth permission and adapter state.
- Show the selected adapter, pairing instructions and a button that opens Android Bluetooth Settings when no bond exists.
- Connect explicitly; do not auto-connect at launch.
- Display progress as named states: connecting, identifying, initializing, ready, disconnecting or failed.
- Show returned ELM/STN identity and selected adapter-map version when ready.

### 2. Motorcycle readiness

- Before diagnostic traffic, show: ignition on, engine off, neutral, kill switch RUN and stable battery/charging guidance.
- Identify the requested module before exposing its operations.
- Display the selected motorcycle profile and whether it is observed, research-enabled or validated.
- Keep unavailable operations visible only if a short blocker explanation helps research; otherwise hide them in release builds.

### 3. DTC screen

- `Read DTCs` is a separate action and always precedes Clear.
- Show raw/display code, status and description status without inventing a manufacturer description.
- `Clear DTCs` shows exactly which codes will be removed, explains that the fault is not repaired and requires a separate confirmation.
- After a positive clear response, automatically perform the count verification as part of the same operation—not as an independent user write.
- Report “DTC memory cleared” only after verification; report remaining codes separately.

### 4. Service screen

- Show current values only when obtained through proven read operations; never present requested or cached values as motorcycle state.
- Let the user choose next-service distance and date only within encodings supported by the validated profile.
- Show current, requested and encoded values in research builds before confirmation.
- After reset, perform proven read-back checks and request an ignition-cycle verification.
- Always state that resetting the reminder does not perform maintenance.

### 5. Logs screen

- List recent sessions with time, adapter, profile, operation and outcome.
- Allow inspection of ordered high-level events and raw/parsed frames in research builds.
- Export only through an explicit user action, with a redaction preview and warning that diagnostic logs may contain vehicle information.

Polished branding is not required before implementation. Use accessible Material 3 components, system light/dark themes, large touch targets and plain language.

## Architecture

Start with one Gradle application module. Keep package boundaries strict without prematurely creating multiple Gradle modules:

```text
android/app/src/main/java/<project>/
  app/                 application setup and dependency wiring
  ui/                  Compose navigation, screens and reusable components
  domain/              use cases, states, safety gates and typed outcomes
  profiles/            internal adapter/operation-profile loading and validation
  transport/bluetooth/ bonded-device discovery and RFCOMM socket lifecycle
  adapter/elm/         command queue, ASCII framing, prompt assembly and init
  diagnostics/         CAN line, ISO-TP and UDS request/response handling
  features/connection/ connection and adapter-readiness flow
  features/dtc/        DTC read, clear confirmation and verification
  features/service/    service read/reset models and guarded workflow
  logging/             structured event journal, redaction, retention and export
```

Dependency direction:

```text
Compose UI -> domain use cases -> profiles/diagnostics/adapter session
                                  -> transport interface
                                     -> Android RFCOMM implementation

Structured logger receives events from every layer but does not control them.
```

Use interfaces for the transport and clock so the same workflows run against fake, transcript-replay and real RFCOMM implementations.

## Core state and command model

Use one connection-owned state machine:

```text
disconnected
  -> selecting_or_pairing
  -> connecting_adapter
  -> identifying_adapter
  -> initializing_adapter
  -> adapter_ready
  -> identifying_vehicle_module
  -> vehicle_ready
  -> executing_operation
  -> vehicle_ready
  -> disconnecting
  -> disconnected

Any active state -> failed -> disconnecting -> disconnected
Executing write -> ambiguous_write_result -> disconnecting -> disconnected
```

Rules:

- Only one command may await an ELM prompt at a time.
- Responses are arbitrary byte chunks; reassemble until `>` and preserve the original bytes.
- Keep command timeout, response-pending timeout and whole-operation timeout distinct.
- A UDS negative response is a diagnostic result, not automatically a transport failure.
- `0x78` response pending extends bounded waiting for the final response; it never resends the request.
- A timeout/disconnect after a write produces `ambiguous_write_result`; no automatic retry is permitted.
- Disconnect invalidates all pending commands and module readiness.

## Map and asset pipeline

Application assets follow this pipeline:

1. Maintain JSON Schemas for adapter, ECU and DTC maps.
2. Validate all three map types before packaging them.
3. Copy validated maps from the repository root into generated Android assets; do not maintain duplicate source copies.
4. Parse into typed Kotlin models that reject unknown schema versions, missing required statuses and inconsistent gates.
5. Preserve map IDs and content hashes in session logs.

Generate sanitized transcript fixtures from retained captures manually. Fixtures may contain only operation-relevant adapter/diagnostic bytes and synthetic identifiers; no MAC address, VIN, serial number or unrelated traffic.

## TDD methodology

Use red–green–refactor for every production behavior:

1. Select one observable behavior small enough to finish in one short cycle.
2. Write the narrowest unit, transcript or UI test that describes it.
3. Run that test and confirm it fails for the expected missing-behavior reason—not because the test is broken.
4. Add the minimum production code needed to pass.
5. Run the focused test, then the complete local suite.
6. Refactor names, duplication and boundaries only while all tests remain green.

Rules:

- No production behavior is “done” if its test was written afterward and never observed failing.
- Every bug fix starts with a test that reproduces the bug.
- Use deterministic fake clocks and scripted transports; tests must not depend on real sleeps, Bluetooth hardware or execution timing.
- Prefer state/output tests over implementation-detail interaction tests. Fake Android/platform boundaries rather than mocking every class.
- A captured behavior enters the codebase as a minimized, sanitized transcript fixture first.
- Hardware tests validate assumptions and discover new evidence; they do not replace automated tests.
- Scaffolding, manifest wiring and short hardware-characterization spikes may precede a useful failing test. Keep the spike isolated, then write the behavioral test before retaining or integrating its code.
- Run the smallest relevant test during red/green cycles and the full unit/transcript suite at every completed step.
- Keep each checklist item below independently reviewable. Do not combine unrelated red tests into one large implementation batch.

## Intensive logging design

Use an ordered append-only JSON Lines event journal in app-private storage. Every event contains, where applicable:

- Session UUID and monotonic sequence number.
- UTC wall-clock timestamp plus monotonic elapsed time.
- Build type, app version, Android version and map IDs/hashes.
- Layer (`ui`, `profile`, `bluetooth`, `elm`, `uds`, `operation`).
- State transition or operation/command name.
- Direction, raw hex bytes, sanitized text and parsed meaning.
- Start/end duration, timeout category and outcome.
- Redaction fields and whether a response was complete at the ELM prompt.

Logging requirements:

- Log before and after every state transition, socket operation, adapter command and diagnostic request.
- Write asynchronously through one ordered logger so logging never blocks Bluetooth reads.
- Retain raw serial bytes in research builds; release exports default to sanitized structured events.
- Redact MAC addresses, VINs, serial numbers and user-entered identifiers before export.
- Tag SecurityAccess exchanges before persistence and redact seed/key payload bytes from both raw and parsed events. “Log every frame” explicitly does not override this rule.
- Rotate by total size and session count; offer manual deletion from the Logs screen.
- Flush critical write-intent, raw request and result events durably enough to diagnose a crash or disconnect.
- Record write operations as `intent -> sent -> response/pending -> final/ambiguous -> verification`.
- Do not attempt to collect system HCI logs from the app.
- Exclude the journal and exports from Android backup and device-to-device transfer.
- Do not request the Android `INTERNET` permission in the initial version; export remains a local user-initiated share action.
- Provide an explicit “Export sanitized diagnostic bundle” action; never upload automatically.

## Small test-first implementation steps

Complete these in order. Each `RED/GREEN` item is one TDD cycle: observe the named test failing, add only the minimum implementation, run the focused test and full suite, then refactor before moving on. Checked items have automated coverage in the current tree; hardware and instrumentation checkpoints remain unchecked until performed on their stated target.

### Milestone 0 — executable project skeleton

- [x] **0.1 Scaffold exception:** create the minimal Kotlin/Compose application, one app module and unit-test source set. Confirm the generated app and empty test task build; add no feature code.
- [x] **0.2 RED/GREEN:** add `ProjectPolicyTest` asserting the application test environment starts; make it pass with the minimum test configuration.
- [x] **0.3 RED/GREEN:** add a build check asserting the release manifest has no `INTERNET` permission; configure the manifest until it passes.
- [x] **0.4 RED/GREEN:** add a build check asserting diagnostic-log paths are excluded from backup/device transfer; add the minimum backup rules.

Checkpoint: a clean checkout builds and runs a deterministic unit suite with no network permission.

### Milestone 1 — map contracts and typed loading

- [x] **1.1 RED/GREEN:** add a schema test that rejects an ECU map without `schema_version`; create the version-3 ECU-map schema until it fails for the intended reason and the real map passes.
- [x] **1.2 RED/GREEN:** add invalid fixtures for unknown ECU and adapter schema versions; make build-time validation reject both.
- [x] **1.3 RED/GREEN:** test that the build copies only validated source maps into generated assets; implement the minimum validation/copy task.
- [x] **1.4 RED/GREEN:** test decoding the MC-Android identity, transport kind and SPP UUID into typed Kotlin values; implement only those adapter DTO fields.
- [x] **1.5 RED/GREEN:** test decoding the engine ECU and instrument module identities/transports; add only the required ECU DTO fields.
- [x] **1.6 RED/GREEN:** test that missing `knowledge_status` or required operation fields fail closed; add typed validation errors.
- [x] **1.7 RED/GREEN:** test that asset content hashes are stable and available to a session; implement map ID/hash calculation.

Checkpoint: malformed, unknown or incomplete maps cannot load, and no protocol constant is required in feature code.

### Milestone 2 — structured logging foundation

- [ ] **2.1 RED/GREEN:** test JSONL serialization of one session-start event with UUID, sequence, wall time and monotonic time; implement the event model and serializer.
- [x] **2.2 RED/GREEN:** submit concurrent events and assert persisted sequence order; implement a single ordered asynchronous writer.
- [x] **2.3 RED/GREEN:** test MAC, VIN and serial redaction in a structured event; implement field-aware redaction.
- [x] **2.4 RED/GREEN:** test that `2701`/`2702` SecurityAccess seed/key payloads never reach disk; add pre-persistence diagnostic redaction.
- [ ] **2.5 RED/GREEN:** exceed the configured size/session limit and assert the oldest journal rotates; implement bounded retention.
- [ ] **2.6 RED/GREEN:** simulate a write intent followed by disconnect and assert durable `intent`, `sent` and `ambiguous` events remain ordered; implement critical-event flushing.
- [ ] **2.7 RED/GREEN:** request an export and assert identifiers are redacted and only selected session files appear; implement local sanitized bundle creation.
- [ ] **2.8 RED/GREEN:** delete a session and assert its journal/export files are gone while other sessions remain; implement scoped deletion.

Checkpoint: fake operations produce deterministic, bounded, private and exportable logs without blocking command execution.

### Milestone 3 — deterministic fakes and transcript runner

- [ ] **3.1 RED/GREEN:** advance a fake monotonic clock and assert scheduled transcript chunks appear without real sleeping; implement `TestClock`/scheduler.
- [x] **3.2 RED/GREEN:** feed a two-chunk inbound transcript and assert order and byte identity; implement the fake byte transport.
- [x] **3.3 RED/GREEN:** script a remote disconnect between chunks and assert the transport closes once; add disconnect scripting.
- [x] **3.4 RED/GREEN:** send an unexpected outbound command and assert the transcript fails with expected/actual bytes; implement outbound assertions.
- [ ] **3.5 RED/GREEN:** script timeout and I/O failures and assert typed transport outcomes; implement error injection.
- [x] **3.6 RED/GREEN:** add the minimized Android adapter-initialization fixture and assert its outbound/inbound bytes and chunk order.
- [ ] **3.7 RED/GREEN:** add minimized DTC read and clear fixtures and assert each contains only the relevant transaction.
- [ ] **3.8 RED/GREEN:** add separate minimized 8,000 km and 10,000 km service fixtures and assert their captured inputs are explicit.
- [ ] **3.9 RED/GREEN:** scan every transcript fixture for MAC/VIN/serial patterns and fail the test when one is introduced.

Checkpoint: every known workflow can be replayed deterministically with chunking, delays and failures.

### Milestone 4 — ELM framing and serialized command session

- [x] **4.1 RED/GREEN:** pass `ATWS` and assert the encoder emits ASCII plus one `0D`; implement command encoding.
- [x] **4.2 RED/GREEN:** feed a response split before `>` and assert no completion until `3E`; implement prompt assembly.
- [x] **4.3 RED/GREEN:** feed two complete responses in one read and assert they are delivered separately; implement buffer remainder handling.
- [x] **4.4 RED/GREEN:** replay echoed `ATWS`/`ATE0` and non-echoed later commands; implement command-aware echo removal without changing raw bytes.
- [x] **4.5 RED/GREEN:** start two commands concurrently and assert the second waits; implement a single FIFO command queue.
- [x] **4.6 RED/GREEN:** omit the prompt and assert command timeout differs from transport disconnect; implement typed timeout outcomes.
- [x] **4.7 RED/GREEN:** disconnect after a read command and assert ordinary failure; disconnect after a write and assert `ambiguous_write_result`; implement intent-aware failure classification.
- [x] **4.8 RED/GREEN:** replay the captured MC-Android identity/initialization sequence with several adversarial fragmentations; implement map-driven adapter initialization.
- [x] **4.9 RED/GREEN:** return an unexpected ELM/STN identity and assert initialization stops before vehicle commands; add identity gates.

Checkpoint: the complete captured adapter session passes without relying on stream read boundaries.

### Milestone 5 — connection state and feature gates

- [ ] **5.1 RED/GREEN:** test every allowed adapter state transition and reject `disconnected -> adapter_ready`; implement the connection reducer/state machine.
- [ ] **5.2 RED/GREEN:** disconnect from each active state and assert pending readiness is cleared; implement cleanup transitions.
- [x] **5.3 RED/GREEN:** test the matrix for fake, research and release builds across unknown/observed/validated profiles; implement one shared capability evaluator.
- [x] **5.4 RED/GREEN:** assert unknown adapter, ECU or instrument identity disables all live module operations; implement fail-closed profile selection.
- [ ] **5.5 RED/GREEN:** assert engine readiness never implies instrument readiness and vice versa; implement per-module readiness state.

Checkpoint: UI and domain code receive capabilities from one tested policy, not scattered booleans.

### Milestone 6 — adapter UI driven by fakes

- [ ] **6.1 RED/GREEN:** Compose-test the disconnected screen for Pair/Select and Connect actions; implement the minimum screen.
- [ ] **6.2 RED/GREEN:** emit connecting/identifying/initializing states and assert exact progress labels and disabled duplicate actions; implement state rendering.
- [ ] **6.3 RED/GREEN:** emit `adapter_ready` and assert adapter identity, map ID and Disconnect are shown; implement the ready state.
- [ ] **6.4 RED/GREEN:** emit permission denied and assert one actionable permission message; implement that error state.
- [ ] **6.5 RED/GREEN:** emit pairing required and assert the Settings/PIN guidance; implement that error state.
- [ ] **6.6 RED/GREEN:** emit timeout, identity mismatch and remote close as separate parameterized cases; implement distinct actionable messages.
- [ ] **6.7 RED/GREEN:** recreate the Compose host/ViewModel and assert the session owner is retained without a second connect call; implement lifecycle-safe ownership.
- [ ] **6.8 RED/GREEN:** cancel during connection and assert one close plus a return to disconnected; implement user cancellation.

Checkpoint: the complete adapter-readiness UX works against fakes before Android Bluetooth code exists.

### Milestone 7 — Android Bluetooth Classic integration

- [x] **7.1 RED/GREEN:** unit-test permission requirements for Android 11 versus Android 12+; implement the version policy without requesting extra permissions.
- [x] **7.2 RED/GREEN:** provide bonded devices including duplicates/unrelated names and assert stable selectable candidates; implement bonded-device mapping/filtering.
- [x] **7.3 RED/GREEN:** test the pairing action targets Android Settings and displays profile PIN `1234`; implement pairing guidance without hidden APIs.
- [x] **7.4 RED/GREEN:** fake active discovery and assert it is cancelled before connect; implement the Android Bluetooth facade call order.
- [x] **7.5 RED/GREEN:** assert RFCOMM socket creation uses the map SPP UUID and no channel constant; implement socket creation.
- [ ] **7.6 RED/GREEN:** simulate a successful blocking connect and assert it runs off the main dispatcher; implement the connection dispatcher boundary.
- [x] **7.7 RED/GREEN:** simulate connect `IOException` and assert one typed failure plus one close; implement failure cleanup.
- [ ] **7.8 RED/GREEN:** cancel a blocking connect and assert one socket close and cancellation outcome; implement cancellation ownership.
- [ ] **7.9 RED/GREEN:** simulate input EOF and assert remote-close handling closes once.
- [x] **7.10 RED/GREEN:** simulate output failure and assert the active command receives the correct read/write ambiguity classification.
- [ ] **7.11 Instrumentation check:** deny and then grant permission; assert the connection action becomes available without recreating the app.
- [ ] **7.12 Instrumentation check:** return from Android Settings after pairing and assert the bonded-device list refreshes on Android 11.
- [ ] **7.13 Hardware checkpoint:** connect the vLinker to the motorcycle diagnostic port for power, reach `adapter_ready`, run the single guarded read-only capture and disconnect; preserve the private journal and convert any discrepancy into a sanitized failing transcript test before fixing it.

Checkpoint: the Samsung Android 11 phone repeatedly reaches adapter readiness with logs matching replay behavior.

### Milestone 8 — transport support for the three scoped operations

- [x] **8.1 RED/GREEN:** load the DTC transport profile and assert the exact ordered vLinker commands come from internal data; implement routing to the known engine-diagnostics address without changing ECU configuration.
- [ ] **8.2 RED/GREEN:** load the service-reminder transport profile and assert its independent vLinker commands; implement routing to the known instrument address without changing instrument configuration.
- [ ] **8.3 RED/GREEN:** switch modules and assert stale headers/filters/readiness are cleared before new setup; implement explicit module transition.
- [ ] **8.4 RED/GREEN:** parse a normal 11-bit CAN line and a normal 29-bit CAN line; implement CAN line parsing.
- [ ] **8.5 RED/GREEN:** parse one valid ISO-TP single frame; implement single-frame extraction.
- [ ] **8.6 RED/GREEN:** parse one valid ISO-TP multi-frame response; implement first/consecutive-frame assembly.
- [ ] **8.7 RED/GREEN:** reject truncated and wrong-sequence ISO-TP fixtures as separate parameterized cases; implement assembly validation.
- [x] **8.8 RED/GREEN:** parse one UDS positive response; implement the positive result type.
- [x] **8.9 RED/GREEN:** parse one UDS negative response; implement service/NRC extraction.
- [x] **8.10 RED/GREEN:** parse `0x78` as pending rather than final failure; implement the pending result type.
- [ ] **8.11 RED/GREEN:** replay read-only identity requests and assert sensitive fields are redacted in logs but retained only as comparison values; implement identity matching.
- [ ] **8.12 Hardware checkpoint:** attempt only the planned read-only prerequisite matrix; if the ECU requires an unknown key, record unsupported and stop without guessing.

Checkpoint: each physical module has independent, identity-bound readiness and typed diagnostic responses.

### Milestone 9 — DTC read

- [x] **9.1 RED/GREEN:** parse the captured zero-count response; implement the DTC count model.
- [x] **9.2 RED/GREEN:** parse the captured one-DTC response while preserving raw code/status and inferred display status; implement one-record decoding.
- [x] **9.3 RED/GREEN:** parse a synthetic multiple-DTC response; implement bounded list decoding.
- [x] **9.4 RED/GREEN:** reject truncated and unsupported-format responses as separate parameterized cases; implement typed parse errors.
- [x] **9.5 RED/GREEN:** assert detail read is skipped for count zero; implement the zero-count branch.
- [x] **9.6 RED/GREEN:** assert detail read is sent once for a nonzero count; implement the nonzero branch.
- [x] **9.7 RED/GREEN:** prefer validated wording, fall back to reference/generic wording and never invent OEM confirmation; implement DTC presentation data.
- [x] **9.8 RED/GREEN:** disconnect during count and assert a read failure with no automatic replay.
- [ ] **9.9 RED/GREEN:** disconnect during detail read and assert a read failure with no automatic replay.
- [ ] **9.10 Hardware checkpoint:** enable live DTC read only if Milestone 8 established a supported read session; repeat and compare sanitized logs to transcripts.

Checkpoint: DTC read is deterministic, non-mutating and accurately represented for a matching profile.

### Milestone 10 — DTC clear

- [ ] **10.1 RED/GREEN:** attempt Clear without a fresh same-session read and assert denial; implement freshness/session gating.
- [ ] **10.2 RED/GREEN:** change module identity after reading and assert Clear becomes unavailable; implement identity binding.
- [ ] **10.3 RED/GREEN:** Compose-test the warning, current code list and explicit confirmation; implement the confirmation flow.
- [ ] **10.4 RED/GREEN:** replay `7F1478` then `54` and assert one request, bounded waiting and final success; implement pending handling.
- [ ] **10.5 RED/GREEN:** replay a negative final response and assert no verification or retry; implement negative outcome handling.
- [ ] **10.6 RED/GREEN:** disconnect after send and assert `ambiguous_write_result`, no retry and forced disconnect; implement the ambiguous path.
- [ ] **10.7 RED/GREEN:** replay positive clear followed by zero-count and remaining-count verification; implement post-clear verification and precise result wording.
- [ ] **10.8 Hardware checkpoint:** keep live Clear disabled until identity/session/security gates are validated; one controlled clear requires separate authorization and before/after evidence.

Checkpoint: every clear path is replay-tested, single-shot and incapable of claiming that a fault was repaired.

### Milestone 11 — service-reminder read and reset

- [ ] **11.1 RED/GREEN:** model odometer, last-service and next-service values as separate optional fields with provenance; implement the service-state model.
- [ ] **11.2 RED/GREEN:** provide only cached/requested values and assert the UI refuses to label them “read from motorcycle”; implement provenance rendering.
- [ ] **11.3 RED/GREEN:** mark `0D01` semantics unknown and assert live service read remains disabled; implement evidence gating.
- [ ] **11.4 Evidence step:** capture or identify a true read-only service-state sequence; minimize it into a failing transcript test before adding a parser.
- [ ] **11.5 RED/GREEN:** once evidence exists, parse each proven field and leave absent fields unavailable; implement only observed decoding.
- [x] **11.6 RED/GREEN:** feed 8,000 km and 10,000 km inputs and assert the two captured command sequences; implement a pure captured-case builder.
- [x] **11.7 RED/GREEN:** reject distance that is negative, overflows or is not a 100 km increment; implement distance validation.
- [ ] **11.8 RED/GREEN:** reject miles input and require explicit kilometre units; implement unit validation without conversion guesses.
- [ ] **11.9 RED/GREEN:** reject invalid calendar dates; implement date validation independent of encoding.
- [ ] **11.10 RED/GREEN:** assert live arbitrary dates remain disabled while the fixed `0x016E0000` suffix semantics are unresolved; implement field-level capability gates.
- [ ] **11.11 RED/GREEN:** Compose-test current/requested/encoded values, maintenance warning and explicit confirmation; implement research confirmation UI.
- [ ] **11.12 RED/GREEN:** replay an unexpected response and assert immediate stop with no retry.
- [ ] **11.13 RED/GREEN:** replay post-send disconnect and assert ambiguity with no retry.
- [ ] **11.14 RED/GREEN:** once a read command is proven, replay immediate verification and implement the verified result.
- [ ] **11.15 RED/GREEN:** model pending ignition-cycle verification separately from immediate verification.
- [ ] **11.16 Hardware checkpoint:** keep live Reset disabled until all bytes, exact instrument identity and read-back are validated; one controlled reset requires separate authorization.

Checkpoint: the complete service UX and captured builders exist without representing guesses as executable support.

### Milestone 12 — logs UI and end-to-end research flows

- [ ] **12.1 RED/GREEN:** render a fake session list ordered newest first with operation/outcome; implement the Logs screen.
- [ ] **12.2 RED/GREEN:** open a session and assert ordered state, command, raw/parsed and redaction events; implement detail rendering.
- [ ] **12.3 RED/GREEN:** preview export and assert redacted fields and bundle contents; wire the tested exporter to Android's user-initiated share sheet.
- [ ] **12.4 RED/GREEN:** run adapter, DTC and service fake flows end-to-end and assert navigation plus final outcomes.
- [ ] **12.5 RED/GREEN:** inject process/lifecycle recreation between non-write states and assert restoration without duplicate commands.
- [ ] **12.6 RED/GREEN:** inject backgrounding during a write and assert bounded completion or ambiguity, never silent cancellation/retry.

Checkpoint: every requested feature is demonstrable with deterministic evidence and inspectable logs.

### Milestone 13 — release hardening

- [ ] **13.1 RED/GREEN:** inspect release capabilities and assert research-only actions/raw identifier views are absent; enforce compile-time separation.
- [ ] **13.2 RED/GREEN:** scan packaged assets for capture names, MAC/VIN patterns, keys and private logs; fail the build on a match.
- [ ] **13.3 Instrumentation:** rotate/recreate the UI in each non-write state and assert no duplicate command.
- [ ] **13.4 Instrumentation:** disable/re-enable Bluetooth and assert typed state recovery without automatic reconnect.
- [ ] **13.5 Instrumentation:** deny permissions and assert accessible explanation/action semantics.
- [ ] **13.6 Instrumentation:** run accessibility checks for labels, focus order, touch targets and warnings.
- [ ] **13.7 Release check:** run the complete unit, transcript, Compose, instrumentation, lint and assembly tasks from a clean checkout.
- [ ] **13.8 Release check:** build from an immutable public tag, sign with the protected key and generate/verify a SHA-256 checksum.

Checkpoint: the public APK exposes only validated capabilities and passes the legal release checklist.

## Test strategy

### Unit tests

- YAML/typed-map decoding and rejection.
- ELM command termination, echo handling, fragmentation and prompt detection.
- CAN/ISO-TP/UDS parsing, negative responses and response pending.
- DTC encoding/display/status handling.
- Service distance/date bounds and captured command-builder examples.
- Feature-gate decisions for every knowledge status/build type combination.
- Redaction and ordered logging under concurrency.

### Transcript tests

- Adapter initialization with original and adversarial fragmentation.
- DTC one-code, zero-code, clear-pending-positive and post-clear verification.
- Service 8,000 km and 10,000 km observed sequences.
- Timeout/disconnect before write, after write and during verification.
- Assertions that no write is resent and no command is sent after identity mismatch.

### Android instrumentation tests

- Permission flows for Android 11 and Android 12+.
- Settings return and bonded-device refresh.
- Compose state restoration without retaining a dead socket in a screen.
- Log viewing, redaction preview, export and deletion.

### Hardware tests

Progress strictly through:

1. Adapter powered from the motorcycle diagnostic port, with the app stopping before any ECU/module session.
2. Motorcycle connected, identity/read-only requests only.
3. DTC reads on a matching profile.
4. One explicitly authorized DTC clear with before/after evidence.
5. Service read-only capture.
6. One explicitly authorized service reset after all encoding blockers are resolved.

No automated test performs a live write.

## Critical review

The plan was reviewed against the retained captures and project rules. The following are critical rather than optional polish:

1. **DTC clear is not ECU programming, but it destroys diagnostic evidence.** An all-groups clear may erase status, snapshots or manufacturer-specific diagnostic records in addition to visible codes. It requires a fresh read, explicit confirmation, single send and post-clear verification.
2. **Service reset can mislead maintenance decisions.** A wrong date/distance may not damage the engine immediately, but it can hide overdue maintenance; read-back and clear wording are required.
3. **SecurityAccess remains a writable-operation gate, not an unknown calculation.** The captured two-byte seed/key transform is now known and independently matches six retained exchanges. The default-session DTC count read works without it; the app must keep the transform non-executable until a scoped write operation, exact module identity and prerequisites are validated.
4. **The service “read” feature is not yet known.** Treating `0D01` as harmless based only on its response could accidentally update last-service state. It stays replay-only until isolated.
5. **The service-reset template remains replay-only.** Its distance/date encoding and fixed suffix are statically corroborated, but the suffix semantics and instrument compatibility identity are unresolved; generated requests must not reach hardware yet.
6. **Module identity is not strong enough for writes.** Model name and CAN address alone cannot enable Clear or Reset across firmware revisions.
7. **Automatic retries are dangerous.** A lost positive response can make a successful write appear failed; reconnecting and repeating may duplicate or alter state.
8. **Intensive logs create privacy risk.** VIN, MAC, serial and raw diagnostic data require app-private storage, retention limits and export-time redaction.
9. **Logging must not change timing.** File writes cannot block the Bluetooth reader or serialized command queue.
10. **Two CAN profiles share one adapter session.** Engine ECU and instrument setup must be isolated; stale filters/headers must never carry from one module operation into another.
11. **Android lifecycle cannot own the socket in a screen.** Rotation, navigation or backgrounding must not silently disconnect or duplicate a command.
12. **User-entered units and dates need strict bounds.** Locale formatting, miles versus kilometres, leap years, timezone and integer overflow must never alter encoded values silently.
13. **Raw logging conflicts with SecurityAccess privacy.** Seed/key frames require targeted redaction before disk persistence, not only during export.
14. **App-private files can still be backed up.** Diagnostic journals and exports must be excluded from Android backup/device transfer, and the initial app should have no Internet permission.

No critical issue requires removing the requested features from the initial codebase. The unresolved items do require progressive live gates; bypassing them would make test results unreliable and could erase evidence or set a misleading reminder.

## Definition of done for the initial Android research version

- The Android project builds reproducibly and installs on the Samsung Android 11 device.
- Every retained production behavior was developed through a recorded red–green–refactor cycle or is explicitly labeled as scaffolding/hardware evidence.
- Adapter and ECU maps are build-time validated and runtime typed.
- Fake and transcript transports drive every requested screen and outcome.
- The physical MC-Android adapter reliably reaches `adapter_ready` and disconnects cleanly.
- Intensive ordered logs can be inspected, sanitized, exported and deleted.
- Diagnostic logs are excluded from Android backup/device transfer and the app has no Internet permission.
- Live DTC read is enabled only if its session path is established.
- DTC clear and service reset code paths exist and pass replay tests, while live execution remains gated until their documented blockers are resolved.
- The UI never represents a cached/requested value as a motorcycle read-back.
- No automatic write retry exists.
- No private capture data or real identifier is committed or packaged.

## Current implementation checkpoint

The local v0.4.0 implementation spans Milestones 0–7 and includes selected replay and guarded research work in Milestones 8, 9 and 11:

1. Executable project skeleton and build-policy tests.
2. ECU/adapter contracts and typed map loading.
3. Intensive logging with privacy tests.
4. Deterministic fake/replay transport and ELM command session.
5. Connection state, feature gates and fake-driven Compose UI.
6. Real bonded-device selection, RFCOMM connection, adapter identity/initialization and disconnect.
7. One debug-only read capture using map-loaded engine routing, non-sensitive identifier reads and DTC count/detail requests, with an allowlist test and single-attempt failure behavior.

The next evidence-producing step combines Milestones 7.13, 8.12 and 9.10 into one controlled visit: reach `adapter_ready`, run the read-only capture once with ignition on and engine off, disconnect, then inspect the private journal. Do not repeat the capture after an error and do not enable DTC clear or service-reminder traffic. Unchecked local criteria in Milestones 0–7 remain backlog before those milestones can be declared complete.
