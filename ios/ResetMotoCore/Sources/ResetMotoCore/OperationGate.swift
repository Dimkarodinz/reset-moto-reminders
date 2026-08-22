import Foundation

public actor OperationGate {
  private var token: UUID?

  public init() {}

  public func tryAcquire() -> OperationLease? {
    guard token == nil else { return nil }
    let newToken = UUID()
    token = newToken
    return OperationLease(gate: self, token: newToken)
  }

  fileprivate func release(_ candidate: UUID) {
    if token == candidate { token = nil }
  }
}

public actor OperationLease {
  private let gate: OperationGate
  private let token: UUID
  private var released = false

  fileprivate init(gate: OperationGate, token: UUID) {
    self.gate = gate
    self.token = token
  }

  public func release() async {
    guard !released else { return }
    released = true
    await gate.release(token)
  }
}
