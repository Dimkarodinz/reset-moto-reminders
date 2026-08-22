import XCTest

@testable import ResetMotoCore

final class UseCaseTests: XCTestCase {
  func testDashboardReadReappliesRouteThenDecodesMotorcycle() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.instrument.configurationCommands,
        extras: [
          profile.instrument.statusCommand: "704 DE303433FFFFFFFF",
          profile.instrument.odometerCommand: "704 8D0100AE76000000",
        ]
      ))

    let result = try await DashboardUseCase(profile: profile.instrument).read(using: channel)

    XCTAssertEqual("043", result.statusASCII)
    XCTAssertEqual(44_662, result.odometerKilometres)
    let commands = await channel.commands
    XCTAssertEqual(profile.instrument.configurationCommands + ["5E01", "0D01"], commands)
  }

  func testDtcReadSkipsDetailWhenCountIsZero() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.engine.configurationCommands,
        extras: [profile.engine.dtcCountCommand: "18DAF1D5 06 59010C000000 AA"]
      ))

    let result = try await DTCUseCase(
      profile: profile.engine, descriptions: profile.dtcDescriptions
    ).read(using: channel)

    XCTAssertTrue(result.isEmpty)
    let commands = await channel.commands
    XCTAssertFalse(commands.contains(profile.engine.dtcDetailCommand))
  }

  func testDtcClearSendsClearOnceAndVerifiesAfterPendingResponse() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let seedKey = "042702A018"
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.engine.configurationCommands,
        extras: [
          profile.engine.extendedSessionCommand: "18DAF1D5 02 5003 AAAAAAAAAA",
          profile.engine.seedCommand: "18DAF1D5 04 6701188B AAAAAA",
          seedKey: "18DAF1D5 02 6702 AAAAAAAAAA",
          profile.engine.dtcClearCommand:
            "18DAF1D5 03 7F1478 AAAAAAAA\n18DAF1D5 01 54 AAAAAAAAAAAA",
          profile.engine.dtcCountCommand: "18DAF1D5 06 59010C000000 AA",
        ]
      ))

    let outcome = try await DTCUseCase(
      profile: profile.engine, descriptions: profile.dtcDescriptions
    )
    .clear(using: channel)

    XCTAssertEqual(.cleared, outcome)
    let commands = await channel.commands
    let intents = await channel.intents
    XCTAssertEqual(1, commands.filter { $0 == profile.engine.dtcClearCommand }.count)
    XCTAssertEqual(.write, intents[profile.engine.dtcClearCommand])
  }

  func testDtcClearStopsBeforeClearWhenSecurityIsRejected() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.engine.configurationCommands,
        extras: [profile.engine.extendedSessionCommand: "7F1012"]
      ))

    let outcome = try await DTCUseCase(
      profile: profile.engine, descriptions: profile.dtcDescriptions
    )
    .clear(using: channel)

    XCTAssertEqual(.blocked, outcome)
    let commands = await channel.commands
    XCTAssertFalse(commands.contains(profile.engine.dtcClearCommand))
  }

  func testServiceResetFingerprintsClusterBeforeWritingAndCommitsEchoes() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let date = try XCTUnwrap(
      Calendar(identifier: .gregorian).date(from: DateComponents(year: 2027, month: 8, day: 7)))
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.instrument.configurationCommands,
        extras: [
          profile.instrument.statusCommand: "704 DE303433FFFFFFFF",
          profile.instrument.odometerCommand: "704 8D0100AE76000000",
          "3364": "704 B364000000000000",
          "5C1B0807016E0000": "704 DC1B0807016E0000",
        ]
      ))

    let outcome = try await ServiceReminderUseCase(profile: profile.instrument)
      .reset(distance: 10_000, unit: .kilometres, nextServiceDate: date, using: channel)

    XCTAssertEqual(.committed(odometerKilometres: 44_662), outcome)
    let intents = await channel.intents
    XCTAssertEqual(.write, intents["3364"])
    XCTAssertEqual(.write, intents["5C1B0807016E0000"])
  }

  func testServiceResetStopsBeforeWriteOnFingerprintMismatch() async throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let channel = FakeCommandChannel(
      responses: responseMap(
        profile.instrument.configurationCommands,
        extras: [
          profile.instrument.statusCommand: "704 DE393939FFFFFFFF",
          profile.instrument.odometerCommand: "704 8D0100AE76000000",
        ]
      ))

    let outcome = try await ServiceReminderUseCase(profile: profile.instrument)
      .reset(distance: 10_000, unit: .kilometres, nextServiceDate: Date(), using: channel)

    XCTAssertEqual(.blocked, outcome)
    let commands = await channel.commands
    XCTAssertFalse(
      commands.contains(where: { $0.hasPrefix("33") || $0.hasPrefix("34") || $0.hasPrefix("5C") }))
  }

  private func responseMap(_ configuration: [String], extras: [String: String]) -> [String: String]
  {
    Dictionary(uniqueKeysWithValues: configuration.map { ($0, "OK") }).merging(extras) { _, new in
      new
    }
  }
}

private actor FakeCommandChannel: DiagnosticCommanding {
  private let responses: [String: String]
  private(set) var commands: [String] = []
  private(set) var intents: [String: CommandIntent] = [:]

  init(responses: [String: String]) {
    self.responses = responses
  }

  func execute(_ command: String, intent: CommandIntent) async throws -> String {
    commands.append(command)
    intents[command] = intent
    guard let response = responses[command] else { throw TestFailure.missing(command) }
    return response
  }

  enum TestFailure: Error { case missing(String) }
}
