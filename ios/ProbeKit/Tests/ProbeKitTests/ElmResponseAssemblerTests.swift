import XCTest
@testable import ProbeKit

final class ElmResponseAssemblerTests: XCTestCase {
    func testFragmentedIdentityResponseCompletesOnPrompt() {
        // The expected response class at the inferred MTU of 23: identity
        // text and prompt split across notifications.
        var assembler = ElmResponseAssembler()
        XCTAssertFalse(assembler.append(Data("ELM327 v2.2".utf8)))
        XCTAssertFalse(assembler.append(Data("\r".utf8)))
        XCTAssertTrue(assembler.append(Data("\r>".utf8)))

        XCTAssertTrue(assembler.isComplete)
        XCTAssertEqual(3, assembler.fragmentCount)
        XCTAssertEqual("ELM327 v2.2\n>", assembler.text)
    }

    func testSingleFragmentResponseCompletesImmediately() {
        var assembler = ElmResponseAssembler()
        XCTAssertTrue(assembler.append(Data("OK\r>".utf8)))
        XCTAssertEqual("OK\n>", assembler.text)
    }

    func testIncompleteResponseStaysIncomplete() {
        var assembler = ElmResponseAssembler()
        XCTAssertFalse(assembler.append(Data("ELM327".utf8)))
        XCTAssertFalse(assembler.isComplete)
    }

    func testRawHexPreservesEveryByte() {
        var assembler = ElmResponseAssembler()
        assembler.append(Data([0x41, 0x54, 0x49, 0x0D, 0x3E]))
        XCTAssertEqual("4154490D3E", assembler.rawHex)
    }

    func testNonAsciiBytesAreVisibleButHarmless() {
        var assembler = ElmResponseAssembler()
        assembler.append(Data([0x00, 0xFF, 0x41, 0x3E]))
        XCTAssertTrue(assembler.isComplete)
        XCTAssertEqual("..A>", assembler.text)
        XCTAssertEqual("00FF413E", assembler.rawHex)
    }

    func testEmptyFragmentDoesNotComplete() {
        var assembler = ElmResponseAssembler()
        XCTAssertFalse(assembler.append(Data()))
        XCTAssertEqual(1, assembler.fragmentCount)
    }
}
