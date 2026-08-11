import Foundation

/// Reassembles a fragmented ELM response from GATT notifications. The
/// MC-IOS capture inferred an effective ATT MTU of 23, so a response such as
/// `ELM327 v2.2\r\r>` is expected to arrive as several notification
/// fragments; per the adapter map the response is complete once the ASCII
/// `>` prompt (0x3E) has been received.
public struct ElmResponseAssembler {
    public private(set) var fragmentCount = 0
    private var buffer = Data()
    private let promptByte: UInt8

    public init(promptByte: UInt8 = 0x3E) {
        self.promptByte = promptByte
    }

    /// Appends one notification payload. Returns true once the prompt has
    /// been seen (including in an earlier fragment).
    @discardableResult
    public mutating func append(_ fragment: Data) -> Bool {
        fragmentCount += 1
        buffer.append(fragment)
        return isComplete
    }

    public var isComplete: Bool { buffer.contains(promptByte) }

    /// Everything received so far as uppercase hex, for the journal.
    public var rawHex: String {
        buffer.map { String(format: "%02X", $0) }.joined()
    }

    /// The response as normalized text: carriage returns become newlines,
    /// non-ASCII bytes become `.`, and outer whitespace is trimmed. The raw
    /// bytes stay available via `rawHex`; this is the human-readable form.
    public var text: String {
        let characters = buffer.map { byte -> Character in
            switch byte {
            case 0x0D: return "\n"
            case 0x20...0x7E: return Character(UnicodeScalar(byte))
            default: return "."
            }
        }
        return String(characters)
            .replacingOccurrences(of: "\n+", with: "\n", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
