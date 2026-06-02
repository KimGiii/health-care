import Foundation

/// 소셜로그인 ID 토큰 제공자(Apple, Google) 추상화.
/// 실제 SDK 코디네이터는 다음 단계에서 구현되며, 테스트에서는 mock 으로 교체할 수 있다.
protocol SocialIdentityTokenProvider: Sendable {
    func fetchIdToken() async throws -> String
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    func login(apiClient: APIClient, authState: AuthState) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let body = try apiClient.encode(LoginRequest(email: email, password: password))
            let tokenResponse: TokenResponse = try await apiClient.request(.login(body: body))
            authState.saveAndAuthenticate(tokenResponse: tokenResponse)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "로그인 중 오류가 발생했습니다."
        }
    }

    /// 소셜로그인 공통 흐름: 제공자로부터 ID 토큰을 얻어 백엔드 `/social-login/{provider}` 호출.
    /// 성공 시 `AuthState` 가 onboardingCompleted 에 따라 ProfileSetup 또는 MainTab 으로 전이한다.
    func signInWithSocialProvider(
        _ provider: SocialAuthProvider,
        tokenProvider: SocialIdentityTokenProvider,
        apiClient: APIClient,
        authState: AuthState
    ) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let idToken = try await tokenProvider.fetchIdToken()
            let body = try apiClient.encode(SocialLoginRequest(idToken: idToken))
            let tokenResponse: TokenResponse = try await apiClient.request(
                .socialLogin(provider: provider, body: body)
            )
            authState.saveAndAuthenticate(tokenResponse: tokenResponse)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch is CancellationError {
            // 사용자가 취소한 경우 에러로 표시하지 않음
            errorMessage = nil
        } catch {
            errorMessage = providerErrorMessage(for: provider)
        }
    }

    private func providerErrorMessage(for provider: SocialAuthProvider) -> String {
        switch provider {
        case .apple:  return "Apple 로그인에 실패했습니다."
        case .google: return "Google 로그인에 실패했습니다."
        }
    }
}
