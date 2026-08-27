import Foundation
import ResetMotoCore

enum L10n {
  static func text(_ key: String) -> String {
    NSLocalizedString(key, bundle: .main, value: key, comment: "")
  }

  static func format(_ key: String, _ arguments: CVarArg...) -> String {
    String(format: text(key), locale: Locale.current, arguments: arguments)
  }

  static func message(for error: Error) -> String {
    switch error {
    case DiagnosticOperationError.configurationRejected(let command):
      return format("ios_error_setup_rejected", command)
    case DiagnosticOperationError.inconsistentDTCCount(let reported, let decoded):
      return format("ios_error_dtc_count_mismatch", reported, decoded)
    case DiagnosticParseError.noResponse:
      return text("error_ecu_no_response")
    case is DiagnosticParseError:
      return text("ios_error_unrecognized_response")
    case is ElmFramingError:
      return text("ios_error_response_too_large")
    default:
      return (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
    }
  }
}
