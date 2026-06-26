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
            // 만료되지 않는 구조적 JWT를 저장해 refresh 시도/세션 만료 폴백을 막는다.
            // (가짜 비-JWT 토큰은 isTokenExpired=true → refresh 실패 → 로그아웃됨)
            #if DEBUG
            let accessToken = UITestNetworkStub.makeSessionToken()
            #else
            let accessToken = "ui-test-access-token"
            #endif
            tokenStore.save(accessToken: accessToken, refreshToken: "ui-test-refresh-token")
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
                .onOpenURL(perform: handleIncomingURL)
        }
    }

    /// 위젯 / 외부 딥링크 진입점. gainsy:// 스킴만 처리.
    private func handleIncomingURL(_ url: URL) {
        guard url.scheme == WidgetConstants.urlScheme,
              url.host == "widget" else { return }

        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        switch path {
        case "calorie":
            PushRouter.shared.deliver(type: "WIDGET_CALORIE")
        case "goal":
            PushRouter.shared.deliver(type: "WIDGET_GOAL")
        case "streak":
            PushRouter.shared.deliver(type: "WIDGET_STREAK")
        default:
            break
        }
    }
}
