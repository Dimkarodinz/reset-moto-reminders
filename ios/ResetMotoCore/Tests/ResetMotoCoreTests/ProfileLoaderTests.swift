import XCTest

@testable import ResetMotoCore

final class ProfileLoaderTests: XCTestCase {
  func testBundledProfilePinsSupportedMotorcycleAndPrimaryGattChannel() throws {
    let profile = try ResetMotoProfile.bundledTiger900()

    XCTAssertEqual(1, profile.schemaVersion)
    XCTAssertEqual("triumph-tiger-900-gt-pro-2021", profile.motorcycle.id)
    XCTAssertEqual("vLinker MC-IOS", profile.adapter.advertisedName)
    XCTAssertEqual("18F0", profile.adapter.serviceUUID)
    XCTAssertEqual("2AF1", profile.adapter.commandCharacteristicUUID)
    XCTAssertEqual("2AF0", profile.adapter.responseCharacteristicUUID)
    XCTAssertEqual("ATI", profile.adapter.identifyCommand)
    XCTAssertEqual("043", profile.instrument.expectedStatusASCII)
    XCTAssertEqual("03190108", profile.engine.dtcCountCommand)
    XCTAssertEqual("03190208", profile.engine.dtcDetailCommand)
    XCTAssertEqual("0414FFFFFF", profile.engine.dtcClearCommand)
  }

  func testRejectsUnsupportedSchemaBeforeUsingCommands() throws {
    let data = Data(#"{"schemaVersion":99}"#.utf8)
    XCTAssertThrowsError(try ResetMotoProfile.decode(data)) { error in
      XCTAssertEqual(error as? ProfileError, .unsupportedSchema(99))
    }
  }

  func testRejectsIncompleteProfile() throws {
    let data = Data(#"{"schemaVersion":1}"#.utf8)
    XCTAssertThrowsError(try ResetMotoProfile.decode(data))
  }

  func testBundledProfileContainsEverySupportedDtcLanguage() throws {
    let profile = try ResetMotoProfile.bundledTiger900()

    XCTAssertEqual(Set(["de", "en", "es", "fr", "uk"]), Set(profile.dtcDescriptionLanguages))
    XCTAssertEqual(
      "Las señales del interruptor de freno 1 y del interruptor de freno 2 no coinciden",
      profile.dtcDescriptions(forLanguage: "es-ES")["P1577-00"])
    XCTAssertEqual(
      "Antriebsstrang-Diagnosefehlercode {code}. Es ist keine validierte Herstellerbeschreibung verfügbar.",
      profile.dtcDescriptions(forLanguage: "de-DE")["__generic_P"])
    XCTAssertEqual(
      profile.dtcDescriptions(forLanguage: "en"),
      profile.dtcDescriptions(forLanguage: "pt-BR"),
      "Unknown languages must fail back to English")
    let englishKeys = Set(profile.dtcDescriptions(forLanguage: "en").keys)
    for language in profile.dtcDescriptionLanguages {
      XCTAssertEqual(
        englishKeys, Set(profile.dtcDescriptions(forLanguage: language).keys),
        "Every supported language must retain every English DTC fallback")
    }
  }
}
