import Foundation

protocol GoalProgressLoading: Sendable {
    func loadGoalProgress(id: Int) async throws -> GoalProgressResponse
}

// APIClient satisfies GoalProgressLoading via HomeDashboardLoading conformance
extension APIClient: GoalProgressLoading {}

@MainActor
final class GoalProgressViewModel: ObservableObject {
    @Published var progress: GoalProgressResponse?
    @Published var isLoading = false
    @Published var errorMessage: String?

    let goalId: Int

    init(goalId: Int) {
        self.goalId = goalId
    }

    func load(apiClient: any GoalProgressLoading) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            progress = try await apiClient.loadGoalProgress(id: goalId)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "goal.error.loadProgress")
        }
    }
}
