import Foundation

public enum CommandIntent: Equatable, Sendable { case read, write }

public protocol DiagnosticCommanding: Sendable {
  func execute(_ command: String, intent: CommandIntent) async throws -> String
}

public enum DiagnosticOperationError: Error, Equatable {
  case configurationRejected(String)
  case inconsistentDTCCount(reported: Int, decoded: Int)
}

public struct DashboardResult: Equatable, Sendable {
  public let statusASCII: String
  public let odometerKilometres: Int
  public let odometerRaw: String
}

public struct DashboardUseCase: Sendable {
  private let profile: InstrumentProfile
  private let extractor: CanResponseExtractor

  public init(profile: InstrumentProfile) {
    self.profile = profile
    self.extractor = CanResponseExtractor(responseCANID: profile.responseCANID, isoTP: false)
  }

  public func read(using channel: any DiagnosticCommanding) async throws -> DashboardResult {
    try await configure(profile.configurationCommands, channel)
    let status = try InstrumentDecoder.decodeStatus(
      extractor.extract(await channel.execute(profile.statusCommand, intent: .read)))
    let odometer = try InstrumentDecoder.decodeOdometer(
      extractor.extract(await channel.execute(profile.odometerCommand, intent: .read)))
    return DashboardResult(
      statusASCII: status, odometerKilometres: odometer.kilometres, odometerRaw: odometer.raw)
  }
}

public enum DTCClearOutcome: Equatable, Sendable { case cleared, blocked, needsVerification }

public struct DTCUseCase: Sendable {
  private let profile: EngineProfile
  private let decoder: DTCDecoder
  private let extractor: CanResponseExtractor

  public init(profile: EngineProfile, descriptions: [String: String]) {
    self.profile = profile
    self.decoder = DTCDecoder(descriptions: descriptions)
    self.extractor = CanResponseExtractor(responseCANID: profile.responseCANID, isoTP: true)
  }

  public func read(using channel: any DiagnosticCommanding) async throws -> [DiagnosticTroubleCode]
  {
    try await configure(profile.configurationCommands, channel)
    let count = try decoder.decodeCount(
      extractor.extract(await channel.execute(profile.dtcCountCommand, intent: .read)))
    guard count > 0 else { return [] }
    let values = try decoder.decodeDetails(
      extractor.extract(await channel.execute(profile.dtcDetailCommand, intent: .read)))
    guard count == values.count else {
      throw DiagnosticOperationError.inconsistentDTCCount(reported: count, decoded: values.count)
    }
    return values
  }

  public func clear(using channel: any DiagnosticCommanding) async throws -> DTCClearOutcome {
    try await configure(profile.configurationCommands, channel)
    let session = try extractor.extract(
      await channel.execute(profile.extendedSessionCommand, intent: .read))
    guard session.hasPrefix("5003") else { return .blocked }
    let seed = try extractor.extract(await channel.execute(profile.seedCommand, intent: .read))
    let keyRequest = try SeedKeyDerivation(multiplier: profile.seedMultiplier)
      .keyRequest(seedResponse: seed, prefix: profile.keyRequestPrefix)
    // Security access unlocks the following request but does not itself change
    // diagnostic memory, so an interrupted key exchange is not a write outcome.
    let key = try extractor.extract(await channel.execute(keyRequest, intent: .read))
    guard key.hasPrefix("6702") else { return .blocked }

    // The state-changing request is deliberately sent once. Response-pending
    // and the final positive response are consumed from that one ELM reply.
    let clearRaw = try await channel.execute(profile.dtcClearCommand, intent: .write)
    let clearReplies = (try? extractor.extractAll(clearRaw)) ?? []
    guard clearReplies.contains(where: { $0 == "54" }) else { return .blocked }
    let verification = try extractor.extract(
      await channel.execute(profile.dtcCountCommand, intent: .read))
    return try decoder.decodeCount(verification) == 0 ? .cleared : .needsVerification
  }
}

public enum ServiceReminderOutcome: Equatable, Sendable {
  case committed(odometerKilometres: Int)
  case blocked
  case partiallyApplied
}

public struct ServiceReminderUseCase: Sendable {
  private let profile: InstrumentProfile
  private let extractor: CanResponseExtractor
  private let builder: ServiceReminderCommandBuilder

  public init(profile: InstrumentProfile) {
    self.profile = profile
    self.extractor = CanResponseExtractor(responseCANID: profile.responseCANID, isoTP: false)
    self.builder = ServiceReminderCommandBuilder(profile: profile)
  }

  public func reset(
    distance: Int,
    unit: DistanceUnit,
    nextServiceDate: Date,
    using channel: any DiagnosticCommanding
  ) async throws -> ServiceReminderOutcome {
    // Validate every field before the first adapter or motorcycle command.
    let commands = try builder.build(
      distance: distance, unit: unit, nextServiceDate: nextServiceDate)
    try await configure(profile.configurationCommands, channel)
    let status = try InstrumentDecoder.decodeStatus(
      extractor.extract(await channel.execute(profile.statusCommand, intent: .read)))
    let odometer = try InstrumentDecoder.decodeOdometer(
      extractor.extract(await channel.execute(profile.odometerCommand, intent: .read)))
    guard status == profile.expectedStatusASCII else { return .blocked }

    let distanceReply = try extractor.extract(
      await channel.execute(commands.distance, intent: .write))
    guard positiveEcho(request: commands.distance, response: distanceReply) else { return .blocked }
    let dateReply = try extractor.extract(await channel.execute(commands.date, intent: .write))
    guard positiveEcho(request: commands.date, response: dateReply) else {
      return .partiallyApplied
    }
    return .committed(odometerKilometres: odometer.kilometres)
  }
}

private func configure(_ commands: [String], _ channel: any DiagnosticCommanding) async throws {
  for command in commands {
    let response = try await channel.execute(command, intent: .read)
    guard configurationAccepted(command: command, response: response) else {
      throw DiagnosticOperationError.configurationRejected(command)
    }
  }
}

private func positiveEcho(request: String, response: String) -> Bool {
  guard let requestService = UInt8(request.prefix(2), radix: 16),
    let responseService = UInt8(response.prefix(2), radix: 16),
    responseService == requestService | 0x80
  else { return false }
  return response.dropFirst(2).hasPrefix(request.dropFirst(2))
}
