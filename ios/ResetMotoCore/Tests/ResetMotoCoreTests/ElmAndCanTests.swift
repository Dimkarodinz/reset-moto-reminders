import XCTest

@testable import ResetMotoCore

final class ElmAndCanTests: XCTestCase {
  func testFragmentedElmReplyCompletesOnlyAtPromptAndRemovesEcho() throws {
    var assembler = ElmResponseAssembler()
    XCTAssertNil(try assembler.append(Data("ATI\r".utf8)))
    XCTAssertNil(try assembler.append(Data("vLinker MC".utf8)))
    let frame = try XCTUnwrap(assembler.append(Data("\r>".utf8)))

    XCTAssertEqual("vLinker MC", frame.normalized(removingEcho: "ATI"))
    XCTAssertEqual(3, frame.fragmentCount)
  }

  func testAssemblerReturnsBackToBackPromptFramesWithoutLosingBytes() throws {
    var assembler = ElmResponseAssembler()
    let first = try XCTUnwrap(assembler.append(Data("OK\r>NO DATA\r>".utf8)))
    XCTAssertEqual("OK", first.normalized(removingEcho: nil))
    let second = try XCTUnwrap(assembler.nextFrame())
    XCTAssertEqual("NO DATA", second.normalized(removingEcho: nil))
  }

  func testEngineSingleFrameExtraction() throws {
    let extractor = CanResponseExtractor(responseCANID: "18DAF1D5", isoTP: true)
    XCTAssertEqual(
      "59010C000000",
      try extractor.extract("18DAF1D5 06 59010C000000 AA")
    )
  }

  func testEngineMultiFrameExtraction() throws {
    let extractor = CanResponseExtractor(responseCANID: "18DAF1D5", isoTP: true)
    let raw = "18DAF1D5 10 0B 59020C157700\n18DAF1D5 21 0812345608AAAA"
    XCTAssertEqual("59020C1577000812345608", try extractor.extract(raw))
  }

  func testInstrumentRawFrameExtraction() throws {
    let extractor = CanResponseExtractor(responseCANID: "704", isoTP: false)
    XCTAssertEqual("DE303433FFFFFFFF", try extractor.extract("704 DE303433FFFFFFFF"))
  }

  func testNoDataIsNotTreatedAsHex() {
    let extractor = CanResponseExtractor(responseCANID: "704", isoTP: false)
    XCTAssertThrowsError(try extractor.extract("NO DATA"))
  }

  func testConfiguredHeaderRejectsAReplyFromAnotherModule() {
    let extractor = CanResponseExtractor(responseCANID: "704", isoTP: false)

    XCTAssertThrowsError(try extractor.extract("7E8 DE303433FFFFFFFF")) { error in
      XCTAssertEqual(error as? DiagnosticParseError, .unexpectedResponse)
    }
  }

  func testCommandCompletionNeedsBothGattAcknowledgementAndElmPrompt() {
    var responseFirst = CommandCompletionGate()
    responseFirst.receive(response: "OK")
    XCTAssertNil(responseFirst.completedResponse)
    responseFirst.acknowledgeWrite()
    XCTAssertEqual("OK", responseFirst.completedResponse)

    var acknowledgementFirst = CommandCompletionGate()
    acknowledgementFirst.acknowledgeWrite()
    XCTAssertNil(acknowledgementFirst.completedResponse)
    acknowledgementFirst.receive(response: "OK")
    XCTAssertEqual("OK", acknowledgementFirst.completedResponse)
  }

  func testWarmStartAcceptsTheObservedElmIdentityBanner() {
    XCTAssertTrue(configurationAccepted(command: "ATWS", response: "ELM327 v2.2"))
  }

  func testOrdinaryConfigurationRequiresAnOkLineAndRejectsEchoOnly() {
    XCTAssertTrue(configurationAccepted(command: "ATE0", response: "SEARCHING…\nOK"))
    XCTAssertFalse(configurationAccepted(command: "ATE0", response: "ATE0"))
  }

  func testConfigurationFailureHasAnActionableUserMessage() {
    XCTAssertEqual(
      "The adapter rejected setup command ATWS. Disconnect and try again.",
      DiagnosticOperationError.configurationRejected("ATWS").errorDescription
    )
  }
}
