# vLinker MC-Android connection on Android

## Current status

The retained 2026-08-08 Bluetooth HCI capture establishes the adapter's Bluetooth Classic service, RFCOMM channel, serial framing and common initialization sequence. These details were observed while third party ECU linker connected to a vLinker MC+ advertising as `vLinker MC-Android`.

Use [`adapter-maps/vlinker-mc-android.adaptermap.yaml`](../adapter-maps/vlinker-mc-android.adaptermap.yaml) as the machine-readable source of truth. This document explains how application code should use that data. Motorcycle-specific protocol selection, CAN headers, filters and diagnostic requests belong in an ECU map, not here.

The captured transport is covered by deterministic project-code replay tests. The v0.1 phone report also showed that pairing succeeded but the app did not refresh/select the bonded device after returning from Settings; v0.3.1 added a regression-tested refresh and selects the sole matching vLinker. Version 0.4.0 adds a debug-only, single-attempt read-only engine capture after adapter readiness and is installed on the Android 11 test phone. Every current app journal contains only `adapter_profile_loaded`, so project-app initialization and the read capture against the physical, powered adapter are still pending. Bonded-list visibility alone is not connection evidence.

## Next project-app evidence

Run the single consolidated test from [`README.md`](README.md). Its journal must show the connection transitions, exact adapter identities, ordered ELM traffic and one terminal read-capture event. If successful, it establishes the physical RFCOMM/ELM path, engine routing, returned non-sensitive identifiers, minimum observed DTC-read session and DTC response shape. It does not establish SecurityAccess, DTC-clear safety, instrument identity or service-reminder support.

## Adapter fingerprint

| Field | Observed value |
| --- | --- |
| Bluetooth name | `vLinker MC-Android` |
| Bluetooth transport | Bluetooth Classic ACL |
| Service | Serial Port Profile (SPP) |
| Service Class UUID | `00001101-0000-1000-8000-00805f9b34fb` (`0x1101`) |
| SDP PSM | `0x0001` |
| RFCOMM PSM | `0x0003` |
| RFCOMM server channel | `1` |
| Observed data DLCI | `2` |
| Negotiated RFCOMM maximum frame size | `640` octets |
| Adapter identity after `ATWS` | `ELM327 v2.2` |
| STN identity after `STI` | `STN1151 v4.3.2` |
| Pairing method | Pair through Android Settings |
| Pairing PIN | `1234` — user-confirmed for the observed adapter |
| Authentication | Succeeded in the capture |
| Link encryption | Enabled after authentication |

The captured Bluetooth address is private evidence and is intentionally omitted. Do not identify or authorize an adapter by MAC address alone. Use the SPP UUID, advertised name as an additional check, and the returned ELM/STN identity.

## Android permissions and pairing

Permission handling depends on the Android version:

- Android 12 and newer: request `BLUETOOTH_CONNECT` to list bonded devices and connect. Request `BLUETOOTH_SCAN` only if the app later implements discovery.
- Android 11 and older: declare the legacy Bluetooth permissions. Location permission and system Location may also be required only if discovery is implemented.
- Avoid discovery if the user has already paired the adapter. Listing bonded devices is faster and causes less radio traffic.

For the observed MC-Android adapter, first-time pairing was completed in Android Settings with PIN `1234`. The subsequent HCI capture proves that authentication and encryption succeeded. Treat the PIN as confirmed for this adapter profile, not as proof for every hardware or firmware revision.

1. First look for an already bonded device matching the adapter profile.
2. If none exists, direct the user to Android Settings and select `vLinker MC-Android`.
3. Enter PIN `1234` when Android requests it, complete pairing, then return to the app.
4. Refresh the bonded-device list and let the user select the paired adapter.
5. Never silently select a similarly named device when more than one candidate is present.

Do not use hidden APIs to pair automatically or embed reflection-based bonding code. The application may display the confirmed PIN and instructions, but Android Settings owns the pairing interaction.

## Connection sequence

1. Verify that Bluetooth is supported and enabled.
2. Obtain the permissions required by the running Android version.
3. Refresh the bonded-device list and let the user select the adapter. Discovery is a future optional path, not part of the current app.
4. Check the device name against `vLinker MC-Android` as a secondary fingerprint.
5. Cancel active discovery before opening the socket; discovery can slow or destabilize a connection.
6. Create an RFCOMM socket for SPP UUID `00001101-0000-1000-8000-00805f9b34fb`.
7. Connect off the main thread and expose a bounded connection timeout to the transport layer.
8. Obtain the socket input and output streams.
9. Start one reader that remains active for the lifetime of the socket.
10. Initialize the common adapter session one command at a time, waiting for the `>` prompt after every command.
11. Validate the returned ELM/STN identity before applying any ECU profile.
12. Hand the initialized serial transport to the ECU-profile layer.

On disconnect, stop pending work, close the socket once, release the streams, and return the connection to a clean state. The capture ended with HCI reason `0x13`, meaning the remote side terminated the connection; application code must also handle local close, remote close and unexpected link loss.

## Android RFCOMM API shape

The normal Android implementation uses a `BluetoothDevice` selected by the user and an RFCOMM socket created with the standard SPP UUID:

```kotlin
private val sppUuid = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

val socket = device.createRfcommSocketToServiceRecord(sppUuid)
bluetoothAdapter.cancelDiscovery()
socket.connect() // Run on an I/O dispatcher, never the main thread.

val input = socket.inputStream
val output = socket.outputStream
```

This is implementation guidance, not a complete connection manager. Production code still needs permission checks, cancellation, timeouts, lifecycle ownership, serialized writes and disconnect/error handling.

Do not hardcode dynamic L2CAP channel identifiers, the observed DLCI or raw HCI/RFCOMM frames. Android's Bluetooth socket API owns those layers. The application should consume and produce only the serial byte stream.

## Serial command framing

Adapter commands are ASCII terminated by carriage return:

```text
Command terminator: 0D
Response line terminator: 0D
Complete-response marker: 3E  (ASCII >)
```

A response can arrive in several stream reads. Reads are arbitrary chunks, not messages. Append bytes to a receive buffer and declare a response complete only when the ELM prompt `>` is received.

Do not assume that one write produces one read. Do not parse a partial line as a completed CAN response. Apply a maximum response size and a bounded timeout so a missing prompt cannot grow the buffer forever.

At the raw RFCOMM capture layer, credit octets such as `0x21`, `0x02` and `0x01` preceded application data. They are transport metadata, not ASCII. In particular, `0x21` must not be interpreted as an exclamation mark before `ATWS`. Android's socket stream should remove RFCOMM framing and credits; custom packet-level parsers must do so explicitly.

## Observed common initialization

This sequence configures and identifies the adapter only. Wait for a full prompt-terminated response after each command.

| Order | Command | Payload hex | Observed response | Purpose |
| --- | --- | --- | --- | --- |
| 1 | `ATWS` | `41 54 57 53 0D` | Echo, then `ELM327 v2.2` and `>` | Warm start and ELM identity |
| 2 | `ATE0` | `41 54 45 30 0D` | Echo, then `OK` and `>` | Disable command echo |
| 3 | `ATL0` | `41 54 4C 30 0D` | `OK` and `>` | Disable linefeeds |
| 4 | `ATS0` | `41 54 53 30 0D` | `OK` and `>` | Disable printed spaces |
| 5 | `STI` | `53 54 49 0D` | `STN1151 v4.3.2` and `>` | Read STN identity |
| 6 | `ATH1` | `41 54 48 31 0D` | `OK` and `>` | Enable CAN headers in responses |

`ATWS` and `ATE0` were echoed because echo was initially enabled. Commands after `ATE0` were not echoed.

Do not treat the exact identity strings as universal values for every vLinker MC revision. Store a compatibility rule that can distinguish validated identities from unknown ones, and preserve the full returned identity for diagnostics.

## Writer and reader behavior

Use a single serialized writer. For each command:

1. Confirm that no previous command is awaiting completion.
2. Encode ASCII and append exactly one carriage return.
3. Write and flush the payload.
4. Accumulate reader chunks until `>`.
5. Normalize echo, carriage returns and whitespace only after preserving the raw bytes for debug logging.
6. Return the complete raw and normalized response together.

Do not run two diagnostic commands concurrently over one ELM serial session. Keepalive scheduling, if an ECU profile requires it, must use the same writer queue rather than writing around an active request.

Suggested transport states:

```text
idle -> discovering/pairing -> connecting -> adapter_initializing
     -> ready -> disconnecting -> idle

Any state -> failed -> disconnecting -> idle
```

## Validation gates

Before the app sends motorcycle commands, require all of the following:

- The selected device exposes or successfully accepts the SPP connection.
- RFCOMM connects without using hidden/reflection APIs or a hardcoded channel.
- `ATWS` returns an ELM-compatible identity and a final prompt.
- `STI` returns the expected STN family for this validated adapter profile.
- Every common setup command reaches a final prompt within its timeout.
- The selected ECU map explicitly supports the adapter and identified motorcycle/module profile.

An unknown adapter may be offered an adapter-only identity check. It must not automatically inherit permission to clear DTCs or reset a service reminder.

## Failure handling

- Permission denied: explain which permission is missing and allow retry; do not loop on the system dialog.
- Bluetooth disabled: ask the user to enable it; do not repeatedly attempt connections.
- Pairing required: direct the user to Android Settings to pair `vLinker MC-Android` with PIN `1234`, then refresh bonded devices.
- Connection timeout or refusal: close the socket and recreate it for a later attempt.
- End of input or remote disconnect: fail the active command, close the connection and do not silently replay it.
- Missing `>` prompt: preserve the partial response, time out, then disconnect or recover only according to an explicit transport policy.
- Unexpected identity or response: stop before ECU initialization and report an unsupported adapter profile.
- ECU response-pending: this belongs to the diagnostic layer. It must not be mistaken for a missing ELM prompt or trigger an automatic retransmission.

Never automatically retry a write-side motorcycle operation after a disconnect or ambiguous response. Its result may be unknown even when the application did not receive the reply.

## Logging and privacy

Development logs should record:

- Connection-state changes and durations.
- Sanitized adapter name and identity strings.
- Command/response boundaries, raw serial bytes and decoded text.
- Timeouts, socket errors and whether a final prompt was received.
- The selected adapter-map and ECU-map versions.

Redact Bluetooth addresses, device serial numbers and motorcycle VINs before exporting logs. Do not publish complete HCI captures or Android bugreports: they can contain unrelated device, account and network information.

## Still unverified

1. Whether all hardware/firmware revisions advertising as `vLinker MC-Android` use PIN `1234` and return the captured ELM and STN identities.
2. The minimum initialization subset required for this project's read-DTC, clear-DTC and service-reset operations.
3. Appropriate command and connection timeouts under real motorcycle conditions.
4. Physical adapter initialization and the one guarded read-only engine capture by project code while the vLinker is powered from the motorcycle diagnostic port.

Resolve these through adapter-only or read-only tests before expanding compatibility claims.
