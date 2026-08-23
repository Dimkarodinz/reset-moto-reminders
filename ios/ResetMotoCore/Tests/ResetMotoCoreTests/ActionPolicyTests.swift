import XCTest

@testable import ResetMotoCore

final class ActionPolicyTests: XCTestCase {
  func testClearRequiresAFreshDisplayedNonemptyRead() {
    XCTAssertFalse(DTCActionPolicy.canClear(hasCurrentRead: false, count: 1))
    XCTAssertFalse(DTCActionPolicy.canClear(hasCurrentRead: true, count: 0))
    XCTAssertTrue(DTCActionPolicy.canClear(hasCurrentRead: true, count: 2))
  }

  func testStartingANewReadImmediatelyInvalidatesOldClearAuthorization() {
    var state = DTCReadState()
    state.completeRead([
      DiagnosticTroubleCode(
        code: "P0001", rawCode: "0x000100", statusByte: 0x08, confirmed: true,
        message: "Example")
    ])
    XCTAssertTrue(state.canClear)

    state.beginRead()

    XCTAssertFalse(state.canClear)
    XCTAssertTrue(state.codes.isEmpty)
  }

  func testStartingAClearAttemptConsumesAuthorizationUntilAnotherReadCompletes() {
    var state = DTCReadState()
    state.completeRead([
      DiagnosticTroubleCode(
        code: "P0001", rawCode: "0x000100", statusByte: 0x08, confirmed: true,
        message: "Example")
    ])

    state.beginClearAttempt()

    XCTAssertFalse(state.canClear)
    XCTAssertEqual(["P0001"], state.codes.map(\.code))
  }

  func testBackgroundInterruptionIsAmbiguousOnlyAfterAStateChangingWrite() {
    XCTAssertFalse(
      OperationInterruptionPolicy.isAmbiguous(
        operationRunning: true, stateChangingWriteSent: false))
    XCTAssertFalse(
      OperationInterruptionPolicy.isAmbiguous(
        operationRunning: false, stateChangingWriteSent: true))
    XCTAssertTrue(
      OperationInterruptionPolicy.isAmbiguous(
        operationRunning: true, stateChangingWriteSent: true))
  }
}
