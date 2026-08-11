import Foundation

public enum ElmFramingError: Error, Equatable {
  case responseTooLarge
}

public struct ElmFrame: Equatable, Sendable {
  public let data: Data
  public let fragmentCount: Int

  public func normalized(removingEcho command: String?) -> String {
    var text =
      String(data: data, encoding: .ascii)
      ?? data.map { $0 >= 0x20 && $0 <= 0x7E ? Character(UnicodeScalar($0)) : "\n" }.reduce(
        into: ""
      ) { $0.append($1) }
    text = text.replacingOccurrences(of: ">", with: "")
    var lines =
      text
      .replacingOccurrences(of: "\r", with: "\n")
      .split(separator: "\n")
      .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
      .filter { !$0.isEmpty }
    if let command, lines.first?.caseInsensitiveCompare(command) == .orderedSame {
      lines.removeFirst()
    }
    return lines.joined(separator: "\n")
  }
}

public struct ElmResponseAssembler {
  private let promptByte: UInt8
  private let maximumBytes: Int
  private var buffer = Data()
  private var ready: [ElmFrame] = []
  private var fragments = 0

  public init(promptByte: UInt8 = 0x3E, maximumBytes: Int = 64 * 1024) {
    self.promptByte = promptByte
    self.maximumBytes = maximumBytes
  }

  public mutating func append(_ fragment: Data) throws -> ElmFrame? {
    fragments += 1
    buffer.append(fragment)
    guard buffer.count <= maximumBytes else { throw ElmFramingError.responseTooLarge }
    splitCompleteFrames()
    return nextFrame()
  }

  public mutating func nextFrame() -> ElmFrame? {
    ready.isEmpty ? nil : ready.removeFirst()
  }

  private mutating func splitCompleteFrames() {
    while let prompt = buffer.firstIndex(of: promptByte) {
      let end = buffer.index(after: prompt)
      ready.append(ElmFrame(data: Data(buffer[..<end]), fragmentCount: fragments))
      buffer.removeSubrange(..<end)
      fragments = buffer.isEmpty ? 0 : 1
    }
  }
}
