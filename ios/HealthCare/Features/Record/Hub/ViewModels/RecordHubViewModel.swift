import Foundation

@MainActor
final class RecordHubViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
