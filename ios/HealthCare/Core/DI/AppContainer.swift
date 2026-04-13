import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let tokenStore: TokenStore
    let apiClient: APIClient

    init() {
        let tokenStore = TokenStore()

        #if DEBUG
        let defaultBaseURL = "http://localhost:8080"
        #else
        let defaultBaseURL = "https://api.healthcare.app"
        #endif

        let baseURL = URL(
            string: ProcessInfo.processInfo.environment["BASE_URL"] ?? defaultBaseURL
        )!

        self.tokenStore = tokenStore
        self.apiClient  = APIClient(baseURL: baseURL, tokenStore: tokenStore)
    }
}
