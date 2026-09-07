import Foundation

public enum ProfileError: Error, Equatable {
  case resourceMissing
  case unsupportedSchema(Int)
  case invalid(String)
}

public struct ResetMotoProfile: Codable, Equatable, Sendable {
  public let schemaVersion: Int
  public let motorcycle: MotorcycleProfile
  public let motorcycles: [MotorcycleCompatibilityProfile]
  public let adapters: [AdapterProfile]
  public let engine: EngineProfile
  public let instrument: InstrumentProfile
  public let combinedInstrumentFamilies: [CombinedInstrumentProfile]
  public let dtcDescriptions: [String: String]
  public let dtcDescriptionsByLanguage: [String: [String: String]]?

  public var defaultMotorcycle: MotorcycleCompatibilityProfile {
    motorcycles.first { $0.id == motorcycle.id }!
  }

  public func combinedInstrumentFamily(
    for motorcycle: MotorcycleCompatibilityProfile
  ) -> CombinedInstrumentProfile? {
    combinedInstrumentFamilies.first { $0.id == motorcycle.instrumentFamilyID }
  }

  public var dtcDescriptionLanguages: [String] {
    let localized = dtcDescriptionsByLanguage?.keys.map { $0 } ?? []
    return Array(Set(localized).union(["en"])).sorted()
  }

  public func dtcDescriptions(forLanguage identifier: String?) -> [String: String] {
    guard let identifier else { return dtcDescriptions }
    let language =
      identifier
      .replacingOccurrences(of: "_", with: "-")
      .split(separator: "-")
      .first
      .map { String($0).lowercased() } ?? "en"
    return dtcDescriptionsByLanguage?[language] ?? dtcDescriptions
  }

  public static func decode(_ data: Data) throws -> ResetMotoProfile {
    let schema = try JSONSerialization.jsonObject(with: data) as? [String: Any]
    let version = schema?["schemaVersion"] as? Int ?? -1
    guard version == 2 else { throw ProfileError.unsupportedSchema(version) }
    do {
      let profile = try JSONDecoder().decode(ResetMotoProfile.self, from: data)
      try profile.validate()
      return profile
    } catch let error as ProfileError {
      throw error
    } catch {
      throw ProfileError.invalid(error.localizedDescription)
    }
  }

  public static func bundledTiger900() throws -> ResetMotoProfile {
    guard let url = Bundle.module.url(forResource: "tiger-900-profile", withExtension: "json")
    else {
      throw ProfileError.resourceMissing
    }
    return try decode(Data(contentsOf: url))
  }

  private func validate() throws {
    guard !motorcycles.isEmpty,
      Set(motorcycles.map(\.id)).count == motorcycles.count,
      motorcycles.contains(where: { $0.id == motorcycle.id })
    else {
      throw ProfileError.invalid("Incomplete motorcycle catalogue")
    }
    let modelCodes = motorcycles.flatMap(\.modelCodes)
    guard Set(modelCodes).count == modelCodes.count else {
      throw ProfileError.invalid("Duplicate motorcycle model code")
    }
    guard
      motorcycles.allSatisfy({ candidate in
        candidate.validationStatus != .experimental
          || candidate.capabilities.all.allSatisfy { $0 != .validated }
      })
    else {
      throw ProfileError.invalid("Experimental motorcycle cannot claim a validated capability")
    }
    guard Set(combinedInstrumentFamilies.map(\.id)).count == combinedInstrumentFamilies.count,
      combinedInstrumentFamilies.allSatisfy(\.isValid)
    else {
      throw ProfileError.invalid("Incomplete combined instrument profile")
    }
    for candidate in motorcycles where candidate.capabilities.serviceReset != .unavailable {
      switch candidate.serviceReminderStrategy {
      case .originalSplit:
        guard candidate.instrumentFamilyID == "triumph-original-tft" else {
          throw ProfileError.invalid(
            "Original service reset references the wrong instrument family")
        }
      case .updatedCombined, .hybridCombined, .adaptiveCombined:
        guard let family = combinedInstrumentFamily(for: candidate),
          family.strategy == candidate.serviceReminderStrategy
        else {
          throw ProfileError.invalid("Service reset references an unavailable instrument family")
        }
      }
    }
    guard adapters.count >= 1,
      Set(adapters.map(\.id)).count == adapters.count,
      adapters.allSatisfy({ adapter in
        !adapter.id.isEmpty && !adapter.advertisedName.isEmpty
          && !adapter.serviceUUID.isEmpty
          && !adapter.commandCharacteristicUUID.isEmpty
          && !adapter.responseCharacteristicUUID.isEmpty
          && adapter.identifyCommand == "ATI"
      })
    else {
      throw ProfileError.invalid("Incomplete adapter profile")
    }
    guard !engine.configurationCommands.isEmpty,
      !engine.dtcCountCommand.isEmpty,
      !engine.dtcDetailCommand.isEmpty,
      !engine.identityCommand.isEmpty,
      !instrument.configurationCommands.isEmpty,
      !instrument.expectedStatusASCII.isEmpty
    else {
      throw ProfileError.invalid("Incomplete motorcycle command profile")
    }
  }
}

public struct MotorcycleProfile: Codable, Equatable, Sendable {
  public let id: String
  public let manufacturer: String
  public let model: String
  public let modelYear: Int
}

public enum ProfileValidationStatus: String, Codable, Equatable, Sendable {
  case validated
  case experimental
}

public enum CapabilityStatus: String, Codable, Equatable, Sendable {
  case validated
  case experimental
  case unavailable
}

public enum DTCClearStrategy: String, Codable, Equatable, Sendable {
  case securityAccess = "security_access"
  case direct
}

public enum ServiceReminderStrategy: String, Codable, Equatable, Sendable {
  case originalSplit = "original_split"
  case updatedCombined = "updated_combined"
  case hybridCombined = "hybrid_combined"
  case adaptiveCombined = "adaptive_combined"
}

public struct MotorcycleCapabilities: Codable, Equatable, Sendable {
  public let dtcRead: CapabilityStatus
  public let dtcClear: CapabilityStatus
  public let dashboardRead: CapabilityStatus
  public let serviceReset: CapabilityStatus

  fileprivate var all: [CapabilityStatus] {
    [dtcRead, dtcClear, dashboardRead, serviceReset]
  }
}

public struct MotorcycleCompatibilityProfile: Codable, Equatable, Identifiable, Sendable {
  public let id: String
  public let displayName: String
  public let modelCodes: [String]
  public let validationStatus: ProfileValidationStatus
  public let instrumentFamilyID: String
  public let serviceReminderStrategy: ServiceReminderStrategy
  public let dtcClearStrategy: DTCClearStrategy
  public let capabilities: MotorcycleCapabilities
}

public struct AdapterProfile: Codable, Equatable, Sendable {
  public let id: String
  public let advertisedName: String
  public let serviceUUID: String
  public let commandCharacteristicUUID: String
  public let responseCharacteristicUUID: String
  public let identifyCommand: String
  public let promptByte: UInt8
  public let experimental: Bool
}

public struct EngineProfile: Codable, Equatable, Sendable {
  public let configurationCommands: [String]
  public let responseCANID: String
  public let dtcCountCommand: String
  public let dtcDetailCommand: String
  public let extendedSessionCommand: String
  public let seedCommand: String
  public let keyRequestPrefix: String
  public let seedMultiplier: Int
  public let dtcClearCommand: String
  public let identityCommand: String
}

public struct InstrumentProfile: Codable, Equatable, Sendable {
  public let configurationCommands: [String]
  public let responseCANID: String
  public let statusCommand: String
  public let expectedStatusASCII: String
  public let odometerCommand: String
  public let distancePrefixKilometres: String
  public let distancePrefixMiles: String
  public let distanceRawUnit: Int
  public let distanceMinimumRaw: Int
  public let distanceMaximumRaw: Int
  public let datePrefix: String
  public let yearBase: Int
  public let dateFixedSuffix: String
}

public struct InstrumentSecurityKey: Codable, Equatable, Sendable {
  public let id: String
  public let timingResponseSuffix: String?
  public let aesKey: String
}

public struct CombinedInstrumentProfile: Codable, Equatable, Sendable {
  public let id: String
  public let strategy: ServiceReminderStrategy
  public let configurationCommands: [String]
  public let responseCANID: String
  public let requestPrefix: String
  public let inputStepKilometres: Int
  public let hybridOdometerDivisorKilometres: Int?
  public let minimumDistanceKilometres: Int
  public let maximumDistanceKilometres: Int
  public let yearBase: Int
  public let sessionCommand: String
  public let sessionPositivePrefix: String
  public let seedCommand: String
  public let seedPositivePrefix: String
  public let keyRequestPrefix: String
  public let keyPositivePrefix: String
  public let securityKeys: [InstrumentSecurityKey]
  public let a500Command: String
  public let a000Command: String
  public let a010Command: String
  public let writePositiveResponse: String

  fileprivate var isValid: Bool {
    let needsHybridDivisor = strategy == .hybridCombined || strategy == .adaptiveCombined
    return strategy != .originalSplit
      && !configurationCommands.isEmpty
      && !responseCANID.isEmpty
      && !requestPrefix.isEmpty
      && inputStepKilometres > 0
      && minimumDistanceKilometres > 0
      && minimumDistanceKilometres <= maximumDistanceKilometres
      && (!needsHybridDivisor || (hybridOdometerDivisorKilometres ?? 0) > 0)
      && securityKeys.count(where: { $0.timingResponseSuffix == nil }) == 1
      && securityKeys.allSatisfy { $0.aesKey.count == 32 && $0.aesKey.allSatisfy(\.isHexDigit) }
      && [sessionCommand, seedCommand, keyRequestPrefix, a500Command, a000Command, a010Command]
        .allSatisfy { !$0.isEmpty }
  }
}

public struct ServiceIntervalConstraints: Equatable, Sendable {
  public let step: Int
  public let minimum: Int
  public let maximum: Int

  public init(step: Int, minimum: Int, maximum: Int) {
    self.step = step
    self.minimum = minimum
    self.maximum = maximum
  }

  public func accepts(_ value: Int) -> Bool {
    (minimum...maximum).contains(value) && value.isMultiple(of: step)
  }
}
