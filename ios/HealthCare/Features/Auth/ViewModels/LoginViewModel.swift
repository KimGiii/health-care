import Foundation

@MainActor
final class LoginViewModel: ObservableObject {
    @Published var email = ""
    @Published var password = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    func login(authState: AuthState) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        // TODO: call APIClient.request(.login(...)) and save tokens
        authState.setAuthenticated()
    }
}
