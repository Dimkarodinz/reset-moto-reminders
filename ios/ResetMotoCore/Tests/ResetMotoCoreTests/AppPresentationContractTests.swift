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
}
