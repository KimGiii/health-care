import Foundation

/// 소셜로그인 ID 토큰 제공자(Apple, Google) 추상화.
/// 실제 SDK 코디네이터는 다음 단계에서 구현되며, 테스트에서는 mock 으로 교체할 수 있다.
protocol SocialIdentityTokenProvider: Sendable {
    func fetchIdToken() async throws -> String
}

/// 신규 소셜 가입자에게 약관 동의 UI 를 띄우기 위한 일시적 상태.
/// LoginView/SignUpView 가 이 값을 관찰해 sheet 를 띄운다.
struct PendingSocialConsent: Identifiable, Equatable {
    let id = UUID()
    let provider: SocialAuthProvider
    let idToken: String
}

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    /// 신규 가입자가 발견되면 set 되어 약관 동의 sheet 를 트리거한다.
    @Published var pendingConsent: PendingSocialConsent?

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

    /// 2-step 소셜로그인: 토큰 fetch → /check → 기존 사용자면 즉시 로그인, 신규면 동의 sheet 트리거.
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
            // Apple Sign In 직후 iOS가 LAN 라우팅을 복구할 때까지 대기.
            // 고정 딜레이 대신 NWPathMonitor로 실제 경로 상태를 확인한다.
            await NetworkPathWaiter.waitForLocalNetwork()
            let body = try apiClient.encode(SocialLoginRequest(idToken: idToken))
            let check: SocialLoginCheckResponse = try await apiClient.request(
                .socialLoginCheck(provider: provider, body: body)
            )
            if !check.newUser, let tokens = check.tokens {
                authState.saveAndAuthenticate(tokenResponse: tokens)
            } else {
                // 신규 사용자 — 약관 동의 sheet 노출. 동의 완료는 completeSocialSignUp 에서 처리.
                pendingConsent = PendingSocialConsent(provider: provider, idToken: idToken)
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch is CancellationError {
            errorMessage = nil
        } catch {
            errorMessage = providerErrorMessage(for: provider)
        }
    }

    /// 약관 동의 sheet 에서 "동의하고 계속" 탭 시 호출. /commit 으로 신규 사용자 생성.
    func completeSocialSignUp(
        apiClient: APIClient,
        authState: AuthState
    ) async {
        guard let pending = pendingConsent else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let body = try apiClient.encode(SocialLoginCommitRequest(
                idToken: pending.idToken,
                agreedToTerms: true,
                agreedToPrivacy: true
            ))
            let tokenResponse: TokenResponse = try await apiClient.request(
                .socialLoginCommit(provider: pending.provider, body: body)
            )
            pendingConsent = nil
            authState.saveAndAuthenticate(tokenResponse: tokenResponse)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = providerErrorMessage(for: pending.provider)
        }
    }

    /// 사용자가 동의를 거부/취소한 경우.
    func cancelSocialSignUp() {
        pendingConsent = nil
    }

    private func providerErrorMessage(for provider: SocialAuthProvider) -> String {
        switch provider {
        case .apple:  return "Apple 로그인에 실패했습니다."
        case .google: return "Google 로그인에 실패했습니다."
        }
    }
}
