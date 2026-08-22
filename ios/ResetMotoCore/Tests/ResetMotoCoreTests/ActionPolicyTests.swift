import XCTest

@testable import ResetMotoCore

final class ActionPolicyTests: XCTestCase {
  func testClearRequiresAFreshDisplayedNonemptyRead() {
    XCTAssertFalse(DTCActionPolicy.canClear(hasCurrentRead: false, count: 1))
    XCTAssertFalse(DTCActionPolicy.canClear(hasCurrentRead: true, count: 0))
    XCTAssertTrue(DTCActionPolicy.canClear(hasCurrentRead: true, count: 2))
  }
}
