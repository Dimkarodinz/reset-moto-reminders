# General Motorcycle Research Android guide

This folder owns the separate **Motorcycle Research** Android application. It
supplements [`../../../AGENTS.md`](../../../AGENTS.md),
[`../../../android/AGENTS.md`](../../../android/AGENTS.md), and
[`../../../LEGAL_RESTRICTIONS.md`](../../../LEGAL_RESTRICTIONS.md).

## Scope and hard boundaries

The APK runs only the finite standard-read profile in
[`profiles/standard-obd-read.researchprofile.yaml`](profiles/standard-obd-read.researchprofile.yaml).
Every profile command must also pass `GeneralResearchCommandPolicy`; changing the
YAML alone cannot authorize another command.

- Keep this build read-only. Never add VIN (`0902`), Mode `04`, SecurityAccess,
  write-data, routine control, passive monitoring, CAN-address sweeps, DID scans,
  fuzzing or guessed manufacturer commands.
- Continue after an unsupported individual request. Stop on identity mismatch,
  timeout, transport failure or cancellation and preserve the partial report.
- Never log the private adapter address. Keep the existing text and raw-hex
  redaction active and expose reports only through explicit sharing.
- Treat results as evidence for the tested motorcycle only. A standard OBD
  response cannot establish service-reset or DTC-clear compatibility.
- Derive exact write candidates later from public technical information, an open
  implementation, passive capture or controlled CAN observation. Validate them
  in a separate exact-allowlisted manufacturer profile, never in this APK.

## Architecture

This module is included by [`../../../android/settings.gradle.kts`](../../../android/settings.gradle.kts)
and compiles the shared adapter, diagnostic, domain, logging, profile and
transport packages from the main Android app. Keep general-only orchestration,
policy and UI under `dev.resetlight.research.general`; do not copy shared classes.

The launcher reuses the main app's exact density-specific foreground PNGs and
background color through `generateSharedLauncherResources`.
`ic_general_research_launcher_overlay.xml` masks only the original needle/hub
and adds the optically centered `?`. Do not redraw or independently tune the
outer gauge, reset arrow, speed marks, scale or yellow accent; edit the main
launcher source when the whole icon family should change.

Use red-green-refactor TDD. Run this module plus the main and Triumph modules when
shared code changes. Installing and opening the APK is a phone-level smoke test;
never start a hardware scan automatically.
