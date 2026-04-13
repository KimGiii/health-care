import Foundation

@MainActor
final class GoalSettingViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
