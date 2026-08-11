import Foundation

/// One GATT endpoint the probe talks to.
public struct GattEndpoint: Equatable, Sendable {
    public let serviceUuid: String
    public let characteristicUuid: String

    public init(serviceUuid: String, characteristicUuid: String) {
        self.serviceUuid = serviceUuid
        self.characteristicUuid = characteristicUuid
    }
}

/// A candidate command/response channel on the adapter. When
/// `isBidirectional` is true both endpoints are the same characteristic.
public struct ProbeChannel: Equatable, Sendable {
    public let name: String
    public let command: GattEndpoint
    public let response: GattEndpoint

    public init(name: String, command: GattEndpoint, response: GattEndpoint) {
        self.name = name
        self.command = command
        self.response = response
    }

    public var isBidirectional: Bool { command == response }
}

/// Typed transcription of `adapter-maps/vlinker-mc-ios.adaptermap.yaml`
/// (schema version 2) for the one-shot adapter-only `ATI` probe. The map is
/// the source of truth — update the map first, then this mirror, then the
/// pinning test in `ProbeProfileTests`. Both channels are
/// `proposed_unverified` in the map; this probe exists to promote one of
/// them to observed.
public struct ProbeProfile: Sendable {
    public let advertisedName: String
    public let advertisedServiceUuid: String
    public let primaryChannel: ProbeChannel
    public let alternateChannel: ProbeChannel
    /// ASCII `ATI` followed by a carriage return.
    public let identifyCommand: [UInt8]
    /// ASCII `>` — the ELM response-completion prompt.
    public let promptByte: UInt8
    public let responseTimeoutSeconds: TimeInterval

    public static let vlinkerMcIos = ProbeProfile(
        advertisedName: "vLinker MC-IOS",
        advertisedServiceUuid: "18F0",
        primaryChannel: ProbeChannel(
            name: "primary_18f0_split",
            command: GattEndpoint(serviceUuid: "18F0", characteristicUuid: "2AF1"),
            response: GattEndpoint(serviceUuid: "18F0", characteristicUuid: "2AF0")
        ),
        alternateChannel: ProbeChannel(
            name: "custom_bidirectional_characteristic",
            command: GattEndpoint(
                serviceUuid: "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2",
                characteristicUuid: "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F"
            ),
            response: GattEndpoint(
                serviceUuid: "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2",
                characteristicUuid: "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F"
            )
        ),
        identifyCommand: [0x41, 0x54, 0x49, 0x0D],
        promptByte: 0x3E,
        responseTimeoutSeconds: 5
    )
}
