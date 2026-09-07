# Android application guide

## Scope

This folder owns the Android application: Android UI and lifecycle, permissions, Bluetooth transports, internal profile loading, orchestration of the bounded product operations, local persistence and Android packaging. These instructions supplement [`../AGENTS.md`](../AGENTS.md); the root safety and compatibility rules remain mandatory.

The separate [`../research-builds/android/triumph`](../research-builds/android/triumph) and [`../research-builds/android/general`](../research-builds/android/general) applications compile the `adapter`, `diagnostics`, `domain`, `logging`, `profiles`, and `transport` source directories from this app directly. Changes in those shared directories must keep all three Gradle modules green; do not copy them into the research folders.

This app also owns the Android launcher family source of truth: `app/src/main/res/mipmap-*/ic_launcher_foreground.png` and `app/src/main/res/values/colors.xml`. Both research builds copy those exact resources through `generateSharedLauncherResources` and layer only a center-symbol overlay. Keep the outer gauge, reset arrow, speed marks, scale, teal background and yellow accent shared; do not maintain research-specific approximations.

Do not place motorcycle protocol bytes or adapter UUIDs directly in application features. Load them from validated maps through typed models.

## Platform decision and active phase

Android is the first implementation target. Build it natively with Kotlin and Jetpack Compose, using Android Bluetooth APIs directly. Do not introduce Flutter/Dart or a custom cross-platform runtime.

The main app uses the fixed `ResetMotoTheme`: near-black background, restrained graphite surfaces, muted teal actions and high-contrast text/system bars. Keep this serious dark presentation independent of the phone theme; safety warnings continue to use the Material error role.

The v0.11.0 release (`versionCode 15`) packages the family-composed motorcycle catalogue in [`../ecu-maps/README.md`](../ecu-maps/README.md), experimental OBDLink CX BLE support and separate experimental Android-only OBDLink MX, LX and MX+ Classic profiles. It retains the one-time safety acknowledgement, bounded writes, packaged legal notices and the separation from broader research capture:

```text
launch -> accept safety notice once -> select or pair adapter -> connect -> identify
       -> initialize -> show adapter ready
       -> read dashboard information (read-only odometer/status proof)
       -> read trouble codes (default-session confirmed-DTC read, decoded)
       -> clear trouble codes, Beta (arm -> confirm; gated write)
       -> reset service reminder in km or miles (arm -> confirm; gated write)
       -> disconnect
```

The dashboard card sends only the validated `5E01`/`0D01` reads and shows the decoded odometer/status; it never writes. The DTC read performs the observed default-session confirmed-DTC read and decodes it against the packaged dictionary. A reported/decoded count mismatch surfaces a recoverable failure without tearing down the connection. The main app deliberately omits broad capture and report-sharing controls.

**Bounded release writes.** DTC clear (`DtcClearService`) and service-reminder reset are enabled only when `writesEnabled` and every required packaged profile is present. DTC clear sends its clear request exactly once and verifies the count; response-pending never causes a resend. The validated original-TFT reset remains in `ServiceReminderResetService` with its exact `ClusterFingerprintGate`, captured km/miles commands and response echoes unchanged. `CombinedServiceReminderResetService` owns the separate experimental updated-TFT and hybrid paths: timing-selected instrument SecurityAccess, live `A500`/`A000`/`A010` precursor reads, one explicit ISO-TP `2EA000` sequence and post-write read-back. A failure after the first write frame is ambiguous and is never retried. All writes require explicit arm→confirm. DTC clear remains labelled **Beta**; the two new service paths are labelled experimental through their motorcycle profiles until physically validated. The user-facing name is **Reset Moto Reminders**; the package remains `dev.resetlight`.

**Whole-operation serialization.** `ElmCommandSession` serializes individual commands, while `AdapterOperationGate` serializes the complete user action. Do not weaken this to command-only locking: a dashboard or engine action must not change the adapter's CAN route between another action's fingerprint/read and write. The UI disables every other motorcycle action and Disconnect while the lease is held, and the owner independently rejects concurrent or disconnect calls. A lost response after a write is an ambiguous result, not a normal failure: tear down the unhealthy connection, tell the rider to inspect or re-read before retrying, and preserve the specific operation outcome. If the service interval echo is accepted but the date echo is not, report a partial update rather than “nothing was written.”

Build history: v0.4.0 (`versionCode 5`) completed the first motorcycle capture; v0.5.0 (`versionCode 6`) added the reads; v0.6.0 (`versionCode 7`) added the two gated writes; v0.6.1 (`versionCode 8`) fixed live framing, stale routing, failure handling and pre-I/O validation; v0.6.2 (`versionCode 9`) added service-reset input/date validation and localization; v0.6.3 (`versionCode 10`) recorded the first project-app km reset; v0.7.0 (`versionCode 11`) added the captured miles path and dashboard proof; **v0.8.0** (`versionCode 12`) enables the bounded writes for the signed public build and adds persistent first-launch acknowledgement plus packaged legal notices. Physical validation still pending: nonzero DTC detail, DTC clear, and one miles reset through the project app.

Real motorcycle journals exist under dated `logs/` folders; the 2026-08-22 folder also retains minimized private HCI evidence for successful km and miles resets. All are local-only and gitignored. Do not treat bonded-list visibility as proof of a connection — a useful journal contains `connection_state`, paired `elm` outbound/inbound events and a terminal operation event. Pull journals with `adb` and analyze with `../tools/journal-analyze.py`; its read-only allowlist intentionally flags write commands.

Keep the initial project structurally simple. Separate UI, use cases/profile gates, adapter session/framing and Bluetooth transport, but do not create independent Gradle modules until the code or tests justify them.

## Required project sources

| Source | Use |
| --- | --- |
| [`README.md`](README.md) | Local build, APK and adapter-only hardware-test instructions |
| [`../AGENTS.md`](../AGENTS.md) | Project scope, shared architecture, safety gates and research status |
| [`../LEGAL_RESTRICTIONS.md`](../LEGAL_RESTRICTIONS.md) | Privacy, clean-room development, APK distribution and release checklist |
| [`../adapter-maps/adaptermap.schema.json`](../adapter-maps/adaptermap.schema.json) | Adapter-map version-2 contract |
| [`../adapter-maps/vlinker-mc-android.adaptermap.yaml`](../adapter-maps/vlinker-mc-android.adaptermap.yaml) | Primary Android adapter profile: Bluetooth Classic SPP/RFCOMM |
| [`../adapter-maps/vlinker-mc-ios.adaptermap.yaml`](../adapter-maps/vlinker-mc-ios.adaptermap.yaml) | Optional Android BLE profile; command channel is still unverified |
| [`../adapter-maps/obdlink-cx.adaptermap.yaml`](../adapter-maps/obdlink-cx.adaptermap.yaml) | Experimental Android/iOS BLE UART profile from OBDLink's public developer notes; physical project validation pending |
| [`../adapter-maps/obdlink-lx-android.adaptermap.yaml`](../adapter-maps/obdlink-lx-android.adaptermap.yaml) | Experimental Android Classic profile; exact `OBDLink LX` name plus `STDI`/`STI` identity |
| [`../adapter-maps/obdlink-mx-plus-android.adaptermap.yaml`](../adapter-maps/obdlink-mx-plus-android.adaptermap.yaml) | Experimental Android Classic profile; exact `OBDLink MX+` name plus `STDI`/`STI` identity |
| [`../ecu-maps/README.md`](../ecu-maps/README.md) | Family/profile composition and the capability-specific experimental matrix |
| [`../adapter-maps/obdlink-mx-android.adaptermap.yaml`](../adapter-maps/obdlink-mx-android.adaptermap.yaml) | Experimental Android-only original MX Bluetooth Classic profile; exact-name, physical-button pairing and identity gates; physical project validation pending |
| [`../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml) | Current motorcycle/module protocol evidence |
| [`../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml) | Publishable user-facing DTC code-to-message lookup |
| [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) | Android pairing, RFCOMM, framing, initialization and failure handling |
| [`.claude/plans/initial-version.md`](.claude/plans/initial-version.md) | Reviewed implementation plan for the initial Android research version |
| [`../research-builds/android/triumph/README.md`](../research-builds/android/triumph/README.md) | Separate Triumph compatibility collector, opt-in write validation, report workflow and build instructions |
| [`../research-builds/android/general/README.md`](../research-builds/android/general/README.md) | Separate bounded, read-only standard-OBD collector for unmapped motorcycle families |

The MC-Android map remains the hardware-validated Android transport. OBDLink CX is an independent experimental BLE profile, never a vLinker fallback. Android can technically access the MC-IOS BLE profile, but the application must keep that profile disabled beyond adapter-only characterization until its command channel is observed and validated.

## Application boundaries

Keep these concerns separate:

```text
Android UI and lifecycle
  -> use cases and safety gates
    -> internal adapter/motorcycle operation profile selection
      -> diagnostic session and command queue
        -> ELM framing
          -> Android Bluetooth transport
```

- Bluetooth Classic/RFCOMM and BLE/GATT are separate transport implementations behind one adapter transport interface.
- The adapter layer connects, identifies and initializes the selected adapter map.
- The diagnostic layer uses an internal profile to perform only DTC read, DTC clear or service-reminder reset. The application does not offer ECU-map management or ECU configuration.
- The UI never assembles raw diagnostic requests and never bypasses compatibility gates.
- Serialize all adapter commands through one connection-owned queue.
- Serialize each complete multi-command user operation through `AdapterOperationGate`; never queue a second user action behind a write without another explicit tap.
- Treat Android activities, fragments and composables as replaceable presentation; do not bind socket ownership to a screen instance.

## Current feature gates

- The product scope is adapter connection, the bounded read-only dashboard proof, DTC read/clear and service reminder reset. The main app shows no broad capture or report-sharing actions.
- DTC read sends only the observed default-session confirmed-DTC requests and decodes them. It never sends SecurityAccess, DTC clear or service-reminder commands.
- The two writes are release-enabled but remain strictly gated. DTC clear requires the packaged engine profile and sends its clear request once. Original-TFT service reset still requires `ClusterFingerprintGate`; combined service reset requires a complete selected instrument family, positive session/security responses and valid live precursor shapes. Both require explicit confirmation and never retry an ambiguous write.
- Project-code replay is covered by automated tests. Hardware-validated so far: default-session DTC count, instrument reads and the full km service reset. The successful miles command is capture-validated and implemented. Not yet validated through this app: nonzero DTC detail, DTC clear and a miles reset.
- DTC clear stays labelled Beta until its engine identity/prerequisite assumptions are hardware-validated. The miles path is release-enabled from captured successful traffic and deterministic replay, with its first project-app motorcycle run still pending.
- The MC-IOS BLE path must not send ECU commands until the pending `ATI` proof establishes its command/response endpoint and framing.
- The CX path must match `OBDLink CX` plus `FFF0`/`FFF1`/`FFF2`, enable OS-managed bonding, negotiate MTU, serialize acknowledged chunks and pass `ATI` before motorcycle operations. Keep it labelled experimental until a powered CX/Tiger test succeeds.
- The original MX path must match the exact bonded name `OBDLink MX`, never `OBDLink MX+`, use only the selected profile's standard SPP UUID, then pass the manufacturer-defined `STDI` hardware identity and original-MX `STN115` firmware-family gates. Pairing requires the physical Connect button and Android settings, not a guessed PIN. Keep it labelled experimental until a powered original-MX/Tiger test succeeds.
- LX and MX+ are independent Android Classic profiles. Their exact bonded names and `STDI` product identities must not match each other or fall back to the original MX profile.
- Motorcycle selection is available only while disconnected. Resolve engine and instrument families atomically, show only declared capabilities, and repeat the experimental warning at write confirmation.
- Experimental modern DTC clear performs the redacted `F18C` identity read, sends `14FFFFFF` once and verifies with `190108`. The validated 2021 profile retains its captured security-access strategy.
- Updated-generation Tiger 900 and Tiger Sport 660 retain explicit combined encodings under one reusable service. Other listed modern Triumph groups use an adaptive combined family that selects updated TFT only for an exact 12-byte `A000` data response and hybrid only for an exact 5-byte response. Any other response blocks before the reminder write; the app never retries with the alternate encoding. All combined paths are executable and unit-tested but remain experimental until a real motorcycle run confirms the complete write and read-back.
- Unknown maps, schema versions, adapter identities or module identities fail closed.

## UX starting point

Polished UI mockups are not required before the first build. Begin with a lightweight approved flow covering adapter selection/pairing, connection progress, adapter identity/readiness, actionable errors and disconnect. Use standard accessible Compose components and neutral styling until visual direction exists. The v0.6.0 DTC-clear and service-reset cards use a two-step arm→confirm affordance (shared `ArmedConfirmation` composable) with a red warning line; keep any future write confirmation identical and minimal rather than elaborate.

## Android implementation rules

- Follow [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) for version-dependent Bluetooth permissions and socket behavior.
- Prefer bonded-device selection for the MC-Android adapter. When no bond exists, instruct the user to pair `vLinker MC-Android` in Android Settings with the confirmed PIN `1234`; do not implement hidden or reflection-based automatic pairing.
- The original OBDLink MX is also selected from Android's bonded list. Instruct the user to press its physical Connect button and pair `OBDLink MX` in Android Settings within the documented window; do not request or guess a PIN.
- Cancel discovery before opening RFCOMM.
- Perform connection and stream I/O away from the main thread with explicit cancellation and bounded timeouts.
- Keep one reader active for the socket lifetime and reassemble ELM responses until the prompt.
- Never use hidden Bluetooth APIs, hardcoded RFCOMM channels, raw HCI frames or dynamic L2CAP identifiers in application code.
- Close transports deterministically on cancellation, lifecycle shutdown and error; never silently replay an interrupted write.
- Redact MAC addresses, VINs and serial numbers from exported logs.

## UI safety behavior

- Reading DTCs must be distinct from clearing them.
- Clear must display the current codes, explain that faults are not repaired, and require explicit confirmation.
- Service reset must show current and requested values and explain that maintenance is not performed.
- Hide write actions for unknown or mismatched profiles; a warning alone is insufficient.
- Clearly distinguish connection failure, unsupported adapter, unsupported motorcycle profile, DTC memory cleared and fault repaired.

## Testing and releases

- Follow strict red–green–refactor TDD in the small steps defined by [the initial-version plan](.claude/plans/initial-version.md). Confirm each new test fails for the intended reason before adding production behavior.
- Start with map parsing, schema rejection, response fragmentation and sanitized transcript-replay tests. A hardware discovery must become a deterministic transcript test before it changes the implementation.
- Test cancellation, timeout, remote disconnect, missing prompt, response pending and ambiguous write results.
- Hardware tests default to adapter-only or read-only. Never make an automated test clear DTCs or reset service state on a motorcycle.
- Keep captures and real identifiers outside test fixtures and release artifacts.
- Any distributed APK must satisfy the binary and release requirements in [`../LEGAL_RESTRICTIONS.md`](../LEGAL_RESTRICTIONS.md).

When a capture changes protocol knowledge, update the relevant map first, validate it, then update implementation and tests. Do not encode a discovery only in Android code.
