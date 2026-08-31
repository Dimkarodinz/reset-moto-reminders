# OBDLink CX support - implementation plan

## Validated design

The adapter and motorcycle are separate gates:

```text
OS discovery -> exact adapter profile -> adapter identity -> ELM command queue
                                                        -> exact Tiger profile
                                                           -> bounded reads/writes
```

The official CX UART service is sufficient to implement transport behavior, but not to claim physical validation. The release will therefore identify CX support as experimental. MX+/LX will receive documentation-only placeholders because their Android RFCOMM identity and initialization have not been exercised in this project; iOS MX+ access is not assumed.

Critical/high review corrections incorporated before implementation:

1. Do not add CX as a fallback channel to the vLinker profile. Give it an independent adapter profile and require an exact discovery/layout match.
2. Do not let GATT callbacks write directly to diagnostics. Adapt BLE to the existing single `ByteTransport`/ELM command queue.
3. Do not assume ATT MTU 247. Use Android's negotiated result and iOS `maximumWriteValueLength`, subtracting protocol overhead where the platform API requires it.
4. Do not queue multiple CX writes. Send one chunk, await its completion, then send the next; preserve write ambiguity for any state-changing operation interrupted after its first chunk.
5. Do not mark CX hardware-validated or silently broaden supported motorcycles. Keep the exact Tiger profile and publish an experimental-adapter label.
6. Do not release from the feature branch. Merge the reviewed PR, tag the merge commit, build from clean exact tags, upload artifacts, then update public links.

## TDD implementation sequence

1. **Adapter-map contract**
   - Red: add schema/loader tests for a BLE split-channel profile and unsafe/missing characteristic cases.
   - Green: add `obdlink-cx.adaptermap.yaml`; extend typed adapter models/loaders without weakening existing vLinker validation.
   - Refactor: expose transport-neutral discovery/channel/framing fields and retain Classic-only details as optional typed data.

2. **Adapter registry and selection**
   - Red: test exact matching for vLinker Classic, vLinker BLE and OBDLink CX, including duplicate names and unknown devices.
   - Green: load the platform's supported adapter profiles and select one explicit profile per discovered device.
   - Refactor: keep display identity separate from private Bluetooth addresses.

3. **Android CX BLE transport**
   - Red: pure unit tests for permission policy, scan filtering, GATT layout/properties, negotiated payload size, sequential chunk writes, notification fragments, timeout, cancellation and disconnect.
   - Green: implement a lifecycle-owned BLE facade and `GattByteTransport` behind `ByteTransport`; scan for `FFF0`, connect, discover `FFF1`/`FFF2`, enable notifications, request MTU and serialize acknowledged chunks.
   - Refactor: share prompt framing and connection failure semantics with RFCOMM while keeping platform APIs isolated.

4. **Android app integration**
   - Red: presenter/session tests for selecting the device's adapter profile and refusing stale/unknown selections.
   - Green: add BLE permissions and an in-app scan flow, connect through the chosen transport, run `ATI` and accepted STN/ELM identity checks, and retain all Tiger feature gates.
   - Refactor: preserve the simple connection UI and mark CX as experimental.

5. **iOS multi-adapter profile and transport**
   - Red: Swift tests for profile decoding, adapter choice, CX layout acceptance, write type/size selection and identity validation.
   - Green: generate vLinker and CX BLE profiles, scan both service UUIDs, bind the discovered peripheral to exactly one profile, discover its exact characteristics and send sequential acknowledged chunks using CoreBluetooth's maximum write length.
   - Refactor: move transport rules into tested platform-neutral helpers where practical; keep CoreBluetooth ownership in `AdapterSession`.

6. **Versions, copy and knowledge**
   - Bump Android to 0.9.0/versionCode 13 and iOS to 0.2.0/build 7.
   - Add localized adapter labels and concise experimental wording in English, German, Spanish, French and Ukrainian.
   - Update root/platform guides and adapter sources of truth; record that CX is documentation-implemented but hardware-unvalidated.
   - Prepare MX+/LX future-validation notes only; no executable profile or compatibility claim.

7. **Verification**
   - Run focused tests after every red-green step.
   - Run the Android main-app unit suite, lint and build; research apps are separate helpers and are not part of this release.
   - Run Swift package tests, localization/profile generation checks, simulator build and unsigned generic-device build.
   - Run website locale generation/checks and inspect all generated diffs.
   - Review for critical/high issues: callback races, stale peripheral/device selection, ambiguous writes, permission regressions, profile bypass and accidental private artifacts.

8. **Publication**
   - Commit implementation on `feature/obdlink-cx-support` using personal-project naming.
   - Push the branch, open a GitHub PR, review its diff/checks and merge it to `main`.
   - Create clean annotated tags `android-v0.9.0` and `ios-v0.2.0` on the merged commit.
   - Produce the maintainer-signed Android APK/checksum and unsigned iOS IPA/source ZIP/checksums from the exact tags.
   - Publish versioned GitHub Releases and verify each download.
   - Update website/README download links and compatibility wording only after release URLs exist; regenerate translations, commit through a follow-up PR if necessary, merge and verify GitHub Pages.

## Deferred physical validation

- Connect a powered OBDLink CX to the Tiger 900 and retain a sanitized adapter-only identity/initialization result.
- Validate dashboard and DTC reads before attempting either write.
- Validate service reset and Beta DTC clear separately under the existing motorcycle safety procedure.
- Only after those checks replace the experimental label with a hardware-validated claim.
