public enum DTCActionPolicy {
  public static func canClear(hasCurrentRead: Bool, count: Int) -> Bool {
    hasCurrentRead && count > 0
  }
}

/// Owns the short-lived proof that the DTC list was read in the current session.
/// Starting another read or clear attempt consumes that proof immediately so a
/// failed operation can never leave a stale clear action authorized.
public struct DTCReadState: Equatable, Sendable {
  public private(set) var codes: [DiagnosticTroubleCode] = []
  public private(set) var hasCurrentRead = false

  public init() {}

  public var canClear: Bool {
    DTCActionPolicy.canClear(hasCurrentRead: hasCurrentRead, count: codes.count)
  }

  public mutating func beginRead() {
    codes = []
    hasCurrentRead = false
  }

  public mutating func completeRead(_ codes: [DiagnosticTroubleCode]) {
    self.codes = codes
    hasCurrentRead = true
  }

  public mutating func beginClearAttempt() {
    hasCurrentRead = false
  }
}

public enum OperationInterruptionPolicy {
  public static func isAmbiguous(
    operationRunning: Bool,
    stateChangingWriteSent: Bool
  ) -> Bool {
    operationRunning && stateChangingWriteSent
  }
}
