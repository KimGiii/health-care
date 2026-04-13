import Foundation

@MainActor
final class ExerciseRecordViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
