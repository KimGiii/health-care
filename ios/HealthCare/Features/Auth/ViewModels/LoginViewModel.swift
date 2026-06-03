import Foundation

/// 소셜로그인 인증 결과. ID 토큰과, 재생(replay) 공격 방지를 위한 원본 nonce 를 함께 전달한다.
/// 백엔드는 `rawNonce` 를 제공자 규칙에 맞게 변환해 ID 토큰의 nonce 클레임과 비교한다.
struct SocialIdentityToken: Sendable, Equatable {
    let idToken: String
    /// 인증 요청에 사용한 원본 nonce. 제공자가 nonce 를 지원하지 않으면 nil.
    let rawNonce: String?
}

/// 소셜로그인 ID 토큰 제공자(Apple, Google) 추상화.
/// 실제 SDK 코디네이터는 다음 단계에서 구현되며, 테스트에서는 mock 으로 교체할 수 있다.
protocol SocialIdentityTokenProvider: Sendable {
    func fetchIdToken() async throws -> SocialIdentityToken
}

/// 소셜 로그인 동시 가입 race 대응: 서버가 동시 요청으로 인한 UNIQUE 충돌을 409 로 응답하면
/// 그 사이 identity 가 이미 만들어진 것이므로 동일 요청을 1회만 재시도한다(재시도 시 기존 사용자로 로그인).
/// Apple/Google 인증 UI 를 다시 띄우지 않도록 토큰 fetch 이후의 네트워크 호출에만 적용한다.
@MainActor
func retryingSocialConflict<T>(_ operation: () async throws -> T) async throws -> T {
    do {
        return try await operation()
    } catch let error as APIError {
        if case .serverError(let status, _, _) = error, status == 409 {
            return try await operation()
        }
        throw error
    }
}

/// 신규 소셜 가입자에게 약관 동의 UI 를 띄우기 위한 일시적 상태.
/// LoginView/SignUpView 가 이 값을 관찰해 sheet 를 띄운다.
struct PendingSocialConsent: Identifiable, Equatable {
    let id = UUID()
    let provider: SocialAuthProvider
    let idToken: String
    /// check 단계에서 쓴 원본 nonce. commit 의 토큰 재검증에서 동일하게 사용한다.
    let rawNonce: String?
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
            errorMessage = String(localized: "auth.error.login")
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
            let identity = try await tokenProvider.fetchIdToken()
            // Apple Sign In 직후 iOS가 LAN 라우팅을 복구할 때까지 대기.
            // 고정 딜레이 대신 NWPathMonitor로 실제 경로 상태를 확인한다.
            await NetworkPathWaiter.waitForLocalNetwork()
            let body = try apiClient.encode(
                SocialLoginRequest(idToken: identity.idToken, nonce: identity.rawNonce)
            )
            let check: SocialLoginCheckResponse = try await retryingSocialConflict {
                try await apiClient.request(.socialLoginCheck(provider: provider, body: body))
            }
            if !check.newUser, let tokens = check.tokens {
                authState.saveAndAuthenticate(tokenResponse: tokens)
            } else {
                // 신규 사용자 — 약관 동의 sheet 노출. 동의 완료는 completeSocialSignUp 에서 처리.
                pendingConsent = PendingSocialConsent(
                    provider: provider, idToken: identity.idToken, rawNonce: identity.rawNonce
                )
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
                nonce: pending.rawNonce,
                agreedToTerms: true,
                agreedToPrivacy: true
            ))
            let tokenResponse: TokenResponse = try await retryingSocialConflict {
                try await apiClient.request(.socialLoginCommit(provider: pending.provider, body: body))
            }
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
        case .apple:  return String(localized: "auth.error.apple.signin")
        case .google: return String(localized: "auth.error.google.signin")
        }
    }
}
