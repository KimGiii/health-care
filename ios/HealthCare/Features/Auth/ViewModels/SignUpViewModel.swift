import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published var email = ""
    @Published var displayName = ""
    @Published var password = ""
    @Published var passwordConfirm = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

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
            errorMessage = "닉네임을 입력해주세요."
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
            case .serverError(let code, _) where code == 409:
                errorMessage = "이미 사용 중인 이메일입니다."
            default:
                errorMessage = error.errorDescription
            }
        } catch {
            errorMessage = "회원가입 중 오류가 발생했습니다."
        }
    }
}
