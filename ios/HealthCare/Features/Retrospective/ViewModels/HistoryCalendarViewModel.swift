import Foundation

@MainActor
final class HistoryCalendarViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
