import SwiftUI
import Firebase

@main
struct HealthCareApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var delegate

    @StateObject private var authState: AuthState
    @StateObject private var appContainer = AppContainer()
    @StateObject private var localeManager = LocaleManager.shared
    @AppStorage("appTheme") private var appThemeRawValue = AppTheme.system.rawValue

    private var selectedTheme: AppTheme {
        AppTheme(rawValue: appThemeRawValue) ?? .system
    }

    init() {
        let tokenStore = TokenStore()
        if ProcessInfo.processInfo.arguments.contains("UI_TEST_RESET_STATE") {
            tokenStore.clearTokens()
        }
        if ProcessInfo.processInfo.arguments.contains("UI_TEST_AUTHENTICATED") {
            tokenStore.save(accessToken: "ui-test-access-token", refreshToken: "ui-test-refresh-token")
        }
        _authState = StateObject(wrappedValue: AuthState(tokenStore: tokenStore))
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(authState)
                .environmentObject(appContainer)
                .environmentObject(localeManager)
                .environment(\.locale, localeManager.effectiveLocale)
                .preferredColorScheme(selectedTheme.colorScheme)
        }
    }
}
