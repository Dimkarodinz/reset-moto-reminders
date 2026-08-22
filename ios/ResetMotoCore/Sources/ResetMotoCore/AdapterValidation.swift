import Foundation

public enum AdapterValidation {
  public static func acceptsGattLayout(
    responseCanNotify: Bool,
    commandCanWriteWithResponse: Bool
  ) -> Bool {
    responseCanNotify && commandCanWriteWithResponse
  }

  public static func acceptsIdentity(_ value: String) -> Bool {
    let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
    guard normalized.count >= 4 else { return false }
    guard !["?", "NO DATA", "ERROR", "UNABLE TO CONNECT"].contains(normalized) else {
      return false
    }
    return ["VLINKER", "ELM", "STN"].contains(where: normalized.contains)
  }
}
