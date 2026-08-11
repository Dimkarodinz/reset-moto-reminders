// swift-tools-version: 5.9
import PackageDescription

// Platform-neutral logic for the MC-IOS adapter probe: profile constants,
// ELM response reassembly and the JSONL journal. macOS is included so the
// test suite runs headlessly with `swift test` — no simulator required.
let package = Package(
    name: "ProbeKit",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "ProbeKit", targets: ["ProbeKit"]),
    ],
    targets: [
        .target(name: "ProbeKit"),
        .testTarget(name: "ProbeKitTests", dependencies: ["ProbeKit"]),
    ]
)
