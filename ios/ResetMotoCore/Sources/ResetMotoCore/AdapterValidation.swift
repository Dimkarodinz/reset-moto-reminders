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
    return ["VLINKER", "OBDLINK", "ELM", "STN"].contains(where: normalized.contains)
  }
}

public enum AdapterSelection {
  public static func match(
    name: String?,
    serviceUUIDs: Set<String>,
    adapters: [AdapterProfile]
  ) -> AdapterProfile? {
    let services = Set(serviceUUIDs.map(normalizedUUID))
    return adapters.single { adapter in
      name == adapter.advertisedName && services.contains(normalizedUUID(adapter.serviceUUID))
    }
  }

  private static func normalizedUUID(_ value: String) -> String {
    let normalized = value.replacingOccurrences(of: "0x", with: "", options: .caseInsensitive)
      .uppercased()
    let bluetoothSuffix = "-0000-1000-8000-00805F9B34FB"
    if normalized.hasSuffix(bluetoothSuffix) {
      let prefix = String(normalized.dropLast(bluetoothSuffix.count))
      return prefix.hasPrefix("0000") ? String(prefix.suffix(4)) : prefix
    }
    return normalized
  }
}

public enum BLEPacketSizer {
  private static let cxMaximumPayload = 244

  public static func safePayloadBytes(reportedMaximum: Int) -> Int {
    max(1, min(reportedMaximum, cxMaximumPayload))
  }

  public static func chunks(_ data: Data, maximum: Int) -> [Data] {
    guard !data.isEmpty else { return [] }
    let size = max(1, maximum)
    return stride(from: data.startIndex, to: data.endIndex, by: size).map { start in
      data[start..<min(start + size, data.endIndex)]
    }
  }
}

extension Array {
  fileprivate func single(where predicate: (Element) -> Bool) -> Element? {
    let matches = filter(predicate)
    return matches.count == 1 ? matches[0] : nil
  }
}
