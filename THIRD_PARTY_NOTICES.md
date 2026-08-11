# Third-party notices

The PolyForm Noncommercial License in [`LICENSE`](LICENSE) applies only to
material for which Dmytro Rodin can grant those rights. Third-party components
remain subject to their own licenses and notices.

## OBDex generic diagnostic descriptions

The English and German generic OBD-II titles in the DTC dictionary are a
filtered snapshot of OBDex data:

- Source: <https://github.com/foerbsnavi/OBDex>
- Revision: `bc58b0eb7273226a1aabae98e956b70b8362bda1`
- Data license: CC0 1.0 Universal
- Included records: 220 generic code titles

The upstream data dedication is preserved in
[`third-party/obdex/LICENSE-DATA`](third-party/obdex/LICENSE-DATA). These are
generic OBD-II explanations, not Triumph compatibility claims. Motorcycle-
specific codes without an independently reusable description use the app's
neutral subsystem fallback.

## Preliminary dependency inventory

The Android projects currently declare components from the following projects:

- AndroidX, Jetpack Compose, and the Android Gradle Plugin — Apache License 2.0.
- Kotlin, the Kotlin Gradle Plugin, and kotlinx.coroutines — Apache License 2.0.
- SnakeYAML — Apache License 2.0.
- Gradle and the Gradle Wrapper scripts — Apache License 2.0.

Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>

This is a preliminary direct-dependency inventory, not a complete binary or
transitive-dependency audit. Before any public source or APK release, generate
the resolved dependency list for every distributed build, preserve every
required copyright/NOTICE file, include required license text with the binary,
and verify that no dependency or bundled asset conflicts with the project's
distribution terms.

Nothing in the project-wide `LICENSE` overrides a third party's license or
copyright notice.
