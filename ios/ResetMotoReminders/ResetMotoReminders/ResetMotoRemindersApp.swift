import SwiftUI

@main
struct ResetMotoRemindersApp: App {
  @Environment(\.scenePhase) private var scenePhase
  @StateObject private var session = AdapterSession()

  var body: some Scene {
    WindowGroup {
      ContentView(session: session)
        .preferredColorScheme(.dark)
    }
    .onChange(of: scenePhase) { newPhase in
      if newPhase == .background {
        session.applicationDidEnterBackground()
      }
    }
  }
}
