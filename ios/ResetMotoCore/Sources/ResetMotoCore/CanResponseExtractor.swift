import Foundation

public enum DiagnosticParseError: Error, Equatable {
  case invalidHex
  case noResponse
  case unexpectedResponse
  case truncated
  case tooManyRecords
}

public struct CanResponseExtractor: Sendable {
  private let header: String
  private let isoTP: Bool

  public init(responseCANID: String, isoTP: Bool) {
    self.header = responseCANID.replacingOccurrences(of: "0x", with: "", options: .caseInsensitive)
      .uppercased()
    self.isoTP = isoTP
  }

  public func extract(_ raw: String) throws -> String {
    let values = try extractAll(raw)
    guard values.count == 1, let only = values.first else {
      throw DiagnosticParseError.unexpectedResponse
    }
    return only
  }

  public func extractAll(_ raw: String) throws -> [String] {
    let upper = raw.uppercased()
    if ["NO DATA", "CAN ERROR", "BUS INIT", "STOPPED", "UNABLE TO CONNECT"].contains(
      where: upper.contains)
    {
      throw DiagnosticParseError.noResponse
    }
    let frames = raw.split(whereSeparator: \Character.isNewline).compactMap { line -> String? in
      let hex = line.filter(\.isHexDigit).uppercased()
      return hex.isEmpty ? nil : hex
    }
    guard !frames.isEmpty else { throw DiagnosticParseError.invalidHex }
    let matching = frames.filter { $0.hasPrefix(header) && $0.count > header.count }
    guard !matching.isEmpty else { return frames }
    let dataFrames = matching.map { String($0.dropFirst(header.count)) }
    guard isoTP else { return dataFrames }

    let decoded = try dataFrames.map(hexBytes)
    if decoded.allSatisfy({ ($0[0] >> 4) == 0 }) {
      return try decoded.map(decodeSingleFrame)
    }
    return [try reassemble(decoded)]
  }

  private func decodeSingleFrame(_ frame: [UInt8]) throws -> String {
    let length = Int(frame[0] & 0x0F)
    guard length > 0, frame.count >= length + 1 else { throw DiagnosticParseError.truncated }
    return hex(Array(frame[1...length]))
  }

  private func reassemble(_ frames: [[UInt8]]) throws -> String {
    guard let first = frames.first, first.count >= 2, first[0] >> 4 == 1 else {
      throw DiagnosticParseError.unexpectedResponse
    }
    let length = Int(first[0] & 0x0F) << 8 | Int(first[1])
    var bytes = Array(first.dropFirst(2))
    for (index, frame) in frames.dropFirst().enumerated() {
      guard !frame.isEmpty, frame[0] >> 4 == 2, Int(frame[0] & 0x0F) == (index + 1) & 0x0F else {
        throw DiagnosticParseError.unexpectedResponse
      }
      bytes.append(contentsOf: frame.dropFirst())
    }
    guard bytes.count >= length else { throw DiagnosticParseError.truncated }
    return hex(Array(bytes.prefix(length)))
  }
}

func hexBytes(_ value: String) throws -> [UInt8] {
  let hex = value.filter(\.isHexDigit)
  guard !hex.isEmpty, hex.count.isMultiple(of: 2) else { throw DiagnosticParseError.invalidHex }
  return try stride(from: 0, to: hex.count, by: 2).map { offset in
    let start = hex.index(hex.startIndex, offsetBy: offset)
    let end = hex.index(start, offsetBy: 2)
    guard let byte = UInt8(hex[start..<end], radix: 16) else {
      throw DiagnosticParseError.invalidHex
    }
    return byte
  }
}

func hex(_ bytes: [UInt8]) -> String { bytes.map { String(format: "%02X", $0) }.joined() }

func configurationAccepted(command: String, response: String) -> Bool {
  let normalized = response.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
  return normalized == "OK" || normalized == command.uppercased()
}
