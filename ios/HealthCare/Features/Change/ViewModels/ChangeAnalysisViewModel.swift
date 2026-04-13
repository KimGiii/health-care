import Foundation

@MainActor
final class ChangeAnalysisViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
