import Foundation

public enum ProfileError: Error, Equatable {
  case resourceMissing
  case unsupportedSchema(Int)
  case invalid(String)
}

public struct ResetMotoProfile: Codable, Equatable, Sendable {
  public let schemaVersion: Int
  public let motorcycle: MotorcycleProfile
  public let adapter: AdapterProfile
  public let engine: EngineProfile
  public let instrument: InstrumentProfile
  public let dtcDescriptions: [String: String]
  public let dtcDescriptionsByLanguage: [String: [String: String]]?

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
    guard version == 1 else { throw ProfileError.unsupportedSchema(version) }
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
    guard motorcycle.id == "triumph-tiger-900-gt-pro-2021" else {
      throw ProfileError.invalid("Unsupported motorcycle profile")
    }
    guard !adapter.advertisedName.isEmpty,
      !adapter.serviceUUID.isEmpty,
      !adapter.commandCharacteristicUUID.isEmpty,
      !adapter.responseCharacteristicUUID.isEmpty,
      adapter.identifyCommand == "ATI"
    else {
      throw ProfileError.invalid("Incomplete adapter profile")
    }
    guard !engine.configurationCommands.isEmpty,
      !engine.dtcCountCommand.isEmpty,
      !engine.dtcDetailCommand.isEmpty,
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

public struct AdapterProfile: Codable, Equatable, Sendable {
  public let advertisedName: String
  public let serviceUUID: String
  public let commandCharacteristicUUID: String
  public let responseCharacteristicUUID: String
  public let identifyCommand: String
  public let promptByte: UInt8
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
