/// A CoreBluetooth `.withResponse` command is complete only after both layers
/// have answered: GATT acknowledges the characteristic write and ELM emits a
/// complete prompt-terminated response. Either event may arrive first.
public struct CommandCompletionGate: Equatable, Sendable {
  private var writeAcknowledged = false
  private var response: String?

  public init() {}

  public var completedResponse: String? {
    writeAcknowledged ? response : nil
  }

  public mutating func acknowledgeWrite() {
    writeAcknowledged = true
  }

  public mutating func receive(response: String) {
    self.response = response
  }
}
