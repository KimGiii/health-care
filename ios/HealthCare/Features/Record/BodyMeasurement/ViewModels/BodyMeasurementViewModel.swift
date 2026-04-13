import Foundation

@MainActor
final class BodyMeasurementViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load() async {}
}
