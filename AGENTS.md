# Reset Moto Reminders project guide

## Objective

Build a source-available Android/iOS application with four motorcycle-facing features:

1. Read bounded dashboard information as proof that the motorcycle responded.
2. Read diagnostic trouble codes (DTCs).
3. Clear DTCs only after displaying them and receiving explicit confirmation.
4. Reset the service reminder by setting the next-service date and distance.

The first target is a 2021 Triumph Tiger 900 GT Pro connected through a vLinker MC+. Do not expand the product into ECU coding or calibration, firmware flashing, actuator tests, arbitrary diagnostic scanning, emissions changes, immobilizer work or security-access brute forcing.

The application exposes only the bounded operations above. ECU-map files are an internal command/profile database used to implement those operations across supported motorcycles; importing, editing, configuring or executing maps is not a user-facing feature. Never describe adapter CAN-header/filter setup as “configuring the ECU”: it only routes an allowed diagnostic operation to its known target module and does not alter ECU configuration.

## Platform strategy

Build and maintain two native applications:

1. Android first, using Kotlin, Jetpack Compose and Android's Bluetooth APIs.
2. iOS using Swift, SwiftUI and CoreBluetooth. On-device installation, launch, primary adapter identity and dashboard/odometer read are validated; write operations retain their individual evidence gates.

Do not start with Flutter/Dart or another shared UI runtime. The product has a small UI, while its critical transport code is platform-specific: Android currently uses Bluetooth Classic RFCOMM and iOS uses BLE GATT. A cross-platform layer would still depend on different native plugins or Kotlin/Swift bridges, adding another failure and debugging boundary around safety-sensitive I/O.

Share contracts rather than Bluetooth implementation code: adapter/ECU maps, schemas, sanitized transcripts, state names, safety rules, UI wording and equivalent test cases. Keep both apps behind the same conceptual adapter interface, but allow platform-native lifecycle and error handling. Reconsider runtime code sharing only after the Android vertical slice works and the iOS transport has been captured and validated. Platform choice does not change signing or store-distribution rules.

## Sources of truth

| Subject | File | Responsibility |
| --- | --- | --- |
| Motorcycle protocol | [`ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml), [`ecu-maps/ecumap.schema.json`](ecu-maps/ecumap.schema.json) | Module identity, CAN/ELM configuration and observed diagnostic operations, validated by the ECU-map schema |
| DTC dictionary | [`dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml), [`dtc-maps/dtcmap.schema.json`](dtc-maps/dtcmap.schema.json) | One Tiger 900 GT Pro / Triumph Keihin ECU map containing the observed exact entry and clearly separated unvalidated reference fallbacks |
| Adapter-map contract | [`adapter-maps/adaptermap.schema.json`](adapter-maps/adaptermap.schema.json) | Required schema-version-2 interface for every adapter map |
| Android adapter | [`adapter-maps/vlinker-mc-android.adaptermap.yaml`](adapter-maps/vlinker-mc-android.adaptermap.yaml) | Captured Bluetooth Classic/RFCOMM transport and adapter initialization |
| iOS adapter | [`adapter-maps/vlinker-mc-ios.adaptermap.yaml`](adapter-maps/vlinker-mc-ios.adaptermap.yaml) | Captured BLE/GATT profile, project-app-observed primary channel and remaining validation state |
| Android application | [`android/README.md`](android/README.md), [`android/AGENTS.md`](android/AGENTS.md), [`android/.claude/plans/initial-version.md`](android/.claude/plans/initial-version.md), [`android/VLINK_CONNECTION.md`](android/VLINK_CONNECTION.md) | Build/test instructions, Android ownership, reviewed plan and RFCOMM guidance |
| Triumph research application | [`research-builds/android/triumph/README.md`](research-builds/android/triumph/README.md), [`research-builds/android/triumph/AGENTS.md`](research-builds/android/triumph/AGENTS.md), [`research-builds/android/triumph/PLAN.md`](research-builds/android/triumph/PLAN.md) | Separate one-session Triumph compatibility collector, privacy boundary, test procedure and reviewed implementation plan |
| General research application | [`research-builds/android/general/README.md`](research-builds/android/general/README.md), [`research-builds/android/general/AGENTS.md`](research-builds/android/general/AGENTS.md), [`research-builds/android/general/PLAN.md`](research-builds/android/general/PLAN.md) | Separate bounded standard-OBD read collector for unmapped motorcycle families; never authorizes writes |
| iOS application | [`ios/README.md`](ios/README.md), [`ios/AGENTS.md`](ios/AGENTS.md), [`ios/PLAN.md`](ios/PLAN.md), [`ios/VLINK_CONNECTION.md`](ios/VLINK_CONNECTION.md) | Native app/build guide, tested core, observed primary channel/dashboard read and remaining operation gates |
| Public website | [`docs/`](docs/), [`docs/translations/`](docs/translations/), [`tools/build-site-locales.rb`](tools/build-site-locales.rb), [`tools/check-site-locales.rb`](tools/check-site-locales.rb) | English source pages, generated German/Spanish/French/Ukrainian pages, language metadata and reproducible completeness checks |
| Publication policy | [`LEGAL_RESTRICTIONS.md`](LEGAL_RESTRICTIONS.md), [`LICENSE`](LICENSE), [`COMMERCIAL_LICENSING.md`](COMMERCIAL_LICENSING.md), [`CONTRIBUTING.md`](CONTRIBUTING.md), [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md) | Clean-room, privacy, branding, contribution, distribution and release rules; PolyForm Noncommercial source license and separate commercial licensing |
| Private evidence | Dated README files under the gitignored `logs/` tree, including `2026-08-22` | Capture inventory, provenance and integrity hashes |
| Journal analysis | [`tools/journal-analyze.py`](tools/journal-analyze.py) | Offline comparison of a pulled capture journal against the adapter, ECU and DTC maps |

The entire `logs/` tree is private evidence. Never publish or commit bugreports, dumpstate archives, complete HCI captures, screenshots containing identifiers or unrelated captured traffic. Follow the legal release checklist before preparing public source, a website or a binary.

Keep this file as the project index, safety policy and current research status. Put protocol values in maps and platform implementation details in the Android/iOS connection guides. Split research history into `docs/research/` only after multiple motorcycles make this file difficult to navigate, and retain links here.

## Current cross-platform checkpoint — 2026-08-27

- **Android v0.8.0 public release candidate (2026-08-27):** a dedicated RSA-4096 project key
  signs maintainer release builds through environment variables populated by
  [`tools/build-android-release.sh`](tools/build-android-release.sh). The private
  PKCS#12 keystore is outside Git, its password is in the macOS Keychain, and
  `dist/` is ignored. The build script requires a clean exact tag for public
  output, rejects the Android debug certificate, verifies APK Signature Scheme
  v2 and emits a SHA-256 file. Release-certificate SHA-256 is
  `2fdf4e70325bd00d6f7140deff1285eda368d6711f0dc53415b863661eb50ad6`.
  v0.8.0 enables only the existing profile-gated DTC clear and service reset operations,
  adds a persistent first-launch safety acknowledgement, and packages the license
  notices. DTC clear remains visibly labelled Beta.
- Main Android **v0.8.0** (`versionCode 12`), Triumph Research **v0.4.0** (`versionCode 4`) and General Motorcycle Research **v0.2.0** (`versionCode 2`) pass their respective unit tests, lint and builds. All three use the fixed dark theme plus a bold italic action-style title. The main app hides profile-gate wording before connection; service reset is a normal supported-profile action and only DTC clear is labelled Beta. General Research remains a finite read-only standard-OBD collector and must not inherit Triumph write commands.
- Native iOS preview **v0.1.4** (`build 5`) is implemented in [`ios/ResetMotoReminders/`](ios/ResetMotoReminders/) with its platform-neutral TDD core in [`ios/ResetMotoCore/`](ios/ResetMotoCore/). Main now has English-default German, Spanish, French and Ukrainian localization for the UI, Bluetooth/diagnostic errors and DTC descriptions; all **36** protocol/use-case tests plus **5** presentation-contract tests pass, and unsigned simulator/generic-device builds pass. The v0.1.3 follow-up physically completed connection and dashboard/odometer read on the iPhone 12. The public `ios-v0.1.4` release predates the localization change and still contains both an unsigned IPA with checksum for local AltServer signing and a source ZIP with checksum for Xcode self-build.
- The first powered iOS run reached **Motorcycle connected** but stopped at `ATWS`; v0.1.3 fixed that command-specific response without weakening ordinary setup validation. The follow-up completed the dashboard/odometer read and recognized the exact `043` instrument fingerprint. This validates the corrected read path, not DTC clear or every service-write mode.
- The iOS app mirrors dashboard/odometer read, DTC read, DTC clear (Beta), and km/miles service reset. Commands and DTC descriptions are generated into a typed JSON resource from the shared adapter/ECU/DTC maps by [`ios/tools/sync_profiles.rb`](ios/tools/sync_profiles.rb); feature views contain no protocol bytes.
- Physical installation, the dark UI and the primary MC-IOS `ATI` gate are verified. Each connection uses only the primary `18F0` split channel, enables notifications through CoreBluetooth, requires a recognizable `vLinker`/`ELM`/`STN` identity and prompt, and sends no motorcycle command if this gate fails. The alternate GATT channel remains probe-only and is never auto-tried. Bounded Apple system logs now record operation/command outcomes without raw replies or personal/device identifiers.
- iOS operations are serialized for the complete multi-command flow. DTC clear sends the clear request once through response-pending and verifies the count. Service reset validates distance/date before traffic, fingerprints status `043` before writing, accepts dates only from today through two years ahead, and reports partial or ambiguous results without retrying.

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

### 2026-08-22 — miles command captured and Android v0.7.0 implemented

- Minimized private HCI traces captured successful third-party service resets with the dashboard set to kilometres and miles. Both traces use the same status, odometer and date operations; the distance operation alone differs: service `0x33` represents hundreds of kilometres and service `0x34` represents hundreds of miles. In both cases raw `0x3C` represents 6000 of the selected unit.
- The ECU map now records separate km/miles request prefixes and a common raw unit of 100. Android v0.7.0 (`versionCode 11`) asks the rider to choose the unit currently shown on the motorcycle and builds only the corresponding observed request; it does not infer a unit from phone locale or convert the requested interval.
- The main app UI is intentionally limited to **Dashboard information**, **Read trouble codes**, **Clear trouble codes (Beta)** and **Reset service reminder**. The dashboard card is the already validated `5E01`/`0D01` read-only path; broad capture/report-sharing controls are not exposed.
- The main app now uses a fixed high-contrast dark theme: near-black background/system bars, graphite surfaces, muted teal actions and restrained warning colors. Do not reintroduce automatic light mode without an explicit product decision.
- Cross-validation against a retained private third-party APK agrees with the captured DTC requests and transaction shape. `DtcClearService` now sends `14FFFFFF` exactly once, consumes response-pending plus the final positive response, and only then re-reads the DTC count; it must never resend the clear because of `7F1478`.
- **2026-08-23 orchestration hardening:** `AdapterOperationGate` now owns one lease for each complete multi-command action, not merely each individual ELM command. While a dashboard read, DTC read/clear or reminder reset is active, every other motorcycle action and Disconnect are disabled and backend calls fail closed rather than queueing. This prevents another feature from changing the CAN route between a write flow's prerequisite read and write. If a response is lost after a write, or the reminder interval is accepted but its date is not confirmed, the UI and journal say the outcome needs inspection/re-reading and never claim that nothing changed. DTC Clear remains usable as the explicitly labelled Beta flow; no unsupported identity or automatic unit inference was added. Main verification: 181 tests, lint and debug APK assembly pass.
- Current physical-test boundary: the km reset is project-app validated; the miles reset is capture-validated and implemented but still needs one project-app run; nonzero DTC detail and DTC clear remain hardware-unvalidated. v0.8.0 release-enables the bounded writes; DTC clear remains visibly Beta.

### 2026-08-17 — separate Triumph compatibility collector

- A second Android application now lives at `research-builds/android/triumph` with package `dev.resetlight.research.triumph`, so it installs alongside Reset Moto Reminders rather than replacing it.
- Its Compose and system-bar presentation is fixed dark, using the same near-black/graphite/muted-teal visual direction as the main app.
- Its one-go scan asks for Triumph model/year, connects through the validated MC-Android profile, records adapter metadata, runs all packaged non-sensitive engine identifier reads, reads confirmed DTC count/details, and reads the known TFT status/odometer route. Engine/DTC and instrument/service evidence are classified independently.
- Its read phase uses an exact/semantic `ResearchCommandPolicy`: VIN/serial identifiers, SecurityAccess, DTC clear, `0x2E`, `0x31`, `0x33`, `0x5C`, OBD clear and unbounded bus monitoring are rejected. The only non-read diagnostic transition is one observed `1003` fallback when the default-session DTC count is unavailable.
- **Same-session write validation.** After the complete read phase, the tester may select the exact known service-reset sequence, DTC-clear sequence, or both. Service validation requires the dashboard unit plus user-entered current interval/date, temporarily writes the minimum known change (`+100` in that unit, `+1 day`), verifies echoes, then writes the entered baseline back in the same unit and records restoration independently. `ResearchWriteCommandPolicy` permits only mapped setup/verification reads plus the derived two-byte key, exact clear request, one-byte `0x33` km or `0x34` miles distance request and structured date request. Eligibility requires the corresponding live read candidate; the UI defaults writes off, leaves baseline fields empty, and requires acknowledgement.
- Run service test/restoration before DTC clear so all original read/DTC evidence is already retained and the later clear can reapply the engine route. After an explicit service rejection with a healthy connection, still attempt restoration; if restoration itself is rejected, record it and continue to separately selected DTC clear. A timeout, disconnect or ambiguous service write marks restoration unknown, stops the session and must never trigger an automatic follow-up write or retry.
- `dtc_clear_candidate`, `service_reset_candidate`, and even a validated optional write apply only to the entered motorcycle/year. They do not create a main-app profile or establish family-wide compatibility without review.
- JSONL reports are app-private until explicitly shared, survive cancellation/failure, omit Bluetooth addresses, never request VIN/serial, and apply text plus ASCII-in-raw-hex redaction. This review fixed the shared redactor so raw ELM bytes cannot bypass VIN/security privacy checks.
- v0.3.0 (`versionCode 3`, APK SHA-256 `2cff50d341bd597a6247554c3738fec4487087a619f4ed6a74f9a0102372175f`) supersedes the earlier collector and is installed alongside the main app on the Samsung SM-A202F / Android 11 phone. Package/version, cold launch, scrolling, empty current-value fields, the +100 km/+1 day/restore explanation, DTC-clear control, ambiguous-restore warning, acknowledgement and disabled-until-ready action were visually verified without starting a motorcycle session. Verification: **167** main Android tests, **25** Triumph Research tests and **13** iOS `ProbeKit` tests pass; both Android modules pass lint and assemble.
- **v0.4.0 (`versionCode 4`, 2026-08-22)** incorporates the successful km/miles captures and private static cross-check: the tester explicitly selects the motorcycle unit, the round trip uses `0x33` for km or `0x34` for miles without conversion, restoration is locked to that same unit, and report events record the unit and both distances. The shared DTC clear now sends `14FFFFFF` once, consumes `7F1478` plus the final `54`, and verifies the count. Tests cover miles test/restore, adjacent-prefix rejection and single-send pending/final behavior. It still requires a live report from each candidate motorcycle before main-app support is added.

### 2026-08-17 — general motorcycle read-only collector

- A third Android application lives at `research-builds/android/general` with package `dev.resetlight.research.general`. It asks for manufacturer/model/year and runs one finite standard-OBD read through the validated MC-Android transport.
- The profile contains 18 serialized requests covering adapter/protocol metadata, automatic protocol selection, supported PID pages, stored/pending/permanent DTCs and non-identifying Mode 09 information. Protocol identity is recorded after the first standard requests trigger automatic detection. `GeneralResearchCommandPolicy` independently rejects VIN, DTC clear, SecurityAccess, writes, routines, monitoring, unknown AT commands and undeclared requests.
- Reports are app-private JSONL until explicitly shared. They contain no requested VIN/serial or Bluetooth address, retain the raw bounded traffic after redaction, continue after unsupported individual probes, and preserve a partial report on terminal failures.
- This collector cannot discover a safe service-reset or DTC-clear write by itself. Its result is input to later public-source/passive-capture research and an exact manufacturer profile; it never guesses or executes candidates.
- General Research v0.2.0 (`versionCode 2`, APK SHA-256 `117c748cfef0384cc18bbcfe6d6d29c8738cf53e5db0c309a4d12c91bfd98172`) is installed alongside both existing packages on the Samsung SM-A202F / Android 11 phone. The v0.2 refresh applies the same fixed dark system bars, Material palette and bold italic title as the other apps without changing the finite 18-command scan or its read-only policy. All **14** General Research tests, lint and debug assembly pass; the APK was cold-launched without starting a hardware scan.
- The three Android launchers form one visual family. Reset Moto Reminders owns the launcher foreground PNGs and background color under `android/app/src/main/res`; each research module copies those resources into its generated build resources and replaces only the center needle/hub with its small overlay (`T` for Triumph, `?` for general research). Never redraw the outer gauge, reset arrow, speed marks, scale or yellow accent independently in a research module. Rebuild both research APKs after changing the main launcher asset.
- Phone installation/launch verification and motorcycle evidence are separate. Do not call this app hardware-validated on another Triumph until a powered-adapter report contains `adapter_ready`, module probe events and a terminal `research_session_finished`.

### Captured engine SecurityAccess derivation — evidence only

Private interoperability analysis of a third-party ECU-linker app identified the two-byte seed/key transform used on the captured Tiger engine-ECU path. The APK and decompiled material are retained only in the gitignored local `tmp/` workspace until the maintainer explicitly requests deletion; no third-party asset is committed or distributed.

Treat the response to `27 01` as an unsigned 16-bit big-endian seed. The corresponding `27 02` key is:

```text
key = (seed * 0x4B48) mod 0x10000
```

Send the result as two big-endian bytes. This independently written description matches all six retained engine-ECU exchanges exactly: `188B→A018`, `871C→33E0`, `FBBD→2C28`, `2A7B→FB98`, `7108→2240`, and `89DE→D070`. It is a small deterministic seed/key transform, not general-purpose encryption.

Scope it only to the captured 2021 Tiger 900 GT Pro engine ECU/address path. Do not infer compatibility from `Keihin`, Triumph, model name or CAN address alone. The 2026-08-10 project-app capture already proves default-session DTC count reading does **not** need SecurityAccess.

**Scoped execution exception:** the maintainer has authorized this transform to execute only inside `EngineSeedKeyDerivation`, only on an explicitly confirmed **engine-ECU DTC-clear path**, and never for an ordinary read, the instrument path, or an ungated path. In the main Android app this remains behind the exact packaged profile, read-before-clear and explicit confirmation gates. The separate Triumph Research collector may execute it after the known engine route returns a decodable DTC count and the tester explicitly selects and acknowledges DTC clear. `ResearchWriteCommandPolicy` rejects every other key shape and use. `LEGAL_RESTRICTIONS.md` records the same exception. Whether clear actually requires SecurityAccess is still an open hardware question.

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
| MC-IOS transport | Advertisement and complete GATT layout captured; the production app completed `ATI` over `18F0`/`2AF1`, corrected initialization and the dashboard/odometer read | Exact identity and fragment transcript were not retained; write operations keep their separate validation gates |
| DTC read | Confirmed-DTC count/read observed twice by third-party tools; **project app executed the count read in the default session on 2026-08-10** (zero confirmed DTCs, no `1003`, no SecurityAccess) | The detail read (`190208`) and a nonzero count have not been executed by the project app; extended-session fallback is implemented but unexercised on hardware |
| DTC clear | One successful clear observed: response pending, final positive response, then zero-count verification; captured engine seed/key derivation is known and matches six retained exchanges. Implemented in `DtcClearService`; v0.8.0 sends clear once, consumes pending/final responses, then verifies the count | Whether clear needs SecurityAccess, plus module-identity validation, remain pending; the implementation is untested against a motorcycle and labelled Beta |
| Service reminder | **Project app committed a full km-mode reset on hardware 2026-08-13**. Successful km and miles resets captured on 2026-08-22 establish `0x33` = hundreds of km and `0x34` = hundreds of miles. v0.8.0 release-enables both behind `ClusterFingerprintGate` and asks the rider for the dashboard unit | Km is project-app validated. Miles is capture-validated but still needs its first project-app run. Module identity and the minimum sequence remain unproven |
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
| MC-IOS adapter proof | Completed by production app | Primary `18F0` split channel and accepted `ATI` identity observed; exact transcript was not retained |

Detailed transport capture and replay instructions are in the [Android](android/VLINK_CONNECTION.md) and [iOS](ios/VLINK_CONNECTION.md) guides. Open retained `.btsnoop` files in Wireshark, not a text editor. Reassemble RFCOMM/GATT fragments before interpreting ASCII, then normalize diagnostic traffic as:

```text
CAN identifier | ISO-TP PCI/length | diagnostic payload | padding
```

## Next milestones

1. ~~Run the first project-app read-only capture~~ — completed 2026-08-10 with zero findings; see the checkpoint above and `logs/2026-08-10/README.md`.
2. ~~Convert the validated 2026-08-10 journal into sanitized transcript-replay fixtures~~ — done: `android/app/src/test/resources/transcripts/motorcycle-2026-08-10-read-only-capture.yaml` replays byte-exactly through the full session stack in `MotorcycleCaptureTranscriptTest`.
3. Establish a validated engine profile: the capture proved the default session suffices for the count read with zero DTCs, but exact part number and software version remain unknown (identifier DIDs `F1A0/F1A2/F1AE/F1A7` returned values whose meaning is still unidentified).
4. Validate the DTC detail read (`190208`) and the extended-session branch during the planned controlled ABS-sensor fault test; ignition must be off while disconnecting or reconnecting the sensor, and the motorcycle must not be ridden in that state.
5. ~~Implement DTC clear and service reset against fake and replay transports~~ — done. v0.8.0 enables the existing bounded operations in the signed release while retaining their exact-profile, confirmation, serialization and no-retry gates. Remaining hardware checks: one miles reset through the project app, then read + clear a controlled fault; DTC clear remains visibly Beta.
6. Progress each live Android operation independently as its prerequisites and identity checks become validated.
7. ~~Perform the MC-IOS `ATI` proof and corrected dashboard read~~ — primary production channel, initialization and dashboard/odometer read confirmed. Next retain a nonzero DTC read before the controlled Beta clear; service modes keep their existing evidence gates.
8. ~~Localize the UI and website.~~ **Delivered for English, German, Spanish, French and Ukrainian; English is the default and fallback.** Android and iOS choose the phone language automatically. The website exposes a visible language switcher, localized canonical/hreflang metadata and all 20 page/locale entries in the sitemap. English DTC maps remain authoritative; platform display overlays localize the wording while codes, protocol bytes and journal contents are never translated. Regenerate website pages with `ruby tools/build-site-locales.rb`, iPhone strings with `ruby ios/tools/sync_localizations.rb`, and verify both with the site checker and Swift tests.
9. Show an optional support footer ("Buy Me a Coffee" / Ko-fi link) as a small, always-visible element at the bottom of the screen — visible without scrolling, opening an external browser; no tracking or in-app purchase. Deliberately decouple it from any operation outcome: do **not** fire it on `DtcClearUiState.Cleared` / `ServiceResetUiState.Committed` or any other write success. Coupling a donation to the riskiest operation reads as pressure at an emotional peak and is the most write-adjacent placement possible, which `LEGAL_RESTRICTIONS.md` warns against (keep donations "voluntary and unrelated to access, features, updates or support"). A passive footer on general app engagement satisfies that. Both READMEs carry the maintainer's Ko-fi/Buy Me a Coffee and GitHub Sponsors links.
10. Optimize the project for discoverability by both classic search engines and LLM-based answers. The public site now has unique titles/descriptions, canonical URLs, crawlable semantic HTML, robots/sitemap discovery, `WebSite` plus `MobileApplication` JSON-LD, social metadata, explicit install links and a short factual `llms.txt`; the root README names the supported motorcycle, operations, adapter and safety limits. Never trade accuracy or the private-logs boundary for reach. Still open after the first release: submit the sitemap in Google Search Console and review the GitHub project topics/About text.
11. ~~Support motorcycles set to miles~~ — **implemented in v0.7.0 from successful 2026-08-22 captures and release-enabled in v0.8.0.** Reads remain canonical kilometres. Interval writes are unit-dependent: `0x33` carries hundreds of kilometres and `0x34` carries hundreds of miles. Because no reliable read reports the dashboard unit, the app asks the rider to select the unit currently shown and does not use phone locale. One project-app miles reset remains a useful post-release validation check.
12. ~~Run the "flip the dash unit, capture twice" experiment~~ **Done 2026-08-13** (journal `session-1786622057643`). The user reset in km, switched the dash to miles, and reset again at the same odometer. Reads came back byte-identical (canonical); the km-scaled interval write was rejected in miles mode. See the 2026-08-13 trip section and milestone 11 above. Note the experiment ended up including `33`/`5C` writes (the user performed resets, not read-only captures) — which is what surfaced the write-path result; the km reset was also the first project-app service reset validated on hardware.
13. ~~Add a write-capable release acknowledgement~~ — **done in v0.8.0.** The persistent first-launch gate states that the project is unofficial and has no warranty, reminder reset is not maintenance, clearing a code is not a repair, and use is the owner's responsibility. Qualified Spanish/EU review remains recommended by `LEGAL_RESTRICTIONS.md`; do not present the acknowledgement as eliminating liability.
14. Distribute the free noncommercial community build via **signed GitHub release APKs** with a published SHA-256 checksum, each built from a public source tag (the flow `LEGAL_RESTRICTIONS.md` already requires). No CI is planned — this is a single-developer project with no remote — so a tagged local build, not a pipeline, is the release mechanism. F-Droid is not a distribution path under the current noncommercial source-available license. A separately licensed official Google Play build may be considered only after the DTC-clear and miles-reset gates, store-policy checks, publisher agreement, and commercial legal/tax review are complete.
15. Replace the placeholder support URLs with the maintainer's real donation destination, then add the passive support footer described in milestone 9. Keep it unrelated to operation success, features or support entitlement.
16. Design an **explicitly opt-in, research-build-only** report upload path. Prefer HTTPS plus short-lived presigned object-storage uploads or a user-entered one-time access code; keep objects private, validate/redact server-side, rate-limit submissions and publish a retention/deletion policy. Never embed reusable Basic Auth or storage credentials in an APK, never upload main-app logs automatically, and never include VINs, Bluetooth addresses, serials or unrelated device data.

## Open questions

1. What exact engine-ECU and instrument part numbers and software versions produced the captures?
2. Which diagnostic-session and SecurityAccess steps are actually required for DTC clear (and for the DTC detail read)? The DTC count needs neither in the default session; the detail read and clear remain open hardware questions.
3. Is the now-known captured engine seed/key derivation actually required for DTC clear? It is not required for the validated default-session DTC count read. v0.8.0 executes it only in the exact-profile-gated, explicitly confirmed Beta clear flow; the next controlled motorcycle test should confirm whether the ECU requires it.
4. What are the precise semantics and prerequisites of instrument services `0x5E`, `0x0D`, `0x33` and `0x5C`?
5. Does `0D01` only read odometer data, and how are service date/distance fields represented across units and model variants?
6. Are service-reset order, timing or keepalives significant?
7. What is Triumph's OEM-confirmed description for captured DTC `0x157700` / inferred display `P1577-00`? The current dictionary has only a third-party-corroborated project paraphrase.
8. Which other module identities and software versions share these protocols?
9. Which MC-IOS GATT endpoint carries commands and responses, and what write type and fragmentation behavior does it use?
10. Are extended session and SecurityAccess required for all-groups DTC clear, and are there Triumph-specific side effects beyond erasing stored DTC information?
11. ~~Does the instrument cluster store distance/interval in one canonical unit or the rider's chosen unit?~~ **Answered for the captured Tiger:** reads are canonical kilometres; interval writes use service `0x33` in hundreds of km or `0x34` in hundreds of miles. Still open: whether any readable field reports the current dashboard unit; none is known, so the app asks the rider.

The project app has validated adapter readiness, the engine route, identifier reads, zero-count DTC read, instrument reads and the km service reset. Still unanswered by a project-app motorcycle run: nonzero detail decoding, DTC clear, the captured miles reset, exact module identity and whether SecurityAccess is truly required for clear.

Update a map only from a retained capture, a controlled test or an authoritative source. Preserve the raw observation and label every inference.
