import Foundation

@MainActor
final class MyPageViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
