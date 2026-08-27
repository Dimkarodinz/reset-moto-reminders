import Foundation
import Testing

@Suite("iOS app presentation contract")
struct AppPresentationContractTests {
  @Test("primary screen uses five full-width centered action labels and consistent margins")
  func actionLayout() throws {
    let source = try String(contentsOf: appSource("ContentView.swift"), encoding: .utf8)

    #expect(source.components(separatedBy: "ActionButtonLabel(").count - 1 == 5)
    #expect(source.contains(".frame(maxWidth: .infinity, minHeight: 28, alignment: .center)"))
    #expect(source.contains(".frame(maxWidth: 640, alignment: .leading)"))
    #expect(source.contains(".padding(.horizontal, 20)"))
  }

  @Test("app has the Android-family icon and a spaced display name")
  func iconAndName() throws {
    let project = try String(
      contentsOf:
        iosRoot
        .appendingPathComponent("ResetMotoReminders/ResetMotoReminders.xcodeproj/project.pbxproj"),
      encoding: .utf8)
    let catalog = try String(
      contentsOf: appSource("Assets.xcassets/AppIcon.appiconset/Contents.json"),
      encoding: .utf8)
    let icon = try Data(contentsOf: appSource("Assets.xcassets/AppIcon.appiconset/AppIcon.png"))

    #expect(project.contains("ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon"))
    #expect(project.contains("INFOPLIST_KEY_CFBundleDisplayName = \"Reset Moto Reminders\""))
    #expect(project.contains("PRODUCT_NAME = \"Reset Moto Reminders\""))
    #expect(catalog.contains("AppIcon.png"))
    #expect(catalog.contains("1024x1024"))
    #expect(icon.count > 25)
    #expect(icon[25] == 2, "The App Store icon must be true-color without an alpha channel")
  }

  @Test("service input is dismissible, step-validated, and refers to the motorcycle date")
  func serviceInputSafety() throws {
    let source = try String(contentsOf: appSource("ContentView.swift"), encoding: .utf8)
    let english = try #require(localizedStrings("en"))

    #expect(source.contains("@FocusState private var distanceFieldFocused"))
    #expect(source.contains("ToolbarItemGroup(placement: .keyboard)"))
    #expect(source.contains("Button(L10n.text(\"action_done\"))"))
    #expect(source.contains("value.isMultiple(of: 100)"))
    #expect(source.contains("ios_interval_help"))
    #expect(english["ios_interval_help"]?.contains("motorcycle’s date") == true)
    #expect(!english.values.contains(where: { $0.contains("iPhone date") }))
  }

  @Test("dashboard fingerprint is explained instead of presented as an unknown status")
  func dashboardFingerprintExplanation() throws {
    let source = try String(contentsOf: appSource("ContentView.swift"), encoding: .utf8)
    let english = try #require(localizedStrings("en"))

    #expect(source.contains("ios_dashboard_supported"))
    #expect(source.contains("ios_fingerprint_format"))
    #expect(english["ios_fingerprint_format"]?.contains("not a fault code") == true)
    #expect(!source.contains("LabeledContent(\"Dashboard status\", value: dashboard.statusASCII)"))
  }

  @Test("every supported iPhone locale contains the same localized keys")
  func localizationCompleteness() throws {
    let expectedLocales = Set(["en", "de", "es", "fr", "uk"])
    let localizationRoot = appSource("")
    let localeDirectories = try FileManager.default.contentsOfDirectory(
      at: localizationRoot,
      includingPropertiesForKeys: nil
    ).filter { $0.pathExtension == "lproj" }
    let locales = Set(localeDirectories.map { $0.deletingPathExtension().lastPathComponent })

    #expect(locales == expectedLocales)

    let dictionaries = try Dictionary(
      uniqueKeysWithValues: localeDirectories.map { directory in
        let locale = directory.deletingPathExtension().lastPathComponent
        let stringsURL = directory.appendingPathComponent("Localizable.strings")
        let infoURL = directory.appendingPathComponent("InfoPlist.strings")
        let strings = try #require(NSDictionary(contentsOf: stringsURL) as? [String: String])
        let info = try #require(NSDictionary(contentsOf: infoURL) as? [String: String])
        #expect(info["NSBluetoothAlwaysUsageDescription"] != nil)
        return (locale, strings)
      })

    let englishKeys = Set(try #require(dictionaries["en"]).keys)
    #expect(englishKeys.count >= 45)
    for locale in expectedLocales {
      #expect(Set(try #require(dictionaries[locale]).keys) == englishKeys)
    }
    #expect(dictionaries["de"]?["button_connect"] == "Verbinden")
    #expect(dictionaries["es"]?["dtc_read_title"] == "Códigos de avería")
    #expect(dictionaries["fr"]?["service_reset_title"] == "Réinitialiser le rappel d'entretien")
    #expect(dictionaries["uk"]?["instrument_read_title"] == "Інформація панелі приладів")

    for key in englishKeys {
      let englishArguments = formatArguments(in: try #require(dictionaries["en"]?[key]))
      for locale in expectedLocales {
        #expect(formatArguments(in: try #require(dictionaries[locale]?[key])) == englishArguments)
      }
    }
  }

  private var iosRoot: URL {
    URL(fileURLWithPath: #filePath)
      .deletingLastPathComponent()
      .deletingLastPathComponent()
      .deletingLastPathComponent()
      .deletingLastPathComponent()
  }

  private func appSource(_ relativePath: String) -> URL {
    iosRoot.appendingPathComponent("ResetMotoReminders/ResetMotoReminders/\(relativePath)")
  }

  private func localizedStrings(_ locale: String) -> [String: String]? {
    NSDictionary(
      contentsOf: appSource("\(locale).lproj/Localizable.strings")) as? [String: String]
  }

  private func formatArguments(in value: String) -> [String] {
    let expression = try? NSRegularExpression(pattern: "%[0-9]+\\$[@d]")
    let range = NSRange(value.startIndex..., in: value)
    return expression?.matches(in: value, range: range).compactMap {
      Range($0.range, in: value).map { String(value[$0]) }
    } ?? []
  }
}
