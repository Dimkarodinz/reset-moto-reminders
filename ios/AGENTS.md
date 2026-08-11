# iOS application guide

## Scope

This folder owns the iOS application: iOS UI and lifecycle, CoreBluetooth transport, map loading, diagnostic orchestration, local persistence and iOS packaging/self-build instructions. These instructions supplement [`../AGENTS.md`](../AGENTS.md); the root safety and compatibility rules remain mandatory.

Do not place motorcycle protocol bytes or adapter UUIDs directly in application features. Load them from validated maps through typed models.

## Platform decision and deferred phase

Build the iOS app natively with Swift, SwiftUI and CoreBluetooth. Do not introduce Flutter/Dart merely to share the Android UI.

Full iOS implementation is deferred until both conditions are met:

1. The Android adapter-only vertical slice establishes the shared conceptual interfaces and state vocabulary.
2. The MC-IOS adapter-only `ATI` proof identifies a working GATT command/response channel and observed framing.

Until then, iOS work is limited to transport characterization, map/document maintenance and platform-neutral UX review. When implementation begins, reuse behavior, wording, maps and test scenarios—not Android Bluetooth code or forced pixel-identical layouts.

## Required project sources

| Source | Use |
| --- | --- |
| [`../AGENTS.md`](../AGENTS.md) | Project scope, shared architecture, safety gates and research status |
| [`../LEGAL_RESTRICTIONS.md`](../LEGAL_RESTRICTIONS.md) | Privacy, clean-room development, iOS distribution and release checklist |
| [`../adapter-maps/adaptermap.schema.json`](../adapter-maps/adaptermap.schema.json) | Adapter-map version-2 contract |
| [`../adapter-maps/vlinker-mc-ios.adaptermap.yaml`](../adapter-maps/vlinker-mc-ios.adaptermap.yaml) | iOS adapter profile: BLE advertisement, GATT layout and current validation state |
| [`../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml) | Current motorcycle/module protocol evidence |
| [`../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml) | Shared user-facing DTC dictionary with observed and reference-only entries kept distinct |
| [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) | CoreBluetooth discovery, GATT details and pending adapter-only proof |

Do not load [`../adapter-maps/vlinker-mc-android.adaptermap.yaml`](../adapter-maps/vlinker-mc-android.adaptermap.yaml) as an iOS transport profile. It uses Bluetooth Classic SPP/RFCOMM, which CoreBluetooth does not expose.

## Application boundaries

Keep these concerns separate:

```text
iOS UI and lifecycle
  -> use cases and safety gates
    -> adapter/ECU profile selection
      -> diagnostic session and command queue
        -> ELM framing
          -> CoreBluetooth transport
```

- CoreBluetooth scanning, connection, discovery and notifications belong in one transport component.
- The adapter layer connects, identifies and initializes the adapter according to its map.
- The diagnostic layer owns CAN/ISO-TP/UDS behavior selected from an ECU map.
- The UI never assembles raw diagnostic requests and never bypasses compatibility gates.
- Serialize all writes through one connection-owned queue.
- Keep the retained `CBPeripheral` and connection state outside individual views so view recreation cannot orphan a session.

## Current feature gates

- BLE discovery, connection, GATT discovery and notification evidence is available for the later native implementation; current iOS work remains transport characterization until the deferred-phase conditions are met.
- The MC-IOS command channel is not proven. No captured `ATI` write or adapter response exists.
- Until the adapter-only proof succeeds, the app must stop at `unsupported/unvalidated adapter transport` and send no motorcycle diagnostic command.
- DTC read remains experimental until the transport proof and minimum ECU session/security prerequisites are established.
- DTC clear and service reset remain disabled until exact module identity and software compatibility checks exist.
- Unknown maps, schema versions, characteristic layouts, adapter identities or module identities fail closed.

## iOS implementation rules

- Follow [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) for service discovery, notification setup and the one-time `ATI` proof.
- Resolve services and characteristics by UUID at runtime; captured ATT handles are evidence only.
- Let CoreBluetooth manage ATT MTU and connection parameters. Use `maximumWriteValueLength(for:)` rather than assuming captured sizes.
- Enable notifications through `setNotifyValue`, not by writing CCCD bytes directly.
- Preserve notification fragments in arrival order and declare an ELM response complete only after the prompt is observed and validated.
- Do not automatically probe both candidate GATT channels. Test the primary path once, preserve the capture, and test the fallback only in a separate adapter-only attempt.
- Cancel the connection on unexpected layout, response, timeout or application shutdown. Never silently replay an interrupted write.
- Redact peripheral identifiers, serial numbers and VINs from exported logs.

## UI safety behavior

- Reading DTCs must be distinct from clearing them.
- Clear must display the current codes, explain that faults are not repaired, and require explicit confirmation.
- Service reset must show current and requested values and explain that maintenance is not performed.
- Hide write actions for unknown or mismatched profiles; a warning alone is insufficient.
- Clearly distinguish Bluetooth failure, unsupported adapter transport, unsupported motorcycle profile, DTC memory cleared and fault repaired.

## Testing and distribution

- Use the root red–green–refactor TDD rule for all future iOS production behavior. Convert each GATT capture into a deterministic failing transcript test before implementing the behavior it proves.
- Start with map parsing, schema rejection, GATT fragmentation and sanitized transcript-replay tests.
- Use a fake transport for UI/use-case tests; hardware tests default to adapter-only or read-only.
- Test permission/state changes, restoration assumptions, timeout, disconnect, missing prompt and ambiguous write results.
- Never make an automated test clear DTCs or reset service state on a motorcycle.
- Keep captures and real identifiers outside test fixtures and release artifacts.
- Follow the iOS source/self-build and authorized-distribution limits in [`../LEGAL_RESTRICTIONS.md`](../LEGAL_RESTRICTIONS.md); do not publish a development-signed IPA.

### Planned no-fee distribution channels

Both channels avoid a paid Apple Developer membership for the maintainer and the user; neither requires one from the other side. Decided 2026-08-10.

1. **Source + Xcode self-build (primary).** The user clones the repository, selects their own free Apple ID as the Personal Team in Xcode and deploys to their iPhone over USB. Free-account constraints apply to the user: the signature expires after 7 days (redeploy to renew), at most 3 free-signed apps per device, and one App ID registration for this bundle identifier (the 10-per-week App ID cap is irrelevant to a single app). Requires a Mac.
2. **Unsigned `.ipa` on releases (nice-to-have).** Publish the prebuilt archive unsigned; iOS will not install it directly, but AltStore or SideStore sign it locally with the user's own Apple ID and auto-refresh the 7-day signature. Works without a Mac (AltServer runs on Windows; SideStore needs a computer only for initial setup). The `.ipa` must remain unsigned — publishing a development/Personal-Team-signed IPA breaches Apple's developer agreement and is already forbidden by the legal policy.

Paid-membership channels (App Store, TestFlight, EU alternative marketplaces such as AltStore PAL, EU Web Distribution) are out of scope unless the maintainer later buys the membership; as sole copyright holder the maintainer can add a GPL App Store exception at that time.

When a capture changes protocol knowledge, update the relevant map first, validate it, then update implementation and tests. Do not encode a discovery only in iOS code.
