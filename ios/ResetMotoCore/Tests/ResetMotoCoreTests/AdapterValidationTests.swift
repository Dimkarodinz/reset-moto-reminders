import XCTest

@testable import ResetMotoCore

final class AdapterValidationTests: XCTestCase {
  func testExactGattCapabilitiesAreRequired() {
    XCTAssertTrue(
      AdapterValidation.acceptsGattLayout(
        responseCanNotify: true, commandCanWriteWithResponse: true))
    XCTAssertFalse(
      AdapterValidation.acceptsGattLayout(
        responseCanNotify: false, commandCanWriteWithResponse: true))
    XCTAssertFalse(
      AdapterValidation.acceptsGattLayout(
        responseCanNotify: true, commandCanWriteWithResponse: false))
  }

  func testUsableAtiIdentityMustBeARealAdapterReply() {
    XCTAssertTrue(AdapterValidation.acceptsIdentity("vLinker MC+ 2.3"))
    XCTAssertTrue(AdapterValidation.acceptsIdentity("ELM327 v2.2"))
    XCTAssertTrue(AdapterValidation.acceptsIdentity("OBDLink CX"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity(""))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("?"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("NO DATA"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("ERROR"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("AAAA"))
  }

  func testAdapterSelectionRequiresServiceAndExactName() throws {
    let profile = try ResetMotoProfile.bundledTiger900()

    XCTAssertEqual(
      "obdlink-cx",
      AdapterSelection.match(
        name: "OBDLink CX", serviceUUIDs: ["0000FFF0-0000-1000-8000-00805F9B34FB"],
        adapters: profile.adapters)?.id)
    XCTAssertNil(
      AdapterSelection.match(
        name: "OBDLink CX", serviceUUIDs: ["18F0"], adapters: profile.adapters))
    XCTAssertNil(
      AdapterSelection.match(
        name: "Unknown", serviceUUIDs: ["FFF0"], adapters: profile.adapters))
  }

  func testBLEPacketSizerUsesSafeMaximumAndPreservesPayload() {
    XCTAssertEqual(244, BLEPacketSizer.safePayloadBytes(reportedMaximum: 512))
    XCTAssertEqual(20, BLEPacketSizer.safePayloadBytes(reportedMaximum: 20))
    XCTAssertEqual(
      [20, 20, 5],
      BLEPacketSizer.chunks(Data(repeating: 0x41, count: 45), maximum: 20).map(\.count))
  }
}
