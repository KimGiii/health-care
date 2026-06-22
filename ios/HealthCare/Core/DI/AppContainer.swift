import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let tokenStore: TokenStore
    let apiClient: APIClient
    private let fcmTokenUploader: FcmTokenUploader

    init() {
        let tokenStore = TokenStore()

        let configuredBaseURL = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String
        let environmentBaseURL = ProcessInfo.processInfo.environment["BASE_URL"]
        let baseURL = Self.makeBaseURL(from: environmentBaseURL)
            ?? Self.makeBaseURL(from: configuredBaseURL)
            ?? Self.makeBaseURL(from: Constants.API.defaultBaseURL)!

        // 진단 로그 — 어떤 BASE_URL이 적용됐는지 확인
        NSLog("[AppContainer] Info.plist API_BASE_URL=\(configuredBaseURL ?? "<nil>")")
        NSLog("[AppContainer] env BASE_URL=\(environmentBaseURL ?? "<nil>")")
        NSLog("[AppContainer] resolved baseURL=\(baseURL.absoluteString)")

        let apiClient: APIClient
        #if DEBUG
        if UITestNetworkStub.isEnabled {
            NSLog("[AppContainer] UI test network stub enabled")
            apiClient = APIClient(baseURL: baseURL, tokenStore: tokenStore, session: UITestNetworkStub.makeSession())
        } else {
            apiClient = APIClient(baseURL: baseURL, tokenStore: tokenStore)
        }
        #else
        apiClient = APIClient(baseURL: baseURL, tokenStore: tokenStore)
        #endif
        self.tokenStore = tokenStore
        self.apiClient  = apiClient
        self.fcmTokenUploader = FcmTokenUploader(apiClient: apiClient)
    }

    private static func makeBaseURL(from rawValue: String?) -> URL? {
        guard let rawValue else { return nil }

        let value = rawValue.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !value.isEmpty, !value.contains("$(") else { return nil }

        guard let url = URL(string: value),
              let scheme = url.scheme,
              let host = url.host,
              ["http", "https"].contains(scheme),
              host != "api" else {
            return nil
        }

        return url
    }
}
