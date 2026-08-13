import XCTest
@testable import ProbeKit

final class ProbeJournalTests: XCTestCase {
    private let fixedDate = Date(timeIntervalSince1970: 1_786_600_000)

    func testEventsAreSequencedAndTimestamped() {
        let journal = ProbeJournal(clock: { self.fixedDate })
        journal.record(layer: "ble", name: "scan_started")
        let second = journal.record(layer: "ble", name: "peripheral_discovered", text: "vLinker MC-IOS")

        XCTAssertEqual(2, second.sequence)
        XCTAssertTrue(second.wallTime.hasPrefix("2026-08-13T"))
    }

    func testJsonlLinesAreDeterministicAndParseable() throws {
        let journal = ProbeJournal(clock: { self.fixedDate })
        journal.record(layer: "elm", name: "outbound", text: "ATI", rawHex: "4154490D")
        journal.record(layer: "probe", name: "probe_finished", outcome: "no_response")

        let lines = journal.jsonl().split(separator: "\n")
        XCTAssertEqual(2, lines.count)
        for line in lines {
            let object = try JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any]
            XCTAssertNotNil(object?["sequence"])
            XCTAssertNotNil(object?["wallTime"])
            XCTAssertNotNil(object?["layer"])
            XCTAssertNotNil(object?["name"])
        }
        XCTAssertTrue(lines[0].contains("\"rawHex\":\"4154490D\""))
        XCTAssertTrue(lines[1].contains("\"outcome\":\"no_response\""))
    }

    func testControlCharactersAndQuotesAreEscaped() throws {
        let journal = ProbeJournal(clock: { self.fixedDate })
        journal.record(layer: "elm", name: "inbound", text: "ELM327 \"v2.2\"\r\n>")

        let line = journal.jsonl()
        let object = try JSONSerialization.jsonObject(with: Data(line.utf8)) as? [String: Any]
        XCTAssertEqual("ELM327 \"v2.2\"\r\n>", object?["text"] as? String)
    }
}
