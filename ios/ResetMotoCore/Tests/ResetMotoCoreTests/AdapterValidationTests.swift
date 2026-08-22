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
    XCTAssertFalse(AdapterValidation.acceptsIdentity(""))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("?"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("NO DATA"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("ERROR"))
    XCTAssertFalse(AdapterValidation.acceptsIdentity("AAAA"))
  }
}
