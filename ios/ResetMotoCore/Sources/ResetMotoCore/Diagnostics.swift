import Foundation

public struct Odometer: Equatable, Sendable {
  public let kilometres: Int
  public let raw: String
}

public enum InstrumentDecoder {
  public static func decodeStatus(_ payload: String) throws -> String {
    let bytes = try hexBytes(payload)
    guard bytes.first == 0xDE else { throw DiagnosticParseError.unexpectedResponse }
    let status = bytes.dropFirst().prefix { $0 != 0xFF }
    guard status.allSatisfy({ $0 >= 0x30 && $0 <= 0x39 }) else {
      throw DiagnosticParseError.unexpectedResponse
    }
    return String(bytes: status, encoding: .ascii) ?? ""
  }

  public static func decodeOdometer(_ payload: String) throws -> Odometer {
    let bytes = try hexBytes(payload)
    guard bytes.count >= 5, bytes[0] == 0x8D else { throw DiagnosticParseError.unexpectedResponse }
    let value = Int(bytes[3]) << 8 | Int(bytes[4])
    return Odometer(kilometres: value, raw: String(format: "0x%04X", value))
  }
}

public struct DiagnosticTroubleCode: Equatable, Identifiable, Sendable {
  public var id: String { code }
  public let code: String
  public let rawCode: String
  public let statusByte: UInt8
  public let confirmed: Bool
  public let message: String
}

public struct DTCDecoder: Sendable {
  private let descriptions: [String: String]

  public init(descriptions: [String: String]) { self.descriptions = descriptions }

  public func decodeCount(_ payload: String) throws -> Int {
    let bytes = try hexBytes(payload)
    guard bytes.count == 6, bytes[0] == 0x59, bytes[1] == 0x01 else {
      throw DiagnosticParseError.unexpectedResponse
    }
    return Int(bytes[4]) << 8 | Int(bytes[5])
  }

  public func decodeDetails(_ payload: String) throws -> [DiagnosticTroubleCode] {
    let bytes = try hexBytes(payload)
    guard bytes.count >= 3, bytes[0] == 0x59, bytes[1] == 0x02, (bytes.count - 3).isMultiple(of: 4)
    else {
      throw DiagnosticParseError.unexpectedResponse
    }
    let count = (bytes.count - 3) / 4
    guard count <= 64 else { throw DiagnosticParseError.tooManyRecords }
    return (0..<count).map { index in
      let offset = 3 + index * 4
      let raw = Int(bytes[offset]) << 16 | Int(bytes[offset + 1]) << 8 | Int(bytes[offset + 2])
      let code = displayCode(raw)
      let status = bytes[offset + 3]
      return DiagnosticTroubleCode(
        code: code,
        rawCode: String(format: "0x%06X", raw),
        statusByte: status,
        confirmed: status & 0x08 != 0,
        message: descriptions[code] ?? descriptions[String(code.prefix(5))] ?? fallback(code)
      )
    }
  }

  private func displayCode(_ value: Int) -> String {
    let first = value >> 16
    let second = value >> 8 & 0xFF
    let third = value & 0xFF
    let letters = Array("PCBU")
    return String(
      format: "%c%X%X%X%X-%02X", letters[first >> 6 & 3].asciiValue!, first >> 4 & 3, first & 0xF,
      second >> 4, second & 0xF, third)
  }

  private func fallback(_ code: String) -> String {
    let names: [Character: String] = [
      "P": "Powertrain", "C": "Chassis", "B": "Body", "U": "Network",
    ]
    return
      "\(names[code.first ?? "?"] ?? "Unrecognized") diagnostic trouble code \(code). No validated manufacturer description is available."
  }
}

public enum DistanceUnit: String, Codable, CaseIterable, Sendable { case kilometres, miles }

public struct ServiceReminderCommands: Equatable, Sendable {
  public let distance: String
  public let date: String
}

public struct ServiceReminderCommandBuilder: Sendable {
  private let profile: InstrumentProfile
  public init(profile: InstrumentProfile) { self.profile = profile }

  public func build(distance: Int, unit: DistanceUnit, nextServiceDate: Date) throws
    -> ServiceReminderCommands
  {
    guard distance.isMultiple(of: profile.distanceRawUnit) else {
      throw DiagnosticParseError.unexpectedResponse
    }
    let raw = distance / profile.distanceRawUnit
    guard (profile.distanceMinimumRaw...profile.distanceMaximumRaw).contains(raw) else {
      throw DiagnosticParseError.unexpectedResponse
    }
    let calendar = Calendar(identifier: .gregorian)
    let today = calendar.startOfDay(for: Date())
    let selected = calendar.startOfDay(for: nextServiceDate)
    guard let latest = calendar.date(byAdding: .year, value: 2, to: today),
      selected >= today, selected <= latest
    else { throw DiagnosticParseError.unexpectedResponse }
    let components = calendar.dateComponents([.year, .month, .day], from: nextServiceDate)
    guard let year = components.year, let month = components.month, let day = components.day else {
      throw DiagnosticParseError.unexpectedResponse
    }
    let rawYear = year - profile.yearBase
    guard (0...255).contains(rawYear) else { throw DiagnosticParseError.unexpectedResponse }
    let prefix =
      unit == .kilometres ? profile.distancePrefixKilometres : profile.distancePrefixMiles
    return ServiceReminderCommands(
      distance: prefix + String(format: "%02X", raw),
      date: profile.datePrefix + String(format: "%02X%02X%02X", rawYear, month, day)
        + profile.dateFixedSuffix
    )
  }
}

public struct SeedKeyDerivation: Sendable {
  private let multiplier: Int
  public init(multiplier: Int) { self.multiplier = multiplier }

  public func key(seedHex: String) throws -> String {
    let bytes = try hexBytes(seedHex)
    guard bytes.count == 2 else { throw DiagnosticParseError.unexpectedResponse }
    let seed = Int(bytes[0]) << 8 | Int(bytes[1])
    return String(format: "%04X", seed * multiplier & 0xFFFF)
  }

  public func keyRequest(seedResponse: String, prefix: String) throws -> String {
    let bytes = try hexBytes(seedResponse)
    guard bytes.count == 4, bytes[0] == 0x67, bytes[1] == 0x01 else {
      throw DiagnosticParseError.unexpectedResponse
    }
    return prefix + (try key(seedHex: hex(Array(bytes[2...3]))))
  }
}
