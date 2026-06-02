import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published var email = ""
    @Published var displayName = ""
    @Published var password = ""
    @Published var passwordConfirm = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    /// 신규 소셜 가입자 약관 동의 sheet 트리거.
    @Published var pendingConsent: PendingSocialConsent?

    func register(apiClient: APIClient, authState: AuthState) async {
        guard password == passwordConfirm else {
            errorMessage = "비밀번호가 일치하지 않습니다."
            return
        }
        guard password.count >= 8 else {
            errorMessage = "비밀번호는 8자 이상이어야 합니다."
            return
        }
        guard !displayName.trimmingCharacters(in: .whitespaces).isEmpty else {
            errorMessage = "닉네임을 입력해 주세요."
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let body = try apiClient.encode(
                RegisterRequest(email: email, password: password, displayName: displayName)
            )
            let tokenResponse: TokenResponse = try await apiClient.request(.register(body: body))
            authState.saveAndAuthenticate(tokenResponse: tokenResponse)
        } catch let error as APIError {
            switch error {
            case .serverError(let code, _, _) where code == 409:
                errorMessage = "이미 사용 중인 이메일입니다."
            default:
                errorMessage = error.errorDescription
            }
        } catch {
            errorMessage = "회원가입 중 오류가 발생했습니다."
        }
    }

    /// 2-step 소셜 가입: 토큰 fetch → /check → 신규면 약관 동의 sheet 트리거, 기존이면 즉시 로그인.
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
            await NetworkPathWaiter.waitForLocalNetwork()
            let body = try apiClient.encode(SocialLoginRequest(idToken: idToken))
            let check: SocialLoginCheckResponse = try await apiClient.request(
                .socialLoginCheck(provider: provider, body: body)
            )
            if !check.newUser, let tokens = check.tokens {
                authState.saveAndAuthenticate(tokenResponse: tokens)
            } else {
                pendingConsent = PendingSocialConsent(provider: provider, idToken: idToken)
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch is CancellationError {
            errorMessage = nil
        } catch {
            switch provider {
            case .apple:  errorMessage = "Apple 가입에 실패했습니다."
            case .google: errorMessage = "Google 가입에 실패했습니다."
            }
        }
    }

    func completeSocialSignUp(apiClient: APIClient, authState: AuthState) async {
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
            switch pending.provider {
            case .apple:  errorMessage = "Apple 가입에 실패했습니다."
            case .google: errorMessage = "Google 가입에 실패했습니다."
            }
        }
    }

    func cancelSocialSignUp() {
        pendingConsent = nil
    }
}
