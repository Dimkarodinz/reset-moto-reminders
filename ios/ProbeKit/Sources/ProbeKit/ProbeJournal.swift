import Foundation

/// One journaled probe event. Field names mirror the Android app's JSONL
/// journal so `tools/`-side analysis can treat both the same way.
public struct ProbeJournalEvent: Equatable, Sendable {
    public let sequence: Int
    public let wallTime: String
    public let layer: String
    public let name: String
    public let text: String?
    public let rawHex: String?
    public let outcome: String?
}

/// An append-only JSONL journal for one probe run. Encoding is done by hand
/// so field order is stable and lines are deterministic for a given input —
/// the same property the Android journal has.
public final class ProbeJournal {
    public private(set) var events: [ProbeJournalEvent] = []
    private let clock: () -> Date
    private let formatter: ISO8601DateFormatter

    public init(clock: @escaping () -> Date = { Date() }) {
        self.clock = clock
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        self.formatter = formatter
    }

    @discardableResult
    public func record(
        layer: String,
        name: String,
        text: String? = nil,
        rawHex: String? = nil,
        outcome: String? = nil
    ) -> ProbeJournalEvent {
        let event = ProbeJournalEvent(
            sequence: events.count + 1,
            wallTime: formatter.string(from: clock()),
            layer: layer,
            name: name,
            text: text,
            rawHex: rawHex,
            outcome: outcome
        )
        events.append(event)
        return event
    }

    /// The whole journal as JSONL, one event per line.
    public func jsonl() -> String {
        events.map(Self.encode).joined(separator: "\n")
    }

    static func encode(_ event: ProbeJournalEvent) -> String {
        var fields = [
            "\"sequence\":\(event.sequence)",
            "\"wallTime\":\(escape(event.wallTime))",
            "\"layer\":\(escape(event.layer))",
            "\"name\":\(escape(event.name))",
        ]
        if let text = event.text { fields.append("\"text\":\(escape(text))") }
        if let rawHex = event.rawHex { fields.append("\"rawHex\":\(escape(rawHex))") }
        if let outcome = event.outcome { fields.append("\"outcome\":\(escape(outcome))") }
        return "{\(fields.joined(separator: ","))}"
    }

    private static func escape(_ value: String) -> String {
        var escaped = ""
        for scalar in value.unicodeScalars {
            switch scalar {
            case "\"": escaped += "\\\""
            case "\\": escaped += "\\\\"
            case "\n": escaped += "\\n"
            case "\r": escaped += "\\r"
            case "\t": escaped += "\\t"
            default:
                if scalar.value < 0x20 {
                    escaped += String(format: "\\u%04x", scalar.value)
                } else {
                    escaped.unicodeScalars.append(scalar)
                }
            }
        }
        return "\"\(escaped)\""
    }
}
