# ResetLight Probe (iOS)

A one-screen maintainer tool that runs the **adapter-only `ATI` probe** from
[`../VLINK_CONNECTION.md`](../VLINK_CONNECTION.md) against the vLinker
MC-IOS: scan → connect → discover → enable notifications → write `ATI\r`
once → reassemble the response until the `>` prompt → journal everything as
JSONL. It never sends a motorcycle diagnostic command and never probes both
candidate channels automatically — one button per channel, one shot per app
launch, per the project safety policy.

The platform-neutral logic (profile constants transcribed from
[`../../adapter-maps/vlinker-mc-ios.adaptermap.yaml`](../../adapter-maps/vlinker-mc-ios.adaptermap.yaml),
fragment reassembly, JSONL journal) lives in [`../ProbeKit`](../ProbeKit)
and is tested headlessly:

```bash
cd ios/ProbeKit && swift test
```

This tool is deliberately English-only; the phone-locale localization rule
applies to the end-user apps, not maintainer research tooling.

## Deploying to an iPhone (free Apple ID, no paid membership)

1. Open `ios/ResetLightProbe/ResetLightProbe.xcodeproj` in Xcode. If Xcode
   offers to download the **iOS platform** component, accept (one-time,
   several GB) — building for a device requires it.
2. In the project editor → target **ResetLightProbe** → *Signing &
   Capabilities*: check *Automatically manage signing* and select your
   personal team (your free Apple ID, added via Xcode → Settings →
   Accounts).
3. Connect the iPhone over USB, select it as the run destination, press Run.
4. First-run phone steps: enable **Developer Mode** (Settings → Privacy &
   Security, iOS 16+; the phone prompts and reboots) and trust the developer
   profile (Settings → General → VPN & Device Management).
5. Free-signing expires after 7 days; re-run from Xcode to renew. Irrelevant
   for a probe you run the day you build it.

## Running the probe

1. Power the adapter (bench power is fine; if it must be on the motorcycle:
   ignition ON, engine OFF). Make sure no other app is connected to it.
2. Tap **Probe primary channel** (the split `18F0` pair — the documented
   first candidate). Wait for a terminal status.
3. Only if the primary channel produced no response: relaunch conclusions
   intact — the journal is already saved — and tap **Probe alternate
   channel** (the custom bidirectional characteristic) as a separate attempt.
4. Share the JSONL journal (AirDrop/Files) and drop it into `logs/<date>/`
   in the repository. The journal — not memory — is the evidence that
   updates the adapter map.

## What success unblocks

A response containing an adapter identity (e.g. `ELM327 v…`) followed by
`>` promotes that channel to `observed` in
`adapter-maps/vlinker-mc-ios.adaptermap.yaml` and satisfies the second
deferred-phase condition in [`../AGENTS.md`](../AGENTS.md), unblocking the
real iOS app implementation.
