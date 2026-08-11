# Legal and publication restrictions

Operational policy for contributors and agents; not legal advice. It assumes a maintainer in Spain and worldwide publication. Obtain qualified Spanish/EU advice before the first public release capable of writing to a motorcycle.

Last reviewed: 2026-08-11.

## Non-public material

Never commit, release, publish or paste into a public issue:

- Android bugreports, dumpstate archives, complete HCI logs or unrelated captured traffic.
- Account data, device identifiers, MAC addresses or nearby-device information.
- Third party ECU linker APKs, code, icons, screenshots, text, trace files or other assets.
- Triumph firmware, OEM/calibration maps, dealer software or copied service-manual content.
- Signing keys, Android keystores, Apple certificates/profiles, tokens or credentials.
- Decompiled security code, generic security-bypass tooling or immobilizer material. A concise, independently written seed/key transform may be retained only when the maintainer explicitly authorizes interoperability research, it is validated against the maintainer's own captured input/output pairs and third-party code/artifacts are not retained. The maintainer has authorized this transform (`EngineSeedKeyDerivation`) to execute — but only in the research build (`BuildConfig.RESEARCH_BUILD`), only on the engine-ECU DTC-clear path, and only after the exact-cluster fingerprint gate and an explicit user confirmation. It never runs in a release build or on any other path.

The archives under `logs/` are private evidence. Before the first public commit, move them outside the repository or exclude the complete directory. Publish only minimized, manually reviewed transcripts. If private material enters Git history, stop publication and purge the history; deleting the working-tree file is insufficient.

## Clean-room and branding rules

- Implement from independently observed inputs/outputs and public standards.
- Do not copy or translate thrird party apps implementation code, UI, wording or assets. Diagnostic identifiers and factual meanings may be retained only as independently written, concise project wording with source/version and reference-only status; they must not be presented as OEM-confirmed compatibility data.
- Do not use OEM firmware or calibration maps as an implementation source without legal approval.
- Stop for legal review before publishing decompiled code/assets, access-control-circumvention tooling, immobilizer material or a proprietary database. Private interoperability analysis explicitly authorized by the maintainer may retain only independently written factual results validated against first-party captures; delete temporary third-party artifacts afterward.
- Use Triumph/model names only to identify compatibility; do not use Triumph logos or imply endorsement.
- Display: `Unofficial project. Not affiliated with or endorsed by Triumph Motorcycles.`
- Never describe the application as official, dealer, certified or universally compatible.

Independently written source, sanitized protocol observations, documented profiles, original project assets, release-signed APKs and iOS source/self-build instructions may be published subject to these rules.

## Binary distribution

Every Android APK must be built from a public immutable tag, signed with the protected project key, accompanied by a SHA-256 checksum and contain no capture data, credentials or test identifiers.

Do not publish an IPA signed with a development, Personal Team, ad-hoc or enterprise profile. The no-fee iPhone method is source plus self-build instructions. Public installation must use an Apple-authorized channel such as the App Store, TestFlight for testing, an eligible alternative marketplace or authorized EU Web Distribution.

## Source license

The project is licensed under the GNU General Public License version 3 ([`LICENSE`](LICENSE)). Rationale: GPLv3 keeps the project genuinely open source (contributor-friendly, F-Droid-eligible) while making commercial repackaging pointless, because any distributor must publish complete corresponding source under the same license. The maintainer is the sole copyright holder and may later add an App Store exception or dual-license if Apple-channel distribution is pursued; source-plus-self-build iOS distribution needs no exception. New source files should carry a short GPLv3 notice header once public release preparation begins.

## Safety, liability and monetization

Licence disclaimers and warnings do not eliminate liability. Keep service writes disabled unless the instrument matches an explicitly validated profile and follow the safety rules in `AGENTS.md`. State that resetting a reminder does not perform maintenance.

Require legal review before releasing:

- Support for an unvalidated module identity or software version.
- ECU/security unlocking, seed/key, immobilizer, emissions, calibration, coding or firmware functionality.
- Paid features/support, commercial warranties, nonessential personal-data collection or professional-workshop positioning.

EU product-liability treatment distinguishes genuinely non-commercial FOSS from commercially supplied software. Keep donations voluntary and unrelated to access, features, updates or support; obtain advice if monetization becomes material.

## Release checklist

- [ ] `logs/`, bugreports, HCI/dumpstate files and their Git history are absent.
- [ ] No third-party APK, firmware, OEM map, copied screenshot/logo/text or proprietary asset is present.
- [ ] No secret, signing material, token, MAC address or personal identifier is present.
- [ ] Dependency licences and attribution requirements are satisfied.
- [ ] Compatibility claims match evidence and do not generalize from `Keihin` alone.
- [ ] Unknown or mismatched profiles cannot execute writes.
- [ ] Unofficial-project and safety warnings are visible.
- [ ] Each binary matches a public source tag and checksum.
- [ ] iOS distribution is authorized or source-only.
- [ ] New security-sensitive or commercial functionality received legal review.

## References

- Spanish Copyright Act, Article 100: <https://boe.es/buscar/act.php?id=BOE-A-1996-8930>
- Directive 2009/24/EC, Articles 5 and 6: <https://eur-lex.europa.eu/legal-content/EN/TXT/?uri=CELEX:32009L0024>
- Regulation (EU) 168/2013: <https://eur-lex.europa.eu/eli/reg/2013/168/oj/eng>
- EU Product Liability Directive 2024/2853: <https://eur-lex.europa.eu/eli/dir/2024/2853/oj/eng>
- Android alternative distribution: <https://developer.android.com/distribute/marketing-tools/alternative-distribution>
- Apple account limits: <https://developer.apple.com/help/account/basics/about-your-developer-account>
- Apple registered-device distribution: <https://developer.apple.com/documentation/xcode/distributing-your-app-to-registered-devices>
- Apple EU Web Distribution: <https://developer.apple.com/support/web-distribution-eu/>
- GitHub Acceptable Use: <https://docs.github.com/en/site-policy/acceptable-use-policies/github-acceptable-use-policies>
