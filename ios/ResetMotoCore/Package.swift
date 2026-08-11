// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "ResetMotoCore",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "ResetMotoCore", targets: ["ResetMotoCore"]),
    ],
    targets: [
        .target(
            name: "ResetMotoCore",
            resources: [.process("Resources")]
        ),
        .testTarget(name: "ResetMotoCoreTests", dependencies: ["ResetMotoCore"]),
    ]
)
