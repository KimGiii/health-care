import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func loadDashboard() async {
        isLoading = true
        do {
            // TODO: fetch daily summary from APIClient
        }
        isLoading = false
    }
}
