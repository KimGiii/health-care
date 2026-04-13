import Foundation

@MainActor
final class AppContainer: ObservableObject {
    let tokenStore: TokenStore
    let apiClient: APIClient

    init() {
        let tokenStore = TokenStore()
        let baseURL = URL(
            string: ProcessInfo.processInfo.environment["BASE_URL"]
                ?? "https://api.healthcare.app"
        )!
        self.tokenStore = tokenStore
        self.apiClient  = APIClient(baseURL: baseURL, tokenStore: tokenStore)
    }
}
