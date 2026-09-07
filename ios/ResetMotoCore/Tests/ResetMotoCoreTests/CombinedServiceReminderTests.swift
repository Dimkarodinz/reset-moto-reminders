import XCTest

@testable import ResetMotoCore

final class CombinedServiceReminderTests: XCTestCase {
  func testAESDerivationEncryptsFullSeedAndReturnsFirstFourBytes() throws {
    XCTAssertEqual(
      "69C4E0D8",
      try InstrumentSecurityKeyDerivation().derive(
        seedHex: "00112233445566778899AABBCCDDEEFF",
        aesKeyHex: "000102030405060708090A0B0C0D0E0F"))
  }

  func testAdaptiveBuilderSelectsUpdatedEncodingFromTwelveByteA000Data() throws {
    let family = try adaptiveFamily()
    let payload = try CombinedServiceReminderPayloadBuilder(profile: family).build(
      strategy: family.strategy,
      intervalKilometres: 10_000,
      nextServiceDate: date(),
      a500Payload: "62A50000AE76",
      a000Payload: "62A0000102030405060708090A0B0C")

    XCTAssertEqual(.updatedCombined, payload.resolvedStrategy)
    XCTAssertEqual(
      ["100F2EA00000AE76", "2100D5860102031B", "2208070000000000"], payload.frames)
  }

  func testAdaptiveBuilderSelectsHybridEncodingFromFiveByteA000Data() throws {
    let family = try adaptiveFamily()
    let payload = try CombinedServiceReminderPayloadBuilder(profile: family).build(
      strategy: family.strategy,
      intervalKilometres: 10_001,
      nextServiceDate: date(),
      a500Payload: "62A50000AE76",
      a000Payload: "62A0000102030405")

    XCTAssertEqual(.hybridCombined, payload.resolvedStrategy)
    XCTAssertEqual(["10082EA000088A1B", "2108070000000000"], payload.frames)
  }

  func testAdaptiveResetRejectsUnknownA000ShapeBeforeWrite() async throws {
    let family = try adaptiveFamily()
    let key = try InstrumentSecurityKeyDerivation().derive(
      seedHex: "00112233445566778899AABBCCDDEEFF",
      aesKeyHex: family.securityKeys.first { $0.timingResponseSuffix == "3201F4" }!.aesKey)
    let channel = CombinedFakeChannel(
      responses: responseMap(
        family.configurationCommands,
        extras: [
          family.sessionCommand: "18DAF1C1 06 5003003201F4 AA",
          family.seedCommand:
            "18DAF1C1 10 12 670100112233\n18DAF1C1 21 445566778899AA\n18DAF1C1 22 BBCCDDEEFFAAAA",
          family.keyRequestPrefix + key: "18DAF1C1 02 6702 AAAAAAAAAA",
          family.a500Command: "18DAF1C1 06 62A50000AE76 AA",
          family.a000Command: "18DAF1C1 09 62A000010203040506",
          family.a010Command: "18DAF1C1 05 62A0100102 AAAA",
        ]))

    let outcome = try await CombinedServiceReminderUseCase(profile: family).reset(
      distance: 10_000, unit: .kilometres, nextServiceDate: date(), using: channel)

    XCTAssertEqual(.blocked, outcome)
    let writes = await channel.writes
    XCTAssertTrue(writes.isEmpty)
  }

  func testAdaptiveResetSelectsHybridFormatWritesOnceAndVerifies() async throws {
    let family = try adaptiveFamily()
    let key = try InstrumentSecurityKeyDerivation().derive(
      seedHex: "00112233445566778899AABBCCDDEEFF",
      aesKeyHex: family.securityKeys.first { $0.timingResponseSuffix == "3201F4" }!.aesKey)
    let responses = responseMap(
      family.configurationCommands,
      extras: [
        family.sessionCommand: "18DAF1C1 06 5003003201F4 AA",
        family.seedCommand:
          "18DAF1C1 10 12 670100112233\n18DAF1C1 21 445566778899AA\n18DAF1C1 22 BBCCDDEEFFAAAA",
        family.keyRequestPrefix + key: "18DAF1C1 02 6702 AAAAAAAAAA",
        family.a500Command: "18DAF1C1 06 62A50000AE76 AA",
        family.a010Command: "18DAF1C1 05 62A0100102 AAAA",
        "10082EA000088A1B": "18DAF1C1 30 00000000000000",
        "2108070000000000": "18DAF1C1 03 6EA000 AAAAAAAA",
      ])
    let channel = CombinedFakeChannel(
      sequencedResponses: responses.mapValues { [$0] }.merging([
        family.a000Command: [
          "18DAF1C1 10 08 62A000010203\n18DAF1C1 21 0405AAAAAAAAAA",
          "18DAF1C1 10 08 62A000088A1B\n18DAF1C1 21 0807AAAAAAAAAA",
        ],
        family.a010Command: [
          "18DAF1C1 05 62A0100102 AAAA",
          "18DAF1C1 05 62A0100102 AAAA",
        ],
      ]) { _, sequence in sequence })

    let outcome = try await CombinedServiceReminderUseCase(profile: family).reset(
      distance: 10_001, unit: .kilometres, nextServiceDate: date(), using: channel)

    XCTAssertEqual(.committed(odometerKilometres: 44_662), outcome)
    let writes = await channel.writes
    XCTAssertEqual(["10082EA000088A1B", "2108070000000000"], writes)
  }

  private func adaptiveFamily() throws -> CombinedInstrumentProfile {
    let profile = try ResetMotoProfile.bundledTiger900()
    return try XCTUnwrap(
      profile.combinedInstrumentFamilies.first { $0.id == "triumph-adaptive-combined" })
  }

  private func date() throws -> Date {
    try XCTUnwrap(
      Calendar(identifier: .gregorian).date(
        from: DateComponents(year: 2027, month: 8, day: 7)))
  }

  private func responseMap(_ configuration: [String], extras: [String: String]) -> [String: String]
  {
    Dictionary(
      uniqueKeysWithValues: configuration.map { ($0, $0 == "ATWS" ? "ELM327 v2.2" : "OK") }
    )
    .merging(extras) { _, new in new }
  }
}

private actor CombinedFakeChannel: DiagnosticCommanding {
  private var responses: [String: [String]]
  private(set) var writes: [String] = []

  init(responses: [String: String]) { self.responses = responses.mapValues { [$0] } }

  init(sequencedResponses: [String: [String]]) { self.responses = sequencedResponses }

  func execute(_ command: String, intent: CommandIntent) async throws -> String {
    if intent == .write { writes.append(command) }
    guard var queued = responses[command], !queued.isEmpty else { throw Failure.missing(command) }
    let response = queued.removeFirst()
    responses[command] = queued.isEmpty ? [response] : queued
    return response
  }

  enum Failure: Error { case missing(String) }
}
