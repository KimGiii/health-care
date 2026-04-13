import Foundation

@MainActor
final class DietRecordViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
