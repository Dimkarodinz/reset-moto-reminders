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
- The 2026-08-10 capture found zero DTCs, so a nonzero count/detail validation still requires a future capture with a present fault. DTC clear and service reset were subsequently built (v0.6.0, below) but remain **hardware-unvalidated**; they exist to be exercised on the next motorcycle trip behind their gates.
- Follow-up (Android **v0.5.0**, `versionCode 6`, test/lint/APK-verified only): the default-session confirmed-DTC read is now a mainline user-facing feature that decodes codes against the DTC dictionary; the read-only engine capture and a new first-contact instrument read (observed `5E01`/`0D01` only) stay behind `RESEARCH_BUILD`. All three are reads — no SecurityAccess, no writes. A nonzero decoded DTC read and the instrument path are still unvalidated against a motorcycle.
- **Harvest-sweep build — Android v0.6.0 (`versionCode 7`, test/lint/APK-verified only, 2026-08-11):** built to gather everything for one more trip and to validate the two write operations in the same session. Adds, all behind `RESEARCH_BUILD`:
  - **DTC clear** (`DtcClearService`): extended session → `2701` seed → derived `2702` key → `14FFFFFF` → verify `190108` count. The seed/key transform (`EngineSeedKeyDerivation`) is now **executable** (see the scoped exception below), reached only after an explicit in-app confirmation.
  - **Service-reminder reset** (`ServiceReminderResetService`): replays the observed instrument bytes (`5E01` → `0D01` → `33xx` distance → `5Cxx…` date), confirming success by matching the date-commit echo. Gated by `ClusterFingerprintGate`, which **fails closed** before any write byte: it requires the exact captured motorcycle profile, the observed instrument transport route, and the live `5E01` status matching the validated `043`. There is no readable instrument part number/software version, so this fingerprint substitutes for a module-identity match.
  - Both write flows require an explicit arm→confirm action in the UI, run only when the packaged write profiles are present, and are absent from any release build (`writesEnabled = BuildConfig.RESEARCH_BUILD`, enforced in `AdapterSessionOwner` as well as the UI).
  - **Nothing here is hardware-validated yet.** This build exists to be run on the motorcycle: harvest a healthy-state journal + validate service reset, then (after triggering a fault by unplugging a front ABS/wheel-speed sensor with ignition off) read + clear a real DTC. No probes or speculative requests are sent — writes are gated on observed data only.

### Captured engine SecurityAccess derivation — evidence only

Private interoperability analysis of third party OEM linker app identified the two-byte seed/key transform used on the captured Tiger engine-ECU path. The APK and decompiled material were kept outside the repository and deleted after analysis; no implementation code or third-party asset was copied.

Treat the response to `27 01` as an unsigned 16-bit big-endian seed. The corresponding `27 02` key is:

```text
key = (seed * 0x4B48) mod 0x10000
```

Send the result as two big-endian bytes. This independently written description matches all six retained engine-ECU exchanges exactly: `188B→A018`, `871C→33E0`, `FBBD→2C28`, `2A7B→FB98`, `7108→2240`, and `89DE→D070`. It is a small deterministic seed/key transform, not general-purpose encryption.

Scope it only to the captured 2021 Tiger 900 GT Pro engine ECU/address path. Do not infer compatibility from `Keihin`, Triumph, model name or CAN address alone. The 2026-08-10 project-app capture already proves default-session DTC count reading does **not** need SecurityAccess.

**Scoped execution exception (v0.6.0):** the maintainer has authorized this transform to execute, but only inside `EngineSeedKeyDerivation` in the **research build** (`BuildConfig.RESEARCH_BUILD`), only on the **engine-ECU DTC-clear path**, and only after the exact-cluster fingerprint gate and an explicit in-app confirmation. It never runs in a release build, on the instrument path, or for a default-session read. `LEGAL_RESTRICTIONS.md` records the same exception. Whether DTC clear actually requires SecurityAccess is still an open hardware question — this build exists to answer it.

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
| Service reminder | Successful 10,000 km and 8,000 km resets captured; distance uses 100 km units in both observations. Implemented in v0.6.0 (`ServiceReminderResetService`) gated by `ClusterFingerprintGate` (exact profile + observed transport + live `5E01` `043` status), replaying only observed bytes | Module identity, date encoding and minimum sequence are not validated on hardware by the project app; writes stay research-build-only until the next capture confirms them |

Private captures were minimized into action-specific BTSnoop files; full bugreport ZIPs and unrelated report data were deleted. Use the dated private README files for exact capture windows and hashes. Use the maps—not this summary—for exact command bytes.

## Safety requirements

### General

- Treat every unknown request as unsafe. Never fuzz writes, routines, identifiers or security keys.
- Send read-only requests only to a known physical module address at a conservative rate.
- Do not expose write operations until module identity, part number and software version match an explicitly validated profile. Where a module returns no readable part number/software version (the Tiger instrument cluster), a write may be gated instead on an equivalent fail-closed fingerprint — the exact captured motorcycle profile, the observed transport route and a live constant status — as `ClusterFingerprintGate` does. Such write features stay research-build-only until validated on hardware.
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
- Unknown or mismatched profiles remain read-only and must not expose DTC-clear or service-reset actions.
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
5. ~~Implement DTC clear and service read/reset against fake and replay transports~~ — done in v0.6.0 (`DtcClearService`, `ServiceReminderResetService`, both TDD-covered). Live writes remain research-build-only behind their gates; the next step is to exercise them on the motorcycle in one harvest-sweep trip (harvest + service reset healthy, then read + clear a fault triggered by unplugging a front ABS sensor with ignition off).
6. Progress each live Android operation independently as its prerequisites and identity checks become validated.
7. Later, perform the adapter-only MC-IOS `ATI` proof and begin the native iOS app only after its GATT transport is confirmed.
8. Localize the UI. Target English, Spanish and Ukrainian, selected automatically from the phone's language setting; English is the default and fallback. This applies to on-screen strings only — never to captured protocol bytes, map data, DTC codes or journal contents. The user-facing DTC dictionary is already language-tagged (`*.en.dtcmap.yaml`), so plan for `es`/`uk` message maps rather than translating codes in app code.
9. Show an optional support footer ("Buy Me a Coffee" / Ko-fi link) as a small, always-visible element at the bottom of the screen — visible without scrolling, opening an external browser; no tracking or in-app purchase. Deliberately decouple it from any operation outcome: do **not** fire it on `DtcClearUiState.Cleared` / `ServiceResetUiState.Committed` or any other write success. Coupling a donation to the riskiest operation reads as pressure at an emotional peak and is the most write-adjacent placement possible, which `LEGAL_RESTRICTIONS.md` warns against (keep donations "voluntary and unrelated to access, features, updates or support"). A passive footer on general app engagement satisfies that. Both READMEs carry the maintainer's Ko-fi/Buy Me a Coffee and GitHub Sponsors links.
10. Optimize the project for discoverability by both classic search engines and LLM-based answers. Cover: a keyword-rich README (motorcycle/model names, "service reminder reset", "clear DTC", adapter and protocol terms) with clear headings and a plain-language "what it does / what it does not" summary; per-release descriptions on GitHub releases (what changed, supported motorcycle/adapter, APK asset); a GitHub project page/topics/description/About with all relevant attributes; and structured, factual, quotable phrasing so an LLM can answer "how do I reset the service light / clear a DTC on a Triumph Tiger 900" accurately. Never trade safety accuracy or the private-logs boundary for reach — no captures, VINs or protocol dumps in any public-facing text. First slice done: a root [`README.md`](README.md) now exists (user-facing, model-named "Triumph Tiger 900", "what it does / does not", safety-by-design "cannot break your ECU" framing, tested vs potentially-supported models with the shared-ECU-family "needs verification" caveat, the right-to-repair reasoning, adapter/dependency section, and an always-visible support footer note); `android/README.md` now names the model in its opening lines. Still open: per-release descriptions and the GitHub project page/topics/About.
11. Support motorcycles set to miles. The single goal is that the app **represents the bike's own unit** — it shows and accepts whatever unit the dashboard uses and never displays or writes a value that disagrees with it. There is deliberately no phone-locale conversion: the bike is the only source of truth, so the app never converts to a different unit than the dash shows. This is currently unsupported: the app hardcodes metric — `InstrumentResponseDecoder.decodeOdometer` maps the raw 16-bit value straight to `odometerKm`, and `ServiceReminderCommandBuilder` divides the entered km by the map's `raw_unit_km: 100` to produce the interval byte, with no unit detection anywhere. Deliver in stages, each gated on real observation:
    1. **Determine the cluster's unit model** from the "flip the dash unit, capture twice" experiment on the existing bike (see milestone 12 / the harvest procedure). Diff the raw `5E01`/`0D01` bytes between a km capture and a miles capture of the same odometer.
    2. **If the cluster stores one canonical unit (display-only toggle):** the raw bytes are identical, so reads/writes are unit-independent. Label the app's displayed unit to match the dash; the numbers and written bytes stay as they are. Safe with no further capture.
    3. **If the cluster stores in the user's chosen unit:** the diff reveals which field carries the unit and how the interval scaling changes. Read the bike's unit from that field, label reads in it, and accept/write interval values in that same unit — never assume, and **refuse the write** when the unit cannot be established. Extend `ClusterFingerprintGate` so the unit assumption is part of the fingerprint and a mismatch fails closed.
    Until a miles capture exists, do not claim miles support and keep any miles-write path research-build-only behind the gate.
12. Run the "flip the dash unit, capture twice" experiment to answer milestone 11's core unknown. On the existing (km) bike, take a read-only instrument capture with the dash in km, switch the dashboard display to miles, take a second read-only capture of the same odometer, then restore the dash to km. Read-only only — no `33`/`5C` write is needed or allowed for this. Retain both journals; the byte diff decides the Case-A/Case-B branch above.
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
11. Does the instrument cluster store distance/interval in one canonical unit (miles/km toggle is display-only) or in the rider's chosen unit? Is there a readable field that reports the current unit, and does the service-interval byte scaling change between miles and km? The "flip the dash, capture twice" experiment (milestone 12) is designed to answer this; until then the app assumes metric.

The 2026-08-10 journal answered the first project-app questions: the expected adapter identity is reached, the known engine route responds, the six non-sensitive identifiers return the previously observed values, and the DTC count read works in the default session without `1003`. Still unanswered by any project-app capture: the detail-read format with a nonzero count, DTC clear, service reset, the instrument module and SecurityAccess.

Update a map only from a retained capture, a controlled test or an authoritative source. Preserve the raw observation and label every inference.
