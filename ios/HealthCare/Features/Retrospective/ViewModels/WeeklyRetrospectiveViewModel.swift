import Foundation

@MainActor
final class WeeklyRetrospectiveViewModel: ObservableObject {
    @Published var summary: WeeklySummaryResponse?
    @Published var weekOffset: Int = 0
    @Published var isLoading = false
    @Published var errorMessage: String?

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            summary = try await apiClient.request(.getWeeklySummary(weekOffset: weekOffset))
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "주간 요약을 불러오는 중 오류가 발생했습니다."
        }
    }

    func goToPreviousWeek(apiClient: APIClient) {
        weekOffset += 1
        Task { await load(apiClient: apiClient) }
    }

    func goToNextWeek(apiClient: APIClient) {
        guard weekOffset > 0 else { return }
        weekOffset -= 1
        Task { await load(apiClient: apiClient) }
    }
}
