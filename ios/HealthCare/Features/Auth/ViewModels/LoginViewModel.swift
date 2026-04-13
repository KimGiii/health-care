import Foundation

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
}
