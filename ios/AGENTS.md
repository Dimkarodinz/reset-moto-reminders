# iOS application guide

## Scope

This folder owns the iOS application: iOS UI and lifecycle, CoreBluetooth transport, map loading, diagnostic orchestration, local persistence and iOS packaging/self-build instructions. These instructions supplement [`../AGENTS.md`](../AGENTS.md); the root safety and compatibility rules remain mandatory.

Do not place motorcycle protocol bytes or adapter UUIDs directly in application features. Load them from validated maps through typed models.

## Platform decision and current phase

Build the iOS app natively with Swift, SwiftUI and CoreBluetooth. Do not introduce Flutter/Dart merely to share the Android UI.

The native iOS preview is implemented in [`ResetMotoReminders/`](ResetMotoReminders/), with the tested platform-neutral protocol/use-case layer in [`ResetMotoCore/`](ResetMotoCore/). Reuse Android behavior, wording, maps and test scenarios—not Android Bluetooth code or forced pixel-identical layouts.

The production app has validated the MC-IOS primary GATT command channel, `ATI` identity gate and corrected motorcycle/dashboard read. It discovers only the primary `18F0` split channel, enables `2AF0` notifications, sends `ATI` through `2AF1`, and requires a recognizable adapter identity plus ELM prompt before enabling motorcycle features. Version 0.2.0 also includes OBDLink CX as a separate experimental profile from the manufacturer's public BLE interface; it is not physically validated by this project. Never add automatic fallback-channel probing. DTC clear and any service-write mode without retained project-app evidence keep their existing gates.

**Transport-characterization tooling (2026-08-13):** [`ResetLightProbe/`](ResetLightProbe/) remains the maintainer-only tool for testing the alternate channel or collecting a focused JSONL journal. Its platform-neutral logic lives in [`ProbeKit/`](ProbeKit/). The probe run against the powered adapter has not happened yet, and its earlier free signature may have expired.

## Required project sources

| Source | Use |
| --- | --- |
| [`../AGENTS.md`](../AGENTS.md) | Project scope, shared architecture, safety gates and research status |
| [`../LEGAL_RESTRICTIONS.md`](../LEGAL_RESTRICTIONS.md) | Privacy, clean-room development, iOS distribution and release checklist |
| [`../adapter-maps/adaptermap.schema.json`](../adapter-maps/adaptermap.schema.json) | Adapter-map version-2 contract |
| [`../adapter-maps/vlinker-mc-ios.adaptermap.yaml`](../adapter-maps/vlinker-mc-ios.adaptermap.yaml) | iOS adapter profile: BLE advertisement, GATT layout and current validation state |
| [`../adapter-maps/obdlink-cx.adaptermap.yaml`](../adapter-maps/obdlink-cx.adaptermap.yaml) | Experimental CX profile: `FFF0` UART, `FFF1` notifications, `FFF2` acknowledged writes and documented MTU/bonding rules |
| [`../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml`](../ecu-maps/tiger-900-gt-pro-2021.ecumap.yaml) | Current motorcycle/module protocol evidence |
| [`../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml`](../dtc-maps/triumph-tiger-900-gt-pro-2021.en.dtcmap.yaml) | Shared user-facing DTC dictionary with observed and reference-only entries kept distinct |
| [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) | CoreBluetooth discovery, GATT details, observed primary channel and remaining motorcycle proof |
| [`PLAN.md`](PLAN.md) | Reviewed production implementation plan and safety checklist |
| [`ResetMotoCore/`](ResetMotoCore/) | Typed generated profile, ELM/CAN/UDS logic, use cases and deterministic tests |
| [`ResetMotoReminders/`](ResetMotoReminders/) | SwiftUI application, CoreBluetooth session and Xcode project |
| [`tools/sync_profiles.rb`](tools/sync_profiles.rb) | Regenerates the bundled iOS JSON profile from shared YAML maps |
| [`tools/sync_localizations.rb`](tools/sync_localizations.rb) | Generates the five iPhone `Localizable.strings`/`InfoPlist.strings` bundles from shared Android wording plus iOS-only copy |
| [`tools/build_app_icon.swift`](tools/build_app_icon.swift) | Rebuilds the opaque 1024px iOS icon from the Android launcher foreground and shared teal background; the adaptive foreground is centered at the approved roughly 80% visible diameter |

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

- The SwiftUI app and all four feature flows are implemented and compile for simulator and physical iOS targets. The core has deterministic transcript tests; none of these results substitutes for a physical iPhone/motorcycle test.
- Version `0.2.0` (`build 7`) supports English, German, Spanish, French and Ukrainian, with English as the default/fallback. This covers UI copy, Bluetooth/diagnostic failures and known or generic DTC descriptions. Locale selection must never change commands, units or compatibility gates.
- The critical/high safety review requires both the CoreBluetooth write acknowledgement and the prompt-complete ELM reply, requires the configured CAN response ID for live diagnostic replies, invalidates DTC-clear eligibility as soon as a new read or clear begins, rejects callbacks from obsolete BLE objects, and closes the connection on app backgrounding. A state-changing write interrupted by backgrounding remains explicitly ambiguous and is never replayed.
- The MC-IOS primary command channel has been exercised by the production app through its `ATI` gate, although its exact response transcript was not retained. A session-local successful `ATI` gate remains mandatory; unexpected layout, missing prompt, timeout or unknown identity disconnects.
- CX selection requires its exact advertised name and UART service, then its exact characteristic layout and a recognizable OBDLink/ELM/STN `ATI` response. It uses sequential acknowledged chunks sized from CoreBluetooth's negotiated write limit and remains visibly experimental until hardware-tested.
- Dashboard and DTC reads, DTC clear (Beta), and service reset use the same captured Tiger 900 commands and response rules as Android. No other motorcycle profile is selectable.
- Service writes require the exact bundled motorcycle profile and live `043` instrument fingerprint. DTC clear retains the Beta label and explicit destructive confirmation.
- Unknown map schemas, characteristic layouts, adapter identities, instrument fingerprints or response shapes fail closed. Writes are never automatically retried after an ambiguous result.

## iOS implementation rules

- Follow [`VLINK_CONNECTION.md`](VLINK_CONNECTION.md) for service discovery, notification setup and the one-time `ATI` proof.
- Resolve services and characteristics by UUID at runtime; captured ATT handles are evidence only.
- Let CoreBluetooth manage ATT MTU and connection parameters. Use `maximumWriteValueLength(for:)` rather than assuming captured sizes.
- Enable notifications through `setNotifyValue`, not by writing CCCD bytes directly.
- Preserve notification fragments in arrival order and declare an ELM response complete only after the prompt is observed and validated.
- Do not automatically probe both candidate GATT channels. Test the primary path once, preserve the capture, and test the fallback only in a separate adapter-only attempt.
- Cancel the connection on unexpected layout, response, timeout or application shutdown. Never silently replay an interrupted write.
- The production app does not export logs. It may emit bounded Apple system-log events for state, command tags and outcomes, but never raw adapter/ECU replies, peripheral identifiers, serial numbers or VINs. The maintainer probe must apply the same redaction to any shared journal.
- Keep shared wording synchronized from Android with [`tools/sync_localizations.rb`](tools/sync_localizations.rb). Put iOS-only copy in that generator, require identical key and format-argument sets in every locale, and retain English fallback for an unsupported phone language.

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
- Run `swift test --package-path ios/ResetMotoCore` plus unsigned simulator and generic-device Xcode builds before handoff. Run `ruby ios/tools/sync_profiles.rb` after relevant YAML-map changes and `ruby ios/tools/sync_localizations.rb` after shared Android or iOS-only wording changes; review generated diffs, then rerun tests.

### Planned no-fee distribution channels

Both channels avoid a paid Apple Developer membership for the maintainer and the user; neither requires one from the other side. Decided 2026-08-10.

1. **Source + Xcode self-build (primary).** The user clones the repository, selects their own free Apple ID as the Personal Team in Xcode and deploys to their iPhone over USB. Free-account constraints apply to the user: the signature expires after 7 days (redeploy to renew), at most 3 free-signed apps per device, and one App ID registration for this bundle identifier (the 10-per-week App ID cap is irrelevant to a single app). Requires a Mac.
2. **Unsigned `.ipa` on releases (published as `ios-v0.2.0`).** The release includes the prebuilt archive and SHA-256 checksum. AltServer, AltStore or SideStore signs it locally with the user's own Apple ID; the public website uses direct AltServer sideloading as the shortest path and retains Xcode self-build as the alternative. The `.ipa` must remain unsigned — publishing a development/Personal-Team-signed IPA breaches Apple's developer agreement and is already forbidden by the legal policy.

Paid-membership channels (App Store, TestFlight, EU alternative marketplaces such as AltStore PAL, EU Web Distribution) are out of scope unless the maintainer or an authorized publisher later funds and satisfies that channel. A publisher legally separate from Dmytro Rodin must first receive an appropriate written commercial copyright and branding agreement; the public PolyForm Noncommercial license does not authorize commercial store distribution.

When a capture changes protocol knowledge, update the relevant map first, validate it, then update implementation and tests. Do not encode a discovery only in iOS code.
