import Foundation

@MainActor
final class BodyMeasurementViewModel: ObservableObject {
    @Published var measurements: [MeasurementResponse] = []
    @Published var latestMeasurement: MeasurementResponse?
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddSheet = false

    func load(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let list: MeasurementListResponse = try await apiClient.request(.getBodyMeasurements(page: 0, size: 50))
            measurements = list.content
            latestMeasurement = list.content.first
        } catch {
            if case APIError.serverError(let code, _) = error, code == 404 {
                measurements = []
                latestMeasurement = nil
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }

    func delete(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteBodyMeasurement(id: id))
            measurements.removeAll { $0.id == id }
            if latestMeasurement?.id == id {
                latestMeasurement = measurements.first
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func measurementAdded(apiClient: APIClient) async {
        await load(apiClient: apiClient)
    }
}
