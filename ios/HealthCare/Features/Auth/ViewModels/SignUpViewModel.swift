import Foundation

@MainActor
final class SignUpViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var passwordConfirm = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    func register(authState: AuthState) async {
        guard password == passwordConfirm else {
            errorMessage = "비밀번호가 일치하지 않습니다."
            return
        }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        // TODO: call APIClient.request(.register(...)) and save tokens
        authState.setAuthenticated()
    }
}
