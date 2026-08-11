import XCTest
@testable import ProbeKit

/// Pins the transcribed profile to the values documented in
/// `adapter-maps/vlinker-mc-ios.adaptermap.yaml`. If this test needs a
/// change, the map must have changed first.
final class ProbeProfileTests: XCTestCase {
    private let profile = ProbeProfile.vlinkerMcIos

    func testAdvertisementMatchesTheAdapterMap() {
        XCTAssertEqual("vLinker MC-IOS", profile.advertisedName)
        XCTAssertEqual("18F0", profile.advertisedServiceUuid)
    }

    func testPrimaryChannelIsTheSplit18F0Pair() {
        XCTAssertEqual(GattEndpoint(serviceUuid: "18F0", characteristicUuid: "2AF1"), profile.primaryChannel.command)
        XCTAssertEqual(GattEndpoint(serviceUuid: "18F0", characteristicUuid: "2AF0"), profile.primaryChannel.response)
        XCTAssertFalse(profile.primaryChannel.isBidirectional)
    }

    func testAlternateChannelIsTheCustomBidirectionalCharacteristic() {
        let endpoint = GattEndpoint(
            serviceUuid: "E7810A71-73AE-499D-8C15-FAA9AEF0C3F2",
            characteristicUuid: "BEF8D6C9-9C21-4C9E-B632-BD58C1009F9F"
        )
        XCTAssertEqual(endpoint, profile.alternateChannel.command)
        XCTAssertEqual(endpoint, profile.alternateChannel.response)
        XCTAssertTrue(profile.alternateChannel.isBidirectional)
    }

    func testIdentifyCommandIsAsciiAtiWithCarriageReturn() {
        XCTAssertEqual([0x41, 0x54, 0x49, 0x0D], profile.identifyCommand)
        XCTAssertEqual("ATI\r", String(bytes: profile.identifyCommand, encoding: .ascii))
        XCTAssertEqual(0x3E, profile.promptByte)
    }
}
