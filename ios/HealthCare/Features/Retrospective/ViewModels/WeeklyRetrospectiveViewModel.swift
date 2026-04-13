import Foundation

@MainActor
final class WeeklyRetrospectiveViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
