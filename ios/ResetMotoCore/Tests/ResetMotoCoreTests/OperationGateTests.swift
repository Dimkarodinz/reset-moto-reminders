import XCTest

@testable import ResetMotoCore

final class OperationGateTests: XCTestCase {
  func testOnlyOneWholeOperationRunsAtATime() async {
    let gate = OperationGate()
    let first = await gate.tryAcquire()
    let second = await gate.tryAcquire()

    XCTAssertNotNil(first)
    XCTAssertNil(second)
    await first?.release()
    let third = await gate.tryAcquire()
    XCTAssertNotNil(third)
  }

  func testLeaseReleaseIsIdempotent() async {
    let gate = OperationGate()
    let lease = await gate.tryAcquire()
    await lease?.release()
    await lease?.release()
    let next = await gate.tryAcquire()
    XCTAssertNotNil(next)
  }
}
