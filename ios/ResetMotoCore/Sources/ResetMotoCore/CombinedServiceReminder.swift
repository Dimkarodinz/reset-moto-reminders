import CommonCrypto
import Foundation

struct CombinedServiceReminderPayload: Equatable, Sendable {
  let odometerKilometres: Int
  let nextServiceOdometerKilometres: Int
  let resolvedStrategy: ServiceReminderStrategy
  let frames: [String]
}

struct InstrumentSecurityKeyDerivation: Sendable {
  func derive(seedHex: String, aesKeyHex: String) throws -> String {
    let seed = try hexBytes(seedHex)
    let key = try hexBytes(aesKeyHex)
    guard seed.count == kCCBlockSizeAES128, key.count == kCCKeySizeAES128 else {
      throw DiagnosticParseError.unexpectedResponse
    }

    var encrypted = [UInt8](repeating: 0, count: kCCBlockSizeAES128)
    var written = 0
    let status = key.withUnsafeBytes { keyBuffer in
      seed.withUnsafeBytes { seedBuffer in
        CCCrypt(
          CCOperation(kCCEncrypt), CCAlgorithm(kCCAlgorithmAES), CCOptions(kCCOptionECBMode),
          keyBuffer.baseAddress, key.count, nil, seedBuffer.baseAddress, seed.count,
          &encrypted, encrypted.count, &written)
      }
    }
    guard status == kCCSuccess, written == kCCBlockSizeAES128 else {
      throw DiagnosticParseError.unexpectedResponse
    }
    return hex(Array(encrypted.prefix(4)))
  }
}

struct CombinedServiceReminderPayloadBuilder: Sendable {
  private let profile: CombinedInstrumentProfile

  init(profile: CombinedInstrumentProfile) { self.profile = profile }

  func build(
    strategy: ServiceReminderStrategy,
    intervalKilometres: Int,
    nextServiceDate: Date,
    a500Payload: String,
    a000Payload: String
  ) throws -> CombinedServiceReminderPayload {
    guard
      (profile.minimumDistanceKilometres...profile.maximumDistanceKilometres)
        .contains(intervalKilometres),
      intervalKilometres.isMultiple(of: profile.inputStepKilometres)
    else { throw DiagnosticParseError.unexpectedResponse }

    let date = try dateBytes(nextServiceDate)
    let odometerBytes = try didData(a500Payload, did: "A500", expectedCount: 3)
    let odometer = unsignedInteger(odometerBytes)
    let nextOdometer = try addWithoutOverflow(odometer, intervalKilometres)
    let a000Data = try didData(a000Payload, did: "A000")
    let resolved = try resolvedStrategy(strategy, a000DataCount: a000Data.count)

    let payload: [UInt8]
    switch resolved {
    case .updatedCombined:
      guard a000Data.count == 12, nextOdometer <= 0xFF_FF_FF else {
        throw DiagnosticParseError.unexpectedResponse
      }
      payload =
        try hexBytes(profile.requestPrefix) + odometerBytes + bytes(nextOdometer, count: 3)
        + Array(a000Data.prefix(3)) + date
    case .hybridCombined:
      guard a000Data.count == 5,
        let divisor = profile.hybridOdometerDivisorKilometres,
        nextOdometer / divisor <= 0xFF_FF
      else { throw DiagnosticParseError.unexpectedResponse }
      payload = try hexBytes(profile.requestPrefix) + bytes(nextOdometer / divisor, count: 2) + date
    case .originalSplit, .adaptiveCombined:
      throw DiagnosticParseError.unexpectedResponse
    }

    return CombinedServiceReminderPayload(
      odometerKilometres: odometer,
      nextServiceOdometerKilometres: nextOdometer,
      resolvedStrategy: resolved,
      frames: isoTPFrames(payload))
  }

  private func resolvedStrategy(
    _ requested: ServiceReminderStrategy,
    a000DataCount: Int
  ) throws -> ServiceReminderStrategy {
    guard requested == .adaptiveCombined else { return requested }
    switch a000DataCount {
    case 12: return .updatedCombined
    case 5: return .hybridCombined
    default: throw DiagnosticParseError.unexpectedResponse
    }
  }

  private func dateBytes(_ value: Date) throws -> [UInt8] {
    let components = Calendar(identifier: .gregorian).dateComponents(
      [.year, .month, .day], from: value)
    guard let year = components.year, let month = components.month, let day = components.day,
      (0...255).contains(year - profile.yearBase)
    else { throw DiagnosticParseError.unexpectedResponse }
    return [UInt8(year - profile.yearBase), UInt8(month), UInt8(day)]
  }
}

public struct CombinedServiceReminderUseCase: Sendable {
  private let profile: CombinedInstrumentProfile
  private let extractor: CanResponseExtractor
  private let keyDerivation = InstrumentSecurityKeyDerivation()

  public init(profile: CombinedInstrumentProfile) {
    self.profile = profile
    self.extractor = CanResponseExtractor(responseCANID: profile.responseCANID, isoTP: true)
  }

  public func reset(
    distance: Int,
    unit: DistanceUnit,
    nextServiceDate: Date,
    using channel: any DiagnosticCommanding
  ) async throws -> ServiceReminderOutcome {
    let intervalKilometres = unit.toKilometres(distance)
    try validateInput(intervalKilometres, date: nextServiceDate)
    try await configure(profile.configurationCommands, channel)

    let session = try extractor.extract(
      await channel.execute(profile.sessionCommand, intent: .read))
    guard let securityKey = securityKey(for: session) else { return .blocked }
    let seedResponse = try extractor.extract(
      await channel.execute(profile.seedCommand, intent: .read))
    guard seedResponse.hasPrefix(profile.seedPositivePrefix),
      seedResponse.count == profile.seedPositivePrefix.count + 32
    else { return .blocked }
    let derived = try keyDerivation.derive(
      seedHex: String(seedResponse.dropFirst(profile.seedPositivePrefix.count)),
      aesKeyHex: securityKey.aesKey)
    let keyResponse = try extractor.extract(
      await channel.execute(profile.keyRequestPrefix + derived, intent: .read))
    guard keyResponse == profile.keyPositivePrefix else { return .blocked }

    let a500 = try extractor.extract(await channel.execute(profile.a500Command, intent: .read))
    let a000 = try extractor.extract(await channel.execute(profile.a000Command, intent: .read))
    let a010 = try extractor.extract(await channel.execute(profile.a010Command, intent: .read))
    guard a010.hasPrefix("62A010") else { return .blocked }

    let built: CombinedServiceReminderPayload
    do {
      built = try CombinedServiceReminderPayloadBuilder(profile: profile).build(
        strategy: profile.strategy,
        intervalKilometres: intervalKilometres,
        nextServiceDate: nextServiceDate,
        a500Payload: a500,
        a000Payload: a000)
    } catch is DiagnosticParseError {
      return .blocked
    }

    let flowControl = try await channel.execute(built.frames[0], intent: .write)
    guard isContinueToSend(flowControl) else { return .blocked }
    for frame in built.frames.dropFirst().dropLast() {
      _ = try await channel.execute(frame, intent: .write)
    }
    guard let finalFrame = built.frames.last else { return .blocked }
    let finalRaw = try await channel.execute(finalFrame, intent: .write)
    let positive: String
    do {
      positive = try extractor.extract(finalRaw)
    } catch is DiagnosticParseError {
      return .partiallyApplied
    }
    guard positive == profile.writePositiveResponse else { return .partiallyApplied }

    let verifyA000Raw = try await channel.execute(profile.a000Command, intent: .read)
    let verifyA010Raw = try await channel.execute(profile.a010Command, intent: .read)
    let verifyA000: String
    let verifyA010: String
    do {
      verifyA000 = try extractor.extract(verifyA000Raw)
      verifyA010 = try extractor.extract(verifyA010Raw)
    } catch is DiagnosticParseError {
      return .partiallyApplied
    }
    guard verifyA010.hasPrefix("62A010"),
      verify(response: verifyA000, payload: built, date: nextServiceDate)
    else { return .partiallyApplied }
    return .committed(odometerKilometres: built.odometerKilometres)
  }

  private func validateInput(_ distance: Int, date: Date) throws {
    guard
      ServiceIntervalConstraints(
        step: profile.inputStepKilometres,
        minimum: profile.minimumDistanceKilometres,
        maximum: profile.maximumDistanceKilometres
      ).accepts(distance)
    else { throw DiagnosticParseError.unexpectedResponse }
    let calendar = Calendar(identifier: .gregorian)
    let selected = calendar.startOfDay(for: date)
    let today = calendar.startOfDay(for: Date())
    guard let latest = calendar.date(byAdding: .year, value: 2, to: today),
      selected >= today, selected <= latest
    else { throw DiagnosticParseError.unexpectedResponse }
  }

  private func securityKey(for sessionResponse: String) -> InstrumentSecurityKey? {
    guard sessionResponse.hasPrefix(profile.sessionPositivePrefix) else { return nil }
    return profile.securityKeys.first {
      $0.timingResponseSuffix.map(sessionResponse.hasSuffix) == true
    } ?? profile.securityKeys.first { $0.timingResponseSuffix == nil }
  }

  private func isContinueToSend(_ response: String) -> Bool {
    let header = profile.responseCANID.uppercased()
    let lines = response.split(whereSeparator: \.isNewline).map {
      $0.filter(\.isHexDigit).uppercased()
    }
    let data =
      lines.first { $0.hasPrefix(header) }.map { String($0.dropFirst(header.count)) }
      ?? (lines.count == 1 ? lines[0] : nil)
    return data?.hasPrefix("30") == true
  }

  private func verify(
    response: String,
    payload: CombinedServiceReminderPayload,
    date: Date
  ) -> Bool {
    guard let data = try? didData(response, did: "A000") else { return false }
    let components = Calendar(identifier: .gregorian).dateComponents(
      [.year, .month, .day], from: date)
    guard let year = components.year, let month = components.month, let day = components.day else {
      return false
    }
    let encodedDate = [UInt8(year - profile.yearBase), UInt8(month), UInt8(day)]
    switch payload.resolvedStrategy {
    case .updatedCombined:
      return data.count == 12
        && unsignedInteger(Array(data[3..<6])) == payload.nextServiceOdometerKilometres
        && Array(data[9..<12]) == encodedDate
    case .hybridCombined:
      guard data.count == 5, let divisor = profile.hybridOdometerDivisorKilometres else {
        return false
      }
      return unsignedInteger(Array(data[0..<2])) == payload.nextServiceOdometerKilometres / divisor
        && Array(data[2..<5]) == encodedDate
    case .originalSplit, .adaptiveCombined:
      return false
    }
  }
}

private func didData(_ payload: String, did: String, expectedCount: Int? = nil) throws -> [UInt8] {
  let normalized = payload.filter(\.isHexDigit).uppercased()
  let prefix = "62\(did)"
  guard normalized.hasPrefix(prefix) else { throw DiagnosticParseError.unexpectedResponse }
  let data = try hexBytes(String(normalized.dropFirst(prefix.count)))
  guard expectedCount == nil || data.count == expectedCount else {
    throw DiagnosticParseError.unexpectedResponse
  }
  return data
}

private func addWithoutOverflow(_ lhs: Int, _ rhs: Int) throws -> Int {
  let (result, overflow) = lhs.addingReportingOverflow(rhs)
  guard !overflow else { throw DiagnosticParseError.unexpectedResponse }
  return result
}

private func unsignedInteger(_ bytes: [UInt8]) -> Int {
  bytes.reduce(0) { result, byte in (result << 8) | Int(byte) }
}

private func bytes(_ value: Int, count: Int) -> [UInt8] {
  (0..<count).map { index in UInt8(truncatingIfNeeded: value >> ((count - index - 1) * 8)) }
}

private func isoTPFrames(_ payload: [UInt8]) -> [String] {
  precondition((8...0xFFF).contains(payload.count))
  var frames = [
    [UInt8(0x10 | payload.count >> 8), UInt8(truncatingIfNeeded: payload.count)]
      + Array(payload.prefix(6))
  ]
  var offset = 6
  var sequence = 1
  while offset < payload.count {
    let end = min(offset + 7, payload.count)
    frames.append([UInt8(0x20 | sequence & 0x0F)] + Array(payload[offset..<end]))
    offset = end
    sequence += 1
  }
  return frames.map { hex($0 + Array(repeating: 0, count: 8 - $0.count)) }
}
