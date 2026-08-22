import XCTest

@testable import ResetMotoCore

final class DecoderAndBuilderTests: XCTestCase {
  func testDashboardDecodersUseObservedLayouts() throws {
    XCTAssertEqual("043", try InstrumentDecoder.decodeStatus("DE303433FFFFFFFF"))
    let odometer = try InstrumentDecoder.decodeOdometer("8D0100AE76000000")
    XCTAssertEqual(44_662, odometer.kilometres)
    XCTAssertEqual("0xAE76", odometer.raw)
  }

  func testDtcCountAndDetailsDecodeConfirmedRecord() throws {
    let profile = try ResetMotoProfile.bundledTiger900()
    let decoder = DTCDecoder(descriptions: profile.dtcDescriptions)

    XCTAssertEqual(1, try decoder.decodeCount("59010C000001"))
    let dtc = try XCTUnwrap(decoder.decodeDetails("59020C15770008").first)
    XCTAssertEqual("P1577-00", dtc.code)
    XCTAssertTrue(dtc.confirmed)
    XCTAssertEqual("Brake switch 1 and brake switch 2 signals do not match", dtc.message)
  }

  func testUnknownValidDtcStillGetsReadableFallback() throws {
    let decoder = DTCDecoder(descriptions: [:])
    let dtc = try XCTUnwrap(decoder.decodeDetails("59020C12345608").first)
    XCTAssertEqual("P1234-56", dtc.code)
    XCTAssertTrue(dtc.message.contains("Powertrain"))
  }

  func testServiceCommandsSupportKilometresAndMiles() throws {
    let profile = try ResetMotoProfile.bundledTiger900().instrument
    let builder = ServiceReminderCommandBuilder(profile: profile)
    let date = try XCTUnwrap(
      Calendar(identifier: .gregorian).date(from: DateComponents(year: 2027, month: 8, day: 7)))

    XCTAssertEqual(
      ServiceReminderCommands(distance: "3364", date: "5C1B0807016E0000"),
      try builder.build(distance: 10_000, unit: .kilometres, nextServiceDate: date)
    )
    XCTAssertEqual(
      "343C", try builder.build(distance: 6_000, unit: .miles, nextServiceDate: date).distance)
  }

  func testServiceBuilderRejectsUnrepresentableDistance() throws {
    let profile = try ResetMotoProfile.bundledTiger900().instrument
    let builder = ServiceReminderCommandBuilder(profile: profile)
    XCTAssertThrowsError(
      try builder.build(distance: 6_050, unit: .kilometres, nextServiceDate: Date()))
    XCTAssertThrowsError(
      try builder.build(distance: 25_600, unit: .kilometres, nextServiceDate: Date()))
  }

  func testServiceBuilderRejectsDatesOutsideTheTwoYearWindow() throws {
    let profile = try ResetMotoProfile.bundledTiger900().instrument
    let builder = ServiceReminderCommandBuilder(profile: profile)
    let calendar = Calendar(identifier: .gregorian)
    let today = calendar.startOfDay(for: Date())
    let yesterday = try XCTUnwrap(calendar.date(byAdding: .day, value: -1, to: today))
    let latest = try XCTUnwrap(calendar.date(byAdding: .year, value: 2, to: today))
    let tooLate = try XCTUnwrap(calendar.date(byAdding: .day, value: 1, to: latest))

    XCTAssertThrowsError(
      try builder.build(distance: 10_000, unit: .kilometres, nextServiceDate: yesterday))
    XCTAssertThrowsError(
      try builder.build(distance: 10_000, unit: .kilometres, nextServiceDate: tooLate))
  }

  func testSeedKeyDerivationMatchesCapturedPairs() {
    let derivation = SeedKeyDerivation(multiplier: 0x4B48)
    XCTAssertEqual("A018", try derivation.key(seedHex: "188B"))
    XCTAssertEqual("33E0", try derivation.key(seedHex: "871C"))
    XCTAssertEqual(
      "042702A018", try derivation.keyRequest(seedResponse: "6701188B", prefix: "042702"))
  }
}
