import Foundation

enum AuthStatus: Equatable {
    case loading
    case authenticated
    case unauthenticated
}

@MainActor
final class AuthState: ObservableObject {
    @Published private(set) var status: AuthStatus = .loading

    private let tokenStore: TokenStore

    init(tokenStore: TokenStore = TokenStore()) {
        self.tokenStore = tokenStore
        checkPersistedAuth()
    }

    func saveAndAuthenticate(tokenResponse: TokenResponse) {
        tokenStore.save(
            accessToken: tokenResponse.accessToken,
            refreshToken: tokenResponse.refreshToken
        )
        status = .authenticated
    }

    func setUnauthenticated() {
        tokenStore.clearTokens()
        status = .unauthenticated
    }

    private func checkPersistedAuth() {
        status = tokenStore.accessToken != nil ? .authenticated : .unauthenticated
    }
}
