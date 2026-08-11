# ECU family maps

Motorcycle profiles compose two independent modules:

- an engine family for DTC read and clear;
- an instrument family for dashboard reads and service-reminder reset.

Sharing an engine route does not prove that two motorcycles share an instrument protocol. Commands live in family maps; `triumph.motorcycleprofiles.yaml` contains only model associations, capability status and strategy selection.

## Current profiles

| Motorcycle choice | DTC read | DTC clear | Dashboard | Service reset |
|---|---|---|---|---|
| Tiger 900 GT Pro (2021) | Validated | Validated Beta path | Validated | Validated |
| Other first-generation Tiger 900 / Tiger 850 Sport variants listed in the profile | Experimental | Experimental direct clear | Experimental | Experimental original-TFT reset |
| Updated-generation Tiger 900 | Experimental | Experimental direct clear | Unavailable | Experimental combined reset |
| Tiger Sport 660 | Experimental | Experimental direct clear | Unavailable | Experimental hybrid reset |
| Listed modern Street Triple, modern classics, Scrambler, 660/800, Speed Triple 1200 and Tiger 1200 groups | Experimental | Experimental direct clear | Unavailable | Experimental live-detected combined reset |

“Experimental” means the command path is implemented and unit-tested but has not yet been physically tested by this project on that motorcycle. Unknown DTCs still appear with a generic code rather than being discarded.

The updated-TFT and hybrid service families are executable and unit-tested. They use separate combined-write encodings, derive the instrument access key from the live seed, preserve required live fields, send one ISO-TP write sequence and read the service data back. The adaptive family uses the same route and selects one of those encodings only from the live `A000` data length: 12 bytes for updated TFT or 5 bytes for hybrid. Any other shape stops before the write. All combined paths remain experimental until they succeed on their listed motorcycles through this app.

## Rules

- The selected motorcycle cannot change while connected or during an operation.
- Capabilities are independent; an unavailable operation is not shown.
- Motorcycle names do not select an adaptive write format; the live instrument response does.
- The validated 2021 Tiger retains its captured security-access DTC clear.
- Experimental modern profiles use a live redacted engine-identity read, one direct clear request and a DTC-count verification.
- A lost response after a write is ambiguous and is never retried automatically.
- Model codes are used only for compatibility metadata. Full VINs and serial identifiers are never persisted.
