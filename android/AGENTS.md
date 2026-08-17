# Android application guide

## Scope

This folder owns the Android application: Android UI and lifecycle, permissions, Bluetooth transports, internal profile loading, orchestration of the three allowed operations, local persistence and Android packaging. These instructions supplement [`../AGENTS.md`](../AGENTS.md); the root safety and compatibility rules remain mandatory.

The separate [`../research-builds/android/triumph`](../research-builds/android/triumph) and [`../research-builds/android/general`](../research-builds/android/general) applications compile the `adapter`, `diagnostics`, `domain`, `logging`, `profiles`, and `transport` source directories from this app directly. Changes in those shared directories must keep all three Gradle modules green; do not copy them into the research folders.

Do not place motorcycle protocol bytes or adapter UUIDs directly in application features. Load them from validated maps through typed models.

## Platform decision and active phase

Android is the first implementation target. Build it natively with Kotlin and Jetpack Compose, using Android Bluetooth APIs directly. Do not introduce Flutter/Dart or a custom cross-platform runtime.

The current v0.6.3 build (`versionCode 10`) establishes adapter readiness, then exposes a mainline trouble-code read, two research-only reads, and — since v0.6.0 — two research-only **writes**:

```text
launch -> select or pair adapter -> connect -> identify
       -> initialize -> show adapter ready
       -> read trouble codes (mainline; default-session confirmed-DTC read, decoded)
       -> [research build] read-only engine capture (once) / instrument read (5E01, 0D01)
       -> [research build] clear trouble codes (arm -> confirm; SecurityAccess + write)
       -> [research build] reset service reminder (arm -> confirm; gated write)
       -> disconnect
```

The mainline DTC read performs the observed default-session confirmed-DTC read and decodes it against the packaged dictionary; it is read-only and never requests SecurityAccess or sends a write. A reported/decoded count mismatch surfaces a recoverable failure without tearing down the connection. The read-only engine capture and instrument read stay behind `RESEARCH_BUILD` and are absent from release presentation; the instrument read is first contact with the cluster and sends only the two observed reads.

**Research-only writes (v0.6.0).** DTC clear (`DtcClearService`) and service-reminder reset (`ServiceReminderResetService`) are the first paths that send writes. They exist only when `writesEnabled` (wired to `BuildConfig.RESEARCH_BUILD`) and are enforced in `AdapterSessionOwner` as well as hidden from the UI in release builds. DTC clear runs the observed extended-session → `2701` seed → derived `2702` key → `14FFFFFF` → count-verify sequence; the seed/key transform is executable only here (see the scoped exception in `../AGENTS.md`/`../LEGAL_RESTRICTIONS.md`). Service reset replays only the observed instrument bytes and is gated by `ClusterFingerprintGate`, which fails closed before any write byte. Both require an explicit in-app arm→confirm. **The service reset is hardware-validated for the km path** (full commit on the motorcycle 2026-08-13); DTC clear remains hardware-unvalidated. Automated transcript/scripted tests cover the DTC read, the capture allowlist and single-attempt behavior, the instrument decode/blocked paths, failure stops, the seed/key derivation against six retained pairs, the clear sequence (including response-pending and refusals), the fingerprint gate, and the service-reset commit/block paths — including replays of the real 2026-08-13 km commit and miles rejection (165 tests). The user-facing name is **Reset Moto Reminders**; the package remains `dev.resetlight`.

Build history: v0.4.0 (`versionCode 5`) was installed and launch-verified on the Samsung Android 11 phone and completed the first motorcycle capture; v0.5.0 (`versionCode 6`) added the reads; v0.6.0 (`versionCode 7`) added the two gated writes; **v0.6.1** (`versionCode 8`) fixed the four bugs the 2026-08-12 trip exposed (live `704`/ISO-TP framing via `CanResponseExtractor`, stale-route re-apply before DTC ops, session teardown now only on transport-fatal causes, reset input validation blocks instead of throwing); **v0.6.2** (`versionCode 9`) added domain-level service-reset input validation (`ServiceIntervalConstraints`, `NextServiceDateRules`) with a Material 3 date picker; **v0.6.3** (`versionCode 10`, installed on the phone) recorded the 2026-08-13 dash-flip evidence and made the miles-mode rejection message actionable (switch the dash to km and retry). Physical validation still pending: the DTC decode with a nonzero count, DTC clear, and any miles-mode write.

Real motorcycle journals now exist in `logs/2026-08-10/`, `logs/2026-08-12/` and `logs/2026-08-13/` (local-only, gitignored; each has a README). Do not treat bonded-list visibility as proof of a connection — a useful journal contains `connection_state`, paired `elm` outbound/inbound events and a terminal `*_finished`/`*_failed` event. Pull journals with `adb` and analyze with `../tools/journal-analyze.py`; its allowlist findings flag `33`/`5C` writes as CRITICAL by design (they are outside the read-only capture allowlist), which is expected on a reset trip, not an anomaly.

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
| [`../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml) | Current motorcycle/module protocol evidence |
| [`../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml) | Publishable user-facing DTC code-to-message lookup |
| [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) | Android pairing, RFCOMM, framing, initialization and failure handling |
| [`.claude/plans/initial-version.md`](.claude/plans/initial-version.md) | Reviewed implementation plan for the initial Android research version |
| [`../research-builds/android/triumph/README.md`](../research-builds/android/triumph/README.md) | Separate Triumph compatibility collector, opt-in write validation, report workflow and build instructions |
| [`../research-builds/android/general/README.md`](../research-builds/android/general/README.md) | Separate bounded, read-only standard-OBD collector for unmapped motorcycle families |

The MC-Android map is the active Android transport target. Android can technically access the MC-IOS BLE profile, but the application must keep that profile disabled beyond adapter-only characterization until its command channel is observed and validated.

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
- Treat Android activities, fragments and composables as replaceable presentation; do not bind socket ownership to a screen instance.

## Current feature gates

- The roadmap covers adapter connection, DTC read/clear and service read/reset. v0.5.0 made the default-session DTC read a mainline feature and kept the read-only engine capture and instrument read behind `RESEARCH_BUILD`. v0.6.0 adds DTC clear and service reset — both **research-build-only writes** behind explicit confirmation and the gates below; they are absent from release builds.
- The mainline DTC read sends only the observed default-session confirmed-DTC read and decodes it. It never sends SecurityAccess, DTC clear or service-reminder commands. The research capture adds non-sensitive identifier reads, DTC count/details and one conditional extended-session transition; the research instrument read sends only the observed `5E01`/`0D01` reads. None of these issue a write.
- The two v0.6.0 writes are gated: DTC clear requires the packaged clear/security profiles and runs the seed/key derivation only in the research build for the engine path; service reset requires `ClusterFingerprintGate` to pass (exact motorcycle profile + observed transport route + live `5E01` `043` status) before any write byte. Both need an explicit in-app arm→confirm and replay only observed bytes — no probes or speculative requests.
- Project-code replay is covered by automated tests. Hardware-validated so far: the default-session DTC count read (2026-08-10), the instrument reads `5E01`/`0D01` (2026-08-12/13) and the **full km-mode service reset** (2026-08-13). Not yet validated against a motorcycle: a nonzero decoded DTC read, DTC clear, and any miles-mode write.
- DTC clear remains experimental and research-build-only until its prerequisites (whether SecurityAccess is required + module identity) are validated against a motorcycle. Service reset is validated for the km path only; with the dashboard set to miles the cluster rejects the km-scaled interval write (`B3` then all-FF, observed 2026-08-13) and the app blocks with an actionable message — miles writes stay blocked until a successful miles-mode write is captured (the scaling must not be guessed).
- The MC-IOS BLE path must not send ECU commands until the pending `ATI` proof establishes its command/response endpoint and framing.
- Unknown maps, schema versions, adapter identities or module identities fail closed.

## UX starting point

Polished UI mockups are not required before the first build. Begin with a lightweight approved flow covering adapter selection/pairing, connection progress, adapter identity/readiness, actionable errors and disconnect. Use standard accessible Compose components and neutral styling until visual direction exists. The v0.6.0 DTC-clear and service-reset cards use a two-step arm→confirm affordance (shared `ArmedConfirmation` composable) with a red warning line; keep any future write confirmation identical and minimal rather than elaborate.

## Android implementation rules

- Follow [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) for version-dependent Bluetooth permissions and socket behavior.
- Prefer bonded-device selection for the MC-Android adapter. When no bond exists, instruct the user to pair `vLinker MC-Android` in Android Settings with the confirmed PIN `1234`; do not implement hidden or reflection-based automatic pairing.
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
