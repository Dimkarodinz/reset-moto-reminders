# Reset Moto Reminders project guide

## Objective

Build an open-source Android/iOS application with three motorcycle-facing features:

1. Read diagnostic trouble codes (DTCs).
2. Clear DTCs only after displaying them and receiving explicit confirmation.
3. Reset the service reminder by setting the next-service date and distance.

The first target is a 2021 Triumph Tiger 900 GT Pro connected through a vLinker MC+. Do not expand the product into ECU coding or calibration, firmware flashing, actuator tests, arbitrary diagnostic scanning, emissions changes, immobilizer work or security-access brute forcing.

The application exposes only the three operations above. ECU-map files are an internal command/profile database used to implement those operations across supported motorcycles; importing, editing, configuring or executing maps is not a user-facing feature. Never describe adapter CAN-header/filter setup as “configuring the ECU”: it only routes one of the three allowed diagnostic operations to its known target module and does not alter ECU configuration.

## Platform strategy

Build two native applications, sequentially:

1. Android first, using Kotlin, Jetpack Compose and Android's Bluetooth APIs.
2. iOS later, using Swift, SwiftUI and CoreBluetooth after the MC-IOS command channel is proven.

Do not start with Flutter/Dart or another shared UI runtime. The product has a small UI, while its critical transport code is platform-specific: Android currently uses Bluetooth Classic RFCOMM and iOS uses BLE GATT. A cross-platform layer would still depend on different native plugins or Kotlin/Swift bridges, adding another failure and debugging boundary around safety-sensitive I/O.

Share contracts rather than Bluetooth implementation code: adapter/ECU maps, schemas, sanitized transcripts, state names, safety rules, UI wording and equivalent test cases. Keep both apps behind the same conceptual adapter interface, but allow platform-native lifecycle and error handling. Reconsider runtime code sharing only after the Android vertical slice works and the iOS transport has been captured and validated. Platform choice does not change signing or store-distribution rules.

## Sources of truth

| Subject | File | Responsibility |
| --- | --- | --- |
| Motorcycle protocol | [`ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml), [`ecu-maps/ecumap.schema.json`](ecu-maps/ecumap.schema.json) | Module identity, CAN/ELM configuration and observed diagnostic operations, validated by the ECU-map schema |
| DTC dictionary | [`dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml), [`dtc-maps/dtcmap.schema.json`](dtc-maps/dtcmap.schema.json) | One Tiger 900 GT Pro / Triumph Keihin ECU map containing the observed exact entry and clearly separated unvalidated reference fallbacks |
| Adapter-map contract | [`adapter-maps/adaptermap.schema.json`](adapter-maps/adaptermap.schema.json) | Required schema-version-2 interface for every adapter map |
| Android adapter | [`adapter-maps/vlinker-mc-android.adaptermap.yaml`](adapter-maps/vlinker-mc-android.adaptermap.yaml) | Captured Bluetooth Classic/RFCOMM transport and adapter initialization |
| iOS adapter | [`adapter-maps/vlinker-mc-ios.adaptermap.yaml`](adapter-maps/vlinker-mc-ios.adaptermap.yaml) | Captured BLE/GATT profile and explicitly unverified command channel |
| Android application | [`android/README.md`](android/README.md), [`android/AGENTS.md`](android/AGENTS.md), [`android/.claude/plans/initial-version.md`](android/.claude/plans/initial-version.md), [`android/VLINK_CONNECTION.md`](android/VLINK_CONNECTION.md) | Build/test instructions, Android ownership, reviewed plan and RFCOMM guidance |
| Triumph research application | [`research-builds/android/triumph/README.md`](research-builds/android/triumph/README.md), [`research-builds/android/triumph/AGENTS.md`](research-builds/android/triumph/AGENTS.md), [`research-builds/android/triumph/PLAN.md`](research-builds/android/triumph/PLAN.md) | Separate one-session Triumph compatibility collector, privacy boundary, test procedure and reviewed implementation plan |
| General research application | [`research-builds/android/general/README.md`](research-builds/android/general/README.md), [`research-builds/android/general/AGENTS.md`](research-builds/android/general/AGENTS.md), [`research-builds/android/general/PLAN.md`](research-builds/android/general/PLAN.md) | Separate bounded standard-OBD read collector for unmapped motorcycle families; never authorizes writes |
| iOS application | [`ios/AGENTS.md`](ios/AGENTS.md), [`ios/VLINK_CONNECTION.md`](ios/VLINK_CONNECTION.md) | iOS ownership/rules plus CoreBluetooth, GATT layout and pending adapter-only proof |
| Publication policy | [`LEGAL_RESTRICTIONS.md`](LEGAL_RESTRICTIONS.md), [`LICENSE`](LICENSE) | Clean-room, privacy, branding, distribution and release rules; GPLv3 source license |
| Private evidence | [`logs/2026-08-07/README.md`](logs/2026-08-07/README.md), [`logs/2026-08-08/README.md`](logs/2026-08-08/README.md), [`logs/2026-08-09/README.md`](logs/2026-08-09/README.md), [`logs/2026-08-10/README.md`](logs/2026-08-10/README.md) | Capture inventory, provenance and integrity hashes |
| Journal analysis | [`tools/journal-analyze.py`](tools/journal-analyze.py) | Offline comparison of a pulled capture journal against the adapter, ECU and DTC maps |

The entire `logs/` tree is private evidence. Never publish or commit bugreports, dumpstate archives, complete HCI captures, screenshots containing identifiers or unrelated captured traffic. Follow the legal release checklist before preparing public source, a website or a binary.

Keep this file as the project index, safety policy and current research status. Put protocol values in maps and platform implementation details in the Android/iOS connection guides. Split research history into `docs/research/` only after multiple motorcycles make this file difficult to navigate, and retain links here.

## Current implementation checkpoint — 2026-08-11

- Android research build **v0.4.0** (`versionCode 5`, debug APK SHA-256 `fb4bb76d26829bf0708cf32cb2a79eca4667dcadf72e66df5876bfb7ba5f1b6c`) completed the **first project-app motorcycle capture** at 21:44 local time with the vLinker powered from the Tiger 900 diagnostic port, ignition on and engine off.
- The journal (`logs/2026-08-10/session-1786391055629.jsonl`, analyzed with [`tools/journal-analyze.py`](tools/journal-analyze.py), zero findings) shows: RFCOMM connection to `AdapterReady`, ELM/STN identities matching the adapter map, all 24 outbound commands inside the read-only allowlist, all 11 engine transport commands accepted, all six non-sensitive identifier reads byte-identical to the third-party observations, and `190108` → `59010C000000` (zero confirmed DTCs) answered **in the default session** — no `1003`, no SecurityAccess.
- Consequences recorded in the maps: the adapter map marks `independent_replay` validated; the engine transport and default-session DTC count read are project-app replay validated. The DTC **detail** read (`190208`), the extended-session branch, DTC clear and everything about the instrument module remain unexercised by the project app.
- The 2026-08-10 capture found zero DTCs, so a nonzero count/detail validation still requires a future capture with a present fault. DTC clear and service reset were subsequently built (v0.6.0, below); the **service reset was hardware-validated (km mode) on 2026-08-13**, while DTC clear remains hardware-unvalidated behind its gates.
- Follow-up (Android **v0.5.0**, `versionCode 6`, test/lint/APK-verified only): the default-session confirmed-DTC read is now a mainline user-facing feature that decodes codes against the DTC dictionary; the read-only engine capture and a new first-contact instrument read (observed `5E01`/`0D01` only) stay behind `RESEARCH_BUILD`. All three are reads — no SecurityAccess, no writes. A nonzero decoded DTC read and the instrument path are still unvalidated against a motorcycle.
- **Harvest-sweep build — Android v0.6.0 (`versionCode 7`, test/lint/APK-verified only, 2026-08-11):** built to gather everything for one more trip and to validate the two write operations in the same session. Adds, all behind `RESEARCH_BUILD`:
  - **DTC clear** (`DtcClearService`): extended session → `2701` seed → derived `2702` key → `14FFFFFF` → verify `190108` count. The seed/key transform (`EngineSeedKeyDerivation`) is now **executable** (see the scoped exception below), reached only after an explicit in-app confirmation.
  - **Service-reminder reset** (`ServiceReminderResetService`): replays the observed instrument bytes (`5E01` → `0D01` → `33xx` distance → `5Cxx…` date), confirming success by matching the date-commit echo. Gated by `ClusterFingerprintGate`, which **fails closed** before any write byte: it requires the exact captured motorcycle profile, the observed instrument transport route, and the live `5E01` status matching the validated `043`. There is no readable instrument part number/software version, so this fingerprint substitutes for a module-identity match.
  - Both write flows require an explicit arm→confirm action in the UI, run only when the packaged write profiles are present, and are absent from any release build (`writesEnabled = BuildConfig.RESEARCH_BUILD`, enforced in `AdapterSessionOwner` as well as the UI).
  - **Nothing here is hardware-validated yet.** This build exists to be run on the motorcycle: harvest a healthy-state journal + validate service reset, then (after triggering a fault by unplugging a front ABS/wheel-speed sensor with ignition off) read + clear a real DTC. No probes or speculative requests are sent — writes are gated on observed data only.

### 2026-08-12 trip — first run of the v0.6.0 harvest build (partial; four app bugs found and fixed)

- Journal `logs/2026-08-12/session-1786454940306.jsonl` (analyzed clean after the analyzer learned the instrument allowlist; see `logs/2026-08-12/README.md`). The engine capture re-validated: identity match, all identifier reads byte-identical, DTC count 0 in the default session.
- **New live evidence — instrument cluster responded to the project app for the first time**: `5E01` → payload `DE 30 34 33` (status ASCII `"043"`, the validated fingerprint) and `0D01` → `8D 01 00 AE 9C` (odometer `0xAE9C` = 44700 km). Payloads match the third-party observations byte-for-byte. Also new: with `ATH1`+`ATCAF0` live responses arrive framed — engine `18DAF1D5 <ISO-TP PCI> <payload> AA…`, instrument `704 <8 raw data bytes>`.
- Four app bugs found (all fixed in **v0.6.1**, `versionCode 8`, with transcript tests from this journal): (1) every v0.5/0.6 decoder expected the map's payload-only format and rejected live framed responses — fixed by `CanResponseExtractor`; (2) DTC read/clear did not re-apply the engine route after an instrument read, so `190108` got NO DATA on the stale 11-bit route; (3) any operation error tore down the healthy Bluetooth session (observed three times) — now only transport-fatal causes disconnect; (4) service-reset input validation threw pre-I/O instead of blocking with a message. The service reset therefore **never reached the wire** and stays hardware-unvalidated, as do DTC clear, the detail read and a nonzero count.
- The planned **DTC-provocation experiment was not performed** this trip; it remains pending for a future trip (unchanged plan: unplug a front wheel-speed sensor with ignition off, read, optionally clear, restore).

### 2026-08-13 trip — "flip the dash, capture twice" experiment (milestone 12 done; miles write rejected)

- Journal `logs/2026-08-13/session-1786622057643.jsonl`. The user reset the service reminder with the dashboard in km (succeeded), physically switched the dashboard to miles, and re-ran the identical reset at the same odometer (the reset the user reported as "an error"). This ran the milestone-12 experiment and gave the milestone-11 answer — with a bonus write-path result because the resets went past a read-only capture.
- **First hardware-validated service reset by the project app.** In km mode the full sequence committed on real hardware: `5E01`→`043`, `0D01`→`0xAED4` (44756 km), distance `334E` (78×100 = 7800 km) → `B34E…` positive echo, date `5C1B080D016E0000` → `DC1B080D…` echo. This retires the "service reset never reached the wire / hardware-unvalidated" caveat for the km path.
- **The unit model is mixed, not a clean Case A/B:**
  - *Reads are canonical.* The `0D01` odometer raw was `0xAED4` (44756) and `5E01` status `"043"` in **both** the km and miles captures — byte-identical. The dash toggle is display-only for reads, so reads are unit-independent (milestone 11 Case A holds for reads).
  - *The interval write is unit-dependent.* The identical `334E` write accepted in km mode was **rejected** in miles mode: `B3 FFFFFFFFFFFFFF` — positive service byte, value replaced with all-FF instead of the `4E` echo. The app correctly detected the non-echo, blocked with `SERVICE_RESET_REASON_DISTANCE_REJECTED`, and never sent the date. This is expected fail-closed behavior, **not an app bug** — the "error" the user saw was the cluster genuinely refusing a km-scaled write while in miles mode.
- No successful miles write was captured, so the miles interval scaling is still unknown and is not guessed. Fix shipped in **v0.6.3**: the rejection message now names the likely cause (dashboard in miles) and the remedy (switch to km and retry); the ecu-map records the mixed unit model and both 2026-08-13 observations; transcript tests replay both the km commit and the miles rejection. Miles support stays blocked pending a successful miles-write capture.
- The **DTC-provocation experiment was again not performed** this trip; still pending.

### 2026-08-17 — separate Triumph compatibility collector

- A second Android application now lives at `research-builds/android/triumph` with package `dev.resetlight.research.triumph`, so it installs alongside Reset Moto Reminders rather than replacing it.
- Its one-go scan asks for Triumph model/year, connects through the validated MC-Android profile, records adapter metadata, runs all packaged non-sensitive engine identifier reads, reads confirmed DTC count/details, and reads the known TFT status/odometer route. Engine/DTC and instrument/service evidence are classified independently.
- Its read phase uses an exact/semantic `ResearchCommandPolicy`: VIN/serial identifiers, SecurityAccess, DTC clear, `0x2E`, `0x31`, `0x33`, `0x5C`, OBD clear and unbounded bus monitoring are rejected. The only non-read diagnostic transition is one observed `1003` fallback when the default-session DTC count is unavailable.
- **v0.3.0 provides explicit same-session write validation.** After the complete read phase, the tester may select the exact known km service-reset sequence, DTC-clear sequence, or both. Service validation requires user-entered current interval/date, temporarily writes the minimum known change (`+100 km`, `+1 day`), verifies echoes, then writes the entered baseline back and records restoration independently. `ResearchWriteCommandPolicy` permits only mapped setup/verification reads plus the derived two-byte key, exact clear request, one-byte km distance request and structured date request. Eligibility requires the corresponding live read candidate; the UI defaults writes off, leaves baseline fields empty, and requires acknowledgement.
- Run service test/restoration before DTC clear so all original read/DTC evidence is already retained and the later clear can reapply the engine route. After an explicit service rejection with a healthy connection, still attempt restoration; if restoration itself is rejected, record it and continue to separately selected DTC clear. A timeout, disconnect or ambiguous service write marks restoration unknown, stops the session and must never trigger an automatic follow-up write or retry.
- `dtc_clear_candidate`, `service_reset_candidate`, and even a validated optional write apply only to the entered motorcycle/year. They do not create a main-app profile or establish family-wide compatibility without review.
- JSONL reports are app-private until explicitly shared, survive cancellation/failure, omit Bluetooth addresses, never request VIN/serial, and apply text plus ASCII-in-raw-hex redaction. This review fixed the shared redactor so raw ELM bytes cannot bypass VIN/security privacy checks.
- v0.3.0 (`versionCode 3`) supersedes the earlier collector and is installed alongside the main app on the Samsung SM-A202F / Android 11 phone. Package/version, cold launch, scrolling, empty current-value fields, the +100 km/+1 day/restore explanation, DTC-clear control, ambiguous-restore warning, acknowledgement and disabled-until-ready action were visually verified without starting a motorcycle session. Verification: **166** main Android tests, **25** Triumph Research tests and **13** iOS `ProbeKit` tests pass; both Android modules pass lint and assemble.

### 2026-08-17 — general motorcycle read-only collector

- A third Android application lives at `research-builds/android/general` with package `dev.resetlight.research.general`. It asks for manufacturer/model/year and runs one finite standard-OBD read through the validated MC-Android transport.
- The profile contains 18 serialized requests covering adapter/protocol metadata, automatic protocol selection, supported PID pages, stored/pending/permanent DTCs and non-identifying Mode 09 information. Protocol identity is recorded after the first standard requests trigger automatic detection. `GeneralResearchCommandPolicy` independently rejects VIN, DTC clear, SecurityAccess, writes, routines, monitoring, unknown AT commands and undeclared requests.
- Reports are app-private JSONL until explicitly shared. They contain no requested VIN/serial or Bluetooth address, retain the raw bounded traffic after redaction, continue after unsupported individual probes, and preserve a partial report on terminal failures.
- This collector cannot discover a safe service-reset or DTC-clear write by itself. Its result is input to later public-source/passive-capture research and an exact manufacturer profile; it never guesses or executes candidates.
- General Research v0.1.0 (`versionCode 1`, APK SHA-256 `406dc36a93ad14713434b613a2102c77b8c201d4008d48cfb8011965c18c06d2`) is installed alongside both existing packages on the Samsung SM-A202F / Android 11 phone. Its full scrollable UI, visible navigation buttons, automatically selected bonded vLinker and disabled-until-valid start action were visually verified without starting a hardware scan. Verification: **167** main Android, **25** Triumph Research, **12** General Research and **13** iOS ProbeKit tests pass; all three Android modules pass lint and assemble.
- Phone installation/launch verification and motorcycle evidence are separate. Do not call this app hardware-validated on another Triumph until a powered-adapter report contains `adapter_ready`, module probe events and a terminal `research_session_finished`.

### Captured engine SecurityAccess derivation — evidence only

Private interoperability analysis of third party OEM linker app identified the two-byte seed/key transform used on the captured Tiger engine-ECU path. The APK and decompiled material were kept outside the repository and deleted after analysis; no implementation code or third-party asset was copied.

Treat the response to `27 01` as an unsigned 16-bit big-endian seed. The corresponding `27 02` key is:

```text
key = (seed * 0x4B48) mod 0x10000
```

Send the result as two big-endian bytes. This independently written description matches all six retained engine-ECU exchanges exactly: `188B→A018`, `871C→33E0`, `FBBD→2C28`, `2A7B→FB98`, `7108→2240`, and `89DE→D070`. It is a small deterministic seed/key transform, not general-purpose encryption.

Scope it only to the captured 2021 Tiger 900 GT Pro engine ECU/address path. Do not infer compatibility from `Keihin`, Triumph, model name or CAN address alone. The 2026-08-10 project-app capture already proves default-session DTC count reading does **not** need SecurityAccess.

**Scoped execution exception:** the maintainer has authorized this transform to execute only inside `EngineSeedKeyDerivation`, only on an explicitly confirmed **engine-ECU DTC-clear path**, and never for an ordinary read, the instrument path, or an ungated release path. In the main Android app this remains behind `BuildConfig.RESEARCH_BUILD` and the exact profile gate. The separate Triumph Research v0.3.0 collector may execute it after the known engine route returns a decodable DTC count and the tester explicitly selects and acknowledges DTC clear. `ResearchWriteCommandPolicy` rejects every other key shape and use. `LEGAL_RESTRICTIONS.md` records the same exception. Whether clear actually requires SecurityAccess is still an open hardware question.

## Data boundaries

### Internal motorcycle operation profiles (`*.ecumap.yaml`)

Use this hierarchy:

```text
motorcycle.modules.<module>.transport
motorcycle.modules.<module>.commands.<operation>
```

These internal profiles may contain motorcycle/module identity, diagnostic transport, observed ELM configuration, exact requests/responses, decoded fields and command-local uncertainty for DTC read, DTC clear and service-reminder reset only. They are implementation data, not an ECU-configuration feature. They must not contain capture paths, phone details, research plans, global safety/legal policy, citations or broad related-model claims.

Preserve the distinction between `observed`, `inferred`, `confirmed`, `pending` and executable/validated behavior. Never generalize a captured value into a template until controlled captures prove its encoding. Every ECU map must validate against [`ecu-maps/ecumap.schema.json`](ecu-maps/ecumap.schema.json); the Android build packages the schema and refuses unknown schema versions, and `ProfileSchemaTest` enforces it in the unit suite.

### DTC dictionaries (`*.dtcmap.yaml`)

Keep user-facing DTC messages separate from motorcycle command profiles. Within a motorcycle DTC map, keep observed/validated `entries` distinct from `reference_entries`. Resolve a motorcycle-specific exact/base code first, then a reference-only base code, then a generic subsystem message; malformed values use the neutral invalid-code fallback. Never copy third-party wording. Factual code meanings may be stored only as short project-authored paraphrases with source version and evidence status. Reference-only entries do not establish motorcycle compatibility or enable an operation.

### Adapter maps

All adapter maps use schema version 2 and expose the same interface:

```text
adapter.identity
adapter.platform_support
adapter.transport.{discovery,channel,framing}
adapter.operations.{connect,identify_adapter,initialize_adapter,disconnect}
adapter.integration_boundary
adapter.validation
```

Keep transport-neutral data at those paths and put RFCOMM-, ATT- or GATT-specific evidence under the nearest `details` object. Keep unproven platforms and operations present with an explicit `knowledge_status`. Validate every adapter map against the shared schema after editing it.

Adapter maps own Bluetooth discovery/connection, adapter channel selection, ASCII framing, response reassembly and adapter identity/initialization. ECU maps own ELM protocol selection, CAN headers and filters, ISO-TP/UDS and motorcycle-module requests.

## Known compatibility boundary

- Engine diagnostics used a Triumph Keihin ECU at request CAN ID `0x18DAD5F1` and response CAN ID `0x18DAF1D5`.
- The service reminder belongs to the Tiger 900 TFT instrument, not the engine ECU. Its observed request CAN ID is `0x701`; the response CAN ID is `0x704`.
- Exact engine-ECU and instrument part numbers and software versions remain unknown. This prevents a production-safe writable compatibility profile.
- First-generation Tiger 900 variants are research candidates, not declared compatible models. MY 2024-onward Tiger 900 models form a separate boundary.
- A shared supplier or the name `Keihin` does not establish compatible addresses, sessions, security methods or commands.

Classification references: third party ECU linker and [Triumph Technical Bulletin 222](https://static.nhtsa.gov/odi/tsbs/2020/MC-10175257-9999.pdf).

## Verified evidence and present limits

| Area | Current evidence | Limit |
| --- | --- | --- |
| MC-Android transport | SPP UUID `0x1101`, RFCOMM channel 1, encrypted link, ELM327 v2.2 and STN1151 v4.3.2 observed in multiple third-party sessions **and validated by the project app on 2026-08-10** (connection, initialization, identity match, clean disconnect) | Only one project-app session exists; reconnection robustness and error paths are untested on hardware |
| MC-IOS transport | Advertisement, complete GATT layout and notification enablement captured once | No `ATI` write/response; command channel, write type and fragmentation remain unconfirmed |
| DTC read | Confirmed-DTC count/read observed twice by third-party tools; **project app executed the count read in the default session on 2026-08-10** (zero confirmed DTCs, no `1003`, no SecurityAccess) | The detail read (`190208`) and a nonzero count have not been executed by the project app; extended-session fallback is implemented but unexercised on hardware |
| DTC clear | One successful clear observed: response pending, final positive response, then zero-count verification; captured engine seed/key derivation is known and matches six retained exchanges. Implemented in v0.6.0 (`DtcClearService`) with the derivation executable in the research build behind the fingerprint gate + explicit confirm | Whether clear needs SecurityAccess, plus module-identity validation, remain pending; the implementation is untested against a motorcycle and disabled in release builds |
| Service reminder | **Project app committed a full km-mode reset on hardware 2026-08-13** (7800 km interval `334E`→`B34E`, date `5C1B080D…`→`DC…`); earlier 10,000/8,000 km resets captured by third-party tools; distance uses 100 km units. Implemented in v0.6.0 (`ServiceReminderResetService`) gated by `ClusterFingerprintGate`, replaying only observed bytes | Only validated with the dashboard in km. Module identity and the minimum sequence are still unproven. The same write is **rejected when the dashboard is in miles** (`B3` then all-FF); miles interval scaling is unknown and writes stay research-build-only |
| Instrument reads | **Project app received live `5E01`/`0D01` responses (2026-08-12, again 2026-08-13)**: status `"043"` and odometer read directly in km. The 2026-08-13 dash-flip proved reads are **canonical** — `0D01` raw `0xAED4` and `5E01` `"043"` were byte-identical with the dash in km and in miles | `0D01` semantics (read-only vs capture) remain unconfirmed. No readable field reporting the current dashboard unit has been found (status `"043"` is identical in both units) |

Private captures were minimized into action-specific BTSnoop files; full bugreport ZIPs and unrelated report data were deleted. Use the dated private README files for exact capture windows and hashes. Use the maps—not this summary—for exact command bytes.

## Safety requirements

### General

- Treat every unknown request as unsafe. Never fuzz writes, routines, identifiers or security keys.
- Send read-only requests only to a known physical module address at a conservative rate.
- Do not expose write operations in the main/release app until module identity, part number and software version match an explicitly validated profile. Where a module returns no readable part number/software version (the Tiger instrument cluster), a write may be gated instead on an equivalent fail-closed fingerprint — the exact captured motorcycle profile, observed transport route and live constant status — as `ClusterFingerprintGate` does. The separate Triumph collector has one narrower research exception: after matching the known precursor reads and explicit acknowledgement, it may replay only the two exact mapped write sequences to gather per-bike evidence. This never authorizes speculative bytes or release support.
- Stop on identity mismatch, unexpected response, timeout, disconnect, low voltage or unstable vehicle state. Do not blindly retry.
- Record original values and verify changes immediately and after an ignition cycle.
- Never automatically retry a write after a disconnect or ambiguous result; the write may have succeeded even if its response was lost.
- State clearly that resetting a reminder does not perform maintenance.

### DTC clear

- Read and show the current codes before enabling Clear.
- Explain that clearing removes diagnostic evidence but does not repair the fault, then require a separate confirmation action.
- Allow clearing only for a validated ECU profile with ignition on and engine off.
- Treat UDS response pending (`7F1478` in the captured transaction) as an instruction to wait for the final response within a bounded timeout; never resend the clear request because of response pending.
- After a positive response, read the DTC count again and report remaining codes. Say “DTC memory cleared,” not “fault repaired.”

### Service reminder

- Never replay the observed reset against an unidentified instrument.
- Show the current value and requested date/distance before confirmation.
- Read back the new values immediately and after an ignition cycle.
- Do not repeat a known reset merely to obtain another identical trace. If a controlled research write is justified, change one field only and record before/requested/after values.

## Implementation requirements

- Use test-driven development for production behavior: write the smallest failing test, confirm it fails for the intended reason, implement only enough to pass, then refactor with the focused and full suites green.
- Turn every captured protocol behavior or reproduced bug into a sanitized unit/transcript test before changing production logic. Project scaffolding and hardware-characterization spikes are exceptions, but spike code does not become production code until tests describe it.
- Keep Bluetooth transport, adapter commands, CAN/ISO-TP, UDS, motorcycle profiles and UI as separate layers.
- Treat maps as versioned data, not scattered constants. Parse them into typed models and reject unknown schema versions or invalid status combinations.
- Build sanitized transcript-replay tests before application code connects to a motorcycle.
- Preserve raw frames beside decoded results in private development logs so interpretations can be corrected later.
- Serialize commands per adapter connection; do not interleave requests or bypass the transport queue with keepalives.
- Unknown or mismatched profiles remain read-only in the main/release app. The separate Triumph collector may expose its two explicit experimental write validations only under the exception above; all mismatched precursor reads remain read-only.
- Never treat an observed response as a universal fixed response or an observed sequence as a proven minimum sequence.
- Keep the Android and iOS connection implementations behind the same adapter interface even though their transports differ.

## Capture discipline

For every new capture, record local start/end time, phone and adapter used, ignition/engine/kill-switch state, requested action, values before and after, displayed result and whether an ignition-cycle verification was performed. Isolate one user action per capture whenever possible.

| Capture | Status | Purpose |
| --- | --- | --- |
| A: adapter only | Completed | Discovery, pairing, adapter channel, identity and disconnect |
| B: ECU baseline | Completed | Session setup, identity reads, security, keepalives and background polling |
| C: DTC read | Completed | Explicit DTC requests separated from baseline traffic |
| C2: DTC clear | Completed | Clear prerequisites, pending/final responses and post-clear state |
| D: service values, read-only | Unavailable in tested third party ECU linker UI | Separate instrument reads from write behavior |
| E: controlled service reset | Completed twice | Compare distance encoding; do not repeat without a new controlled question |
| F: project-app read-only capture | Completed 2026-08-10 | First app-executed adapter initialization, identifier reads and default-session DTC count (zero) |
| MC-IOS adapter proof | Pending | Send `ATI` once without an ECU and determine the actual GATT command/response channel |

Detailed transport capture and replay instructions are in the [Android](android/VLINK_CONNECTION.md) and [iOS](ios/VLINK_CONNECTION.md) guides. Open retained `.btsnoop` files in Wireshark, not a text editor. Reassemble RFCOMM/GATT fragments before interpreting ASCII, then normalize diagnostic traffic as:

```text
CAN identifier | ISO-TP PCI/length | diagnostic payload | padding
```

## Next milestones

1. ~~Run the first project-app read-only capture~~ — completed 2026-08-10 with zero findings; see the checkpoint above and `logs/2026-08-10/README.md`.
2. ~~Convert the validated 2026-08-10 journal into sanitized transcript-replay fixtures~~ — done: `android/app/src/test/resources/transcripts/motorcycle-2026-08-10-read-only-capture.yaml` replays byte-exactly through the full session stack in `MotorcycleCaptureTranscriptTest`.
3. Establish a validated engine profile: the capture proved the default session suffices for the count read with zero DTCs, but exact part number and software version remain unknown (identifier DIDs `F1A0/F1A2/F1AE/F1A7` returned values whose meaning is still unidentified).
4. Validate the DTC detail read (`190208`) and the extended-session branch on hardware when a confirmed DTC is actually present; do not fabricate a fault for this.
5. ~~Implement DTC clear and service read/reset against fake and replay transports~~ — done in v0.6.0 (`DtcClearService`, `ServiceReminderResetService`, both TDD-covered). Live writes remain research-build-only behind their gates. The **service reset was exercised and committed on hardware 2026-08-13 (km mode)**; the remaining hardware step is the DTC part of the harvest-sweep trip — read + clear a fault triggered by unplugging a front ABS sensor with ignition off (not yet performed as of 2026-08-17).
6. Progress each live Android operation independently as its prerequisites and identity checks become validated.
7. Later, perform the adapter-only MC-IOS `ATI` proof and begin the native iOS app only after its GATT transport is confirmed.
8. Localize the UI. Target English, Spanish, Ukrainian, French and German, selected automatically from the phone's language setting; English is the default and fallback. This applies to on-screen strings only — never to captured protocol bytes, map data, DTC codes or journal contents. **DTC messages are localized (delivered):** English (`*.en.dtcmap.yaml`) stays authoritative and resolves every code's meaning; per-locale display overlays (`*.<locale>.dtctranslation.yaml`, validated by `dtc-maps/dtctranslation.schema.json`) supply translated wording and any code they omit falls back to English. `LocalizedDtcDescriptions` preserves the English text as `originalMessage`, and the read UI shows a "Show original (English)" toggle whenever a translation is displayed — so the authoritative source is always one tap away. `AppContainer` picks the overlay from `Locale.getDefault().language`. Adding a locale is one overlay file plus its wiring; codes, bytes and journal contents are never translated. The remaining UI chrome strings still need `values-es`/`values-uk`/`values-fr`/`values-de` coverage (only the DTC toggle labels and app name exist so far).
9. Show an optional support footer ("Buy Me a Coffee" / Ko-fi link) as a small, always-visible element at the bottom of the screen — visible without scrolling, opening an external browser; no tracking or in-app purchase. Deliberately decouple it from any operation outcome: do **not** fire it on `DtcClearUiState.Cleared` / `ServiceResetUiState.Committed` or any other write success. Coupling a donation to the riskiest operation reads as pressure at an emotional peak and is the most write-adjacent placement possible, which `LEGAL_RESTRICTIONS.md` warns against (keep donations "voluntary and unrelated to access, features, updates or support"). A passive footer on general app engagement satisfies that. Both READMEs carry the maintainer's Ko-fi/Buy Me a Coffee and GitHub Sponsors links.
10. Optimize the project for discoverability by both classic search engines and LLM-based answers. Cover: a keyword-rich README (motorcycle/model names, "service reminder reset", "clear DTC", adapter and protocol terms) with clear headings and a plain-language "what it does / what it does not" summary; per-release descriptions on GitHub releases (what changed, supported motorcycle/adapter, APK asset); a GitHub project page/topics/description/About with all relevant attributes; and structured, factual, quotable phrasing so an LLM can answer "how do I reset the service light / clear a DTC on a Triumph Tiger 900" accurately. Never trade safety accuracy or the private-logs boundary for reach — no captures, VINs or protocol dumps in any public-facing text. First slice done: a root [`README.md`](README.md) now exists (user-facing, model-named "Triumph Tiger 900", "what it does / does not", safety-by-design "cannot break your ECU" framing, tested vs potentially-supported models with the shared-ECU-family "needs verification" caveat, the right-to-repair reasoning, adapter/dependency section, and an always-visible support footer note); `android/README.md` now names the model in its opening lines. Still open: per-release descriptions and the GitHub project page/topics/About.
11. Support motorcycles set to miles. The single goal is that the app **represents the bike's own unit** — it shows and accepts whatever unit the dashboard uses and never displays or writes a value that disagrees with it. There is deliberately no phone-locale conversion: the bike is the only source of truth, so the app never converts to a different unit than the dash shows. This is currently unsupported: the app hardcodes metric — `InstrumentResponseDecoder.decodeOdometer` maps the raw 16-bit value straight to `odometerKm`, and `ServiceReminderCommandBuilder` divides the entered km by the map's `raw_unit_km: 100` to produce the interval byte, with no unit detection anywhere. Deliver in stages, each gated on real observation:
    1. **Determine the cluster's unit model** — done 2026-08-13 (see milestone 12). The result is **mixed**: reads are canonical (Case A) but the interval write is unit-dependent, and a km-scaled write is rejected in miles mode.
    2. **Reads (canonical — confirmed):** the `0D01`/`5E01` raw bytes were byte-identical in km and miles, so reads are unit-independent. The remaining work is display-only: label the shown odometer/interval in the dash's unit while the bytes stay as they are. The blocker is detection — no readable field reporting the current dashboard unit has been found (`5E01` `"043"` is identical in both units), so the app can't yet know which unit to label without asking the user.
    3. **Writes (unit-dependent — partly answered):** the identical `334E` interval write accepted in km mode was rejected in miles mode (`B3` then all-FF). No successful miles write exists, so the miles interval scaling is still unknown and must not be guessed. Next capture needed: a **successful** miles-mode interval write (enter the interval the dash expects in miles, capture the accepted `33xx` byte) to derive the miles scaling. Then read the bike's unit, accept/write in that unit, and extend `ClusterFingerprintGate` so the unit assumption is part of the fingerprint and a mismatch fails closed. Until that capture exists, do not claim miles support; v0.6.3 keeps the km path and tells a miles user to switch the dash to km. *Open maintainer decision (raised 2026-08-17, undecided):* the maintainer floated a bounded probing experiment — sending candidate miles-scaled interval bytes and seeing which the cluster accepts — arguing observed rejections have been harmless. This would relax the "replay only observed bytes / no speculative writes" rule; an accepted-but-wrongly-scaled byte would silently persist a wrong interval (recoverable by rewriting in km mode). Do **not** implement any probing without the maintainer's explicit go-ahead and a written bounded protocol; the default remains the third-party capture path above.
12. ~~Run the "flip the dash unit, capture twice" experiment~~ **Done 2026-08-13** (journal `session-1786622057643`). The user reset in km, switched the dash to miles, and reset again at the same odometer. Reads came back byte-identical (canonical); the km-scaled interval write was rejected in miles mode. See the 2026-08-13 trip section and milestone 11 above. Note the experiment ended up including `33`/`5C` writes (the user performed resets, not read-only captures) — which is what surfaced the write-path result; the km reset was also the first project-app service reset validated on hardware.
13. Before any **write-capable release** (the first non-research build that can clear DTCs or reset the service reminder), ship an in-app consent/disclaimer gate and an explicit right-to-repair / interoperability posture. The gate is a real one-time acknowledgement (unofficial project, no warranty, resetting a reminder is not maintenance, clearing a code is not a repair, the user is responsible for use on their own vehicle) — not a buried EULA. This turns the strong internal safety policy into an external liability shield and pairs with the right-to-repair framing already in the root README, which doubles as discoverability content. Obtain the qualified Spanish/EU legal review that `LEGAL_RESTRICTIONS.md` already requires before that release, including the one-time read for Spanish/EU-market wording. No write-capable release ships without both the gate and that review.
14. Distribute via **signed GitHub release APKs** with a published SHA-256 checksum, each built from a public source tag (the flow `LEGAL_RESTRICTIONS.md` already requires). No CI is planned — this is a single-developer project with no remote — so a tagged local build, not a pipeline, is the release mechanism. A curated store such as F-Droid (which would add trust, reproducible source builds and automatic updates) is intentionally deferred: it is worth reconsidering only if manual-update friction becomes a real problem at scale, and it is not a prerequisite for any release.

## Open questions

1. What exact engine-ECU and instrument part numbers and software versions produced the captures?
2. Which adapter, diagnostic-session and SecurityAccess steps are actually required for DTC clear (and for the DTC detail read)? The 2026-08-10 project-app capture proved the DTC count read needs neither `1003` nor SecurityAccess in the default session; the detail read and clear remain open.
3. Is the now-known captured engine seed/key derivation actually required for DTC clear? It is not required for the validated default-session DTC count read. As of v0.6.0 it is executable in the research build behind the fingerprint gate + explicit confirm, specifically so the next motorcycle trip can answer this; it stays absent from release builds.
4. What are the precise semantics and prerequisites of instrument services `0x5E`, `0x0D`, `0x33` and `0x5C`?
5. Does `0D01` only read odometer data, and how are service date/distance fields represented across units and model variants?
6. Are service-reset order, timing or keepalives significant?
7. What is Triumph's OEM-confirmed description for captured DTC `0x157700` / inferred display `P1577-00`? The current dictionary has only a third-party-corroborated project paraphrase.
8. Which other module identities and software versions share these protocols?
9. Which MC-IOS GATT endpoint carries commands and responses, and what write type and fragmentation behavior does it use?
10. Are extended session and SecurityAccess required for all-groups DTC clear, and are there Triumph-specific side effects beyond erasing stored DTC information?
11. ~~Does the instrument cluster store distance/interval in one canonical unit or the rider's chosen unit?~~ **Partly answered (2026-08-13 dash-flip):** *reads* are canonical (odometer/status byte-identical in km and miles), but the *interval write* is unit-dependent — a km-scaled `334E` accepted in km mode was rejected in miles mode. Still open: (a) is there any readable field reporting the current dash unit? None found — `5E01` `"043"` is identical in both. (b) What is the accepted miles interval-byte scaling? Unknown; needs a **successful** miles-mode write to derive it.

The 2026-08-10 journal answered the first project-app questions: the expected adapter identity is reached, the known engine route responds, the six non-sensitive identifiers return the previously observed values, and the DTC count read works in the default session without `1003`. Still unanswered by any project-app capture: the detail-read format with a nonzero count, DTC clear, service reset, the instrument module and SecurityAccess.

Update a map only from a retained capture, a controlled test or an authoritative source. Preserve the raw observation and label every inference.
