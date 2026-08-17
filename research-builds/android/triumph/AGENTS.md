# Triumph Research Android guide

This folder owns the separate **Triumph Research** Android application. It supplements [`../../../AGENTS.md`](../../../AGENTS.md), [`../../../android/AGENTS.md`](../../../android/AGENTS.md), and [`../../../LEGAL_RESTRICTIONS.md`](../../../LEGAL_RESTRICTIONS.md); their privacy, evidence, and motorcycle-safety rules remain mandatory.

## Scope

The application gathers bounded compatibility evidence from Triumph motorcycles through the validated vLinker MC-Android transport. It asks for model and model year, runs the fixed read scan in [`PLAN.md`](PLAN.md), then optionally validates the two known writes in the same connection. It stores a sanitized JSONL report in app-private storage and lets the tester share that report explicitly.

It is not the user-facing Reset Moto Reminders application. Use package `dev.resetlight.research.triumph` and the visible name `Triumph Research` so both APKs can coexist.

## Hard boundaries

- Complete and journal the automatic read phase before any selected write validation. The read policy permits only the single observed, non-persistent `1003` DTC-read fallback.
- Never send VIN (`F190`), ECU serial (`F18C`), generic write (`2E`), routine control (`31`), OBD mode `04`, or any unlisted write. Enforce separate read and write policies with negative tests even if a packaged map changes.
- Optional write validation is limited to the exact mapped SecurityAccess/DTC-clear/count-verify sequence and exact kilometre `33xx`/`5Cxx` service-reset templates. Service validation is a round trip based on user-entered current values: write one profile step (`+100 km`) and `+1 day`, classify the echoes, then restore the entered values and classify restoration separately. It requires a matching precursor read, explicit selection/acknowledgement, and one session attempt.
- Attempt service restoration after either a confirmed temporary commit or an explicit ECU rejection while the connection remains healthy. On disconnect, timeout or ambiguous write, mark restoration unknown and stop; never send a speculative follow-up write or auto-retry the session. The protocol cannot read the current stored interval/date, so the UI must require accurate baseline entry and must never prefill assumed values.
- Never add arbitrary CAN-address, DID, routine, or key scanning.
- Treat every result as evidence for that motorcycle only, never automatic support for a model family. A rejection is useful evidence and not a prompt to probe alternative bytes.
- Keep kilometre values raw; do not add miles conversion or localization to this research app.
- Do not log Bluetooth MAC addresses. Never request VIN or serial values. Redact sensitive material in both text and ASCII-encoded raw-hex journal fields.
- Keep reports private until the user explicitly shares them. Never commit collected reports.
- Do not turn one successful motorcycle into a family-wide compatibility claim.

## Architecture and reuse

This is a Gradle application module stored outside the main Android directory and included by [`../../../android/settings.gradle.kts`](../../../android/settings.gradle.kts). It compiles the proven adapter, transport, diagnostic, profile, domain, and logging source directories from the main Android app directly. Do not copy those classes into this folder.

The launcher also reuses the main app rather than maintaining another gauge drawing. `generateSharedLauncherResources` copies the main density-specific `ic_launcher_foreground.png` files and launcher background color into generated resources; `ic_research_launcher_overlay.xml` masks only the original needle/hub and adds the centered `T`. Preserve this layering so later main-icon changes propagate automatically. Do not redraw or locally adjust the outer ring, arrow, speed marks, scale or yellow accent.

Research-only code stays under `dev.resetlight.research.triumph`:

```text
Compose screen
  -> ResearchSessionOwner
    -> TriumphResearchScanner + ResearchCommandPolicy
    -> ExperimentalWriteValidator + ResearchWriteCommandPolicy (opt-in)
      -> shared ElmCommandSession
        -> shared RFCOMM transport
```

Use strict red-green-refactor TDD. Hardware tests never start automatically; installing and launching with an unpowered adapter is only a phone-level smoke test.
