import SwiftUI
import Firebase

@main
struct HealthCareApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    @StateObject private var authState = AuthState()
    @StateObject private var appContainer = AppContainer()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(authState)
                .environmentObject(appContainer)
        }
    }
}
