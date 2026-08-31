# Supported adapter connections on iOS

## Current status

The 2026-08-08 nRF Connect screenshots and Bluetooth HCI capture establish discovery, the complete GATT layout and notification setup. That capture did not include an `ATI` exchange. The 2026-08-23 production-app run subsequently reached **Motorcycle connected**, which requires an acknowledged, prompt-complete and accepted `ATI` exchange on the primary split channel. This establishes `0x2AF1` for commands and `0x2AF0` for responses, although the exact identity text and fragment transcript were not retained.

Use [`adapter-maps/vlinker-mc-ios.adaptermap.yaml`](../adapter-maps/vlinker-mc-ios.adaptermap.yaml) as the machine-readable source of truth. Treat handles as evidence only; resolve services and characteristics by UUID at runtime because handles can change with firmware.

OBDLink CX support is also present as an experimental profile. Its machine-readable source is [`adapter-maps/obdlink-cx.adaptermap.yaml`](../adapter-maps/obdlink-cx.adaptermap.yaml). The app selects it only when both the advertised name and service match, then validates the returned `ATI` or `STI` identity before sending motorcycle traffic.

## OBDLink CX experimental BLE profile

The CX uses service `FFF0`, notifications on `FFF1`, and writes on `FFF2`. The app sizes chunks from CoreBluetooth's reported maximum write length, caps them at the documented 244-byte payload, sends each chunk with response and waits for acknowledgement. Characteristic `FEF5` is internal and must not be used.

CX support remains labelled experimental until a powered-adapter and motorcycle run proves discovery, identity validation, initialization, dashboard read, DTC read, DTC clear and service reset. No other OBDLink model inherits this profile. OBDLink MX+ requires a separate manufacturer-supported iOS transport integration; OBDLink LX does not support iOS. Neither is enabled by the CX work.

## Adapter fingerprint

| Field | Observed value |
| --- | --- |
| Advertised local name | `vLinker MC-IOS` |
| Advertised service | `0x18F0` |
| Manufacturer | `JINXUSOLU` |
| Model | `JXSL-MIC65LE` |
| Hardware revision | `2.1` |
| Software revision | `5.8.2,20260330` |
| Firmware revision | Empty |
| Appearance | `128` — Generic Computer |
| Preferred connection parameters | No minimum/maximum requested; latency `0`; no supervision timeout requested |
| Effective ATT MTU in capture | `23` — inferred because no MTU exchange occurred |
| Observed connection updates | `7.5 ms`, then `45 ms`; latency `0`; supervision timeout `5000 ms` |

The captured Bluetooth address and Device Information serial number are private evidence and are intentionally omitted. Do not identify adapters by MAC address or serial number.

## GATT map

| Service | Characteristic | Properties | Observed value handle | Descriptor |
| --- | --- | --- | --- | --- |
| `0x1801` Generic Attribute | `0x2A05` Service Changed | Read | `0x0003` | None |
| `0x1800` Generic Access | `0x2A00` Device Name | Read | `0x0006` | None |
| `0x1800` Generic Access | `0x2A01` Appearance | Read | `0x0008` | None |
| `0x1800` Generic Access | `0x2A04` Preferred Connection Parameters | Read | `0x000A` | None |
| `0x18F0` vendor service | `0x2AF0` | Notify, Indicate | `0x000D` | CCCD `0x2902`, handle `0x000E` |
| `0x18F0` vendor service | `0x2AF1` | Write, Write Without Response | `0x0010` | `0x2902` observed at handle `0x0011`; do not use it for notifications |
| `0x180A` Device Information | Standard Device Information characteristics | Read | `0x0014`–`0x0024` | None |
| `e7810a71-73ae-499d-8c15-faa9aef0c3f2` | `bef8d6c9-9c21-4c9e-b632-bd58c1009f9f` | Read, Write, Write Without Response, Notify, Indicate | `0x0027` | CCCD `0x2902`, handle `0x0028` |

### Device Information values

| Characteristic | UUID | Value |
| --- | --- | --- |
| Manufacturer Name | `0x2A29` | `JINXUSOLU` |
| Model Number | `0x2A24` | `JXSL-MIC65LE` |
| Serial Number | `0x2A25` | Redacted from public documentation |
| Hardware Revision | `0x2A27` | `2.1` |
| Firmware Revision | `0x2A26` | Empty |
| Software Revision | `0x2A28` | `5.8.2,20260330` |
| System ID | `0x2A23` | Eight zero bytes |
| Regulatory Certification Data List | `0x2A2A` | Empty |
| PnP ID | `0x2A50` | Vendor source and all IDs zero |

## Confirmed key/value operations

Only these two data writes were observed:

```text
Purpose: enable notifications for characteristic 0x2AF0
Target descriptor UUID: 0x2902
Observed descriptor handle: 0x000E
ATT operation: Write Request
Value: 01 00
Result: Write Response; notifications shown as enabled

Purpose: enable notifications for custom characteristic bef8d6c9-9c21-4c9e-b632-bd58c1009f9f
Target descriptor UUID: 0x2902
Observed descriptor handle: 0x0028
ATT operation: Write Request
Value: 01 00
Result: Write Response; notifications shown as enabled
```

On iOS, do not write `01 00` to the CCCD directly. Call `setNotifyValue(true, for:)` on the characteristic and wait for `peripheral(_:didUpdateNotificationStateFor:error:)`. CoreBluetooth performs the CCCD write.

## iOS connection sequence

1. Create `CBCentralManager` and wait for `.poweredOn`.
2. Scan for `CBUUID(string: "18F0")`. Use `vLinker MC-IOS` only as an additional check, not as the sole identifier.
3. Retain the discovered `CBPeripheral`, stop scanning and connect.
4. Set the peripheral delegate and discover services `0x18F0` and `0x180A`.
5. Under `0x18F0`, discover `0x2AF0` and `0x2AF1`.
6. Reject the adapter profile if `0x2AF0` lacks Notify/Indicate or `0x2AF1` lacks Write/Write Without Response.
7. Call `setNotifyValue(true, for: responseCharacteristic2AF0)` and wait for successful notification-state confirmation.
8. Ask `maximumWriteValueLength(for: .withResponse)` before sending data. Do not request or assume an ATT MTU directly; CoreBluetooth manages it.
9. Send the session-local adapter-only `ATI` gate below before any motorcycle command.
10. Wait for the successful `didWriteValueFor` acknowledgement and append every `didUpdateValueFor` payload in arrival order. Treat the command as complete only after both the acknowledgement and a response ending with ASCII `>` (`0x3E`) arrive; either callback may arrive first. The production app has completed this gate, but the fragment shape was not retained.
11. Ignore callbacks that do not belong to the retained central/peripheral session. Disconnect after the proof, on app backgrounding, or on any error or unexpected response.

## Observed primary channel

The production app successfully completed its strict identity gate over the split `0x18F0` channel. The primary path is therefore observed; the exact returned identity remains unretained evidence.

> **Tooling:** [`ResetLightProbe/`](ResetLightProbe/) implements exactly this proof (both candidate channels, one shot each, JSONL journal). Prefer it over a manual nRF Connect attempt — it performs the sequence deterministically and captures the evidence.

```text
Status: project-app observed through the ATI gate
Enable notifications: service 0x18F0 / characteristic 0x2AF0
Write target: service 0x18F0 / characteristic 0x2AF1
Write type: withResponse first
Text command: ATI followed by carriage return
Hex payload: 41 54 49 0D
Expected response class: adapter identity text followed by >
```

If a future primary-channel attempt succeeds in writing but produces no notification, stop and preserve the evidence. The custom all-in-one characteristic remains a maintainer-probe-only fallback and must never be auto-tried in production code.

The application must continue reporting `unsupported/unvalidated adapter transport` and sending no motorcycle command whenever the primary path does not return an accepted adapter identity.

The production preview in [`ResetMotoReminders/`](ResetMotoReminders/) performs this primary-channel check at the start of every connection. It does not expose or auto-try the alternate channel. A recognizable `vLinker`/`ELM`/`STN` identity and complete prompt unlock the session; any failure disconnects before motorcycle traffic. The corrected v0.1.3 dashboard/odometer read is physically validated; a retained nonzero-DTC read/clear remains a separate operation-level test.

## Screenshot coverage

All eight screenshots were reviewed:

1. Complete five-service list and connected adapter identity.
2. nRF Connect's local Server tab; no additional remote-adapter data.
3. Generic Attribute and Generic Access characteristics and values.
4. `0x18F0`, `0x2AF0`, `0x2AF1`, properties and notification state.
5. Device Information manufacturer, model and serial.
6. Device Information hardware/software/System ID values.
7. PnP data and the custom service/characteristic.
8. nRF action menu showing that MTU could be requested; it does not show an MTU request or negotiated value.

## References

- [Apple `CBCentralManager`](https://developer.apple.com/documentation/corebluetooth/cbcentralmanager)
- [Apple `CBPeripheral`](https://developer.apple.com/documentation/corebluetooth/cbperipheral)
- [Apple `writeValue(_:for:type:)`](<https://developer.apple.com/documentation/corebluetooth/cbperipheral/writevalue(_:for:type:)>)
- [Apple `maximumWriteValueLength(for:)`](<https://developer.apple.com/documentation/corebluetooth/cbperipheral/maximumwritevaluelength(for:)>)
