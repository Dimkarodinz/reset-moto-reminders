# 2026-08-12 hardware trip — failure analysis and fixes

Journal: `logs/2026-08-12/session-1786454940306.jsonl` (171 events, 68 ELM exchanges,
first trip on the v0.6.0 research build with all four operations available).

## What the trip showed

| Step | Outcome |
|---|---|
| Connect + identify + initialize | OK (ELM327 v2.2 / STN1151 v4.3.2 match) |
| Read-only engine capture | **Complete** — all 6 identifiers match the map, DTC count 0 (mask 0x0C) |
| Instrument read | **Blocked (bug)** — live `5E01`→`704DE303433FFFFFFFF`, `0D01`→`7048D0100AE9C000000` are valid (status "043", odometer 0xAE9C = 44700 km) but the decoder rejected them |
| Mainline DTC read (after instrument read) | **Failed (two bugs)** — `03190108` sent on the instrument route → NO DATA → parse exception → **whole vLink session torn down** |
| Service reset | **Failed (two bugs)** — input validation `require(...)` threw before any byte was sent → session torn down again |
| Planned DTC-provocation experiment | **Not performed** — rider will repeat on a later trip (still pending) |

## Root causes

1. **Live framing mismatch (systemic).** The map records payload-only observed
   responses; unit tests replay them; but the live adapter (ATH1 + ATCAF0) returns
   `<CAN ID><ISO-TP PCI (engine only)><payload><padding>`:
   engine `18DAF1D5 06 59010C0000 00 AA`, instrument `704 DE303433FFFFFFFF`
   (an odd-length hex string, so `diagnosticHexBytes` throws before any prefix check).
   Only `ReadOnlyEngineCapture`'s tolerant marker parser survived on hardware. Affected:
   `DtcReader`/`DtcResponseDecoder`, `DtcClearService`/`UdsResponseParser`,
   `InstrumentResponseDecoder`, `ServiceReminderResetService` echo checks.
2. **No transport reconfiguration between modules.** `DtcReader` and `DtcClearService`
   send UDS requests on whatever route the adapter last used. After an instrument read
   (ATTP6/ATSH701/ATCRA704/ATCFC0) the engine request gets NO DATA.
3. **Operation errors tear the session down.** `runGatedOperation`'s catch-all calls
   `failSession`, so a parse or validation error disconnects a perfectly healthy
   Bluetooth session (observed three times).
4. **Service-reset input validation throws.** `ServiceReminderCommandBuilder.build`
   `require(...)` (interval multiple of 100 km, raw 1..255, year in one byte) throws
   `IllegalArgumentException` pre-I/O; via bug 3 that killed the connection.
5. **Analyzer allowlist stale.** `tools/journal-analyze.py` only knows engine capture
   commands, so it flags the legitimate instrument reads as CRITICAL.

## Fixes

1. New `diagnostics/CanResponseExtractor` — strips the module's response CAN ID,
   reassembles ISO-TP (single-frame; multi-frame first/consecutive) for the engine,
   returns raw 8-byte data for the instrument, detects `NO DATA`/errors as a typed
   `DiagnosticNoResponseException`, and passes bare payloads through unchanged
   (map-format inputs and ATH0 configs keep working). Wired into DtcReader,
   DtcClearService, InstrumentReadOnlyCapture, ServiceReminderResetService.
2. DtcReader + DtcClearService apply the engine configuration commands first,
   exactly like the capture; a rejected command blocks with the existing
   transport-rejected message.
3. `runGatedOperation` tears the session down only when the cause chain shows the
   transport is broken (IOException/CommandFailure/timeout/identity/security);
   diagnostic parse and validation errors mark the operation Failed and keep the
   session alive. NO DATA maps to a friendly localized message.
4. `ServiceReminderResetService` catches builder validation failures and returns
   `Blocked(SERVICE_RESET_REASON_INVALID_INPUT)`; the requested values are journaled
   on `service_reset_started` for future diagnosis.
5. Analyzer allowlist gains the instrument configuration + `5E01`/`0D01`.
6. `UiText.toString()` compacted for readable journals; version bump 0.6.1 (8).

## Tests

Transcript tests using this trip's real bytes: framed engine count response, framed
instrument responses (status "043" / odometer 44700), NO DATA handling, engine
transport configured before `03190108`, invalid reset input blocked with zero bytes
sent, framed DTC-clear handshake, owner keeps the session on diagnostic errors and
still tears down on IOException.

## Still pending on hardware (next trip)

- DTC-provocation experiment (provoke a fault, read nonzero count + detail,
  optionally clear) — not performed this trip.
- Instrument read validated end-to-end by the app (bytes now confirmed by hand).
- Service reset and DTC clear remain unexercised on hardware.
- "Flip the dash unit, capture twice" km/miles experiment (milestone 12).
