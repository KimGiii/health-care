import Foundation

@MainActor
final class GoalProgressViewModel: ObservableObject {
    @Published var progress: GoalProgressResponse?
    @Published var isLoading = false
    @Published var errorMessage: String?

    let goalId: Int

    init(goalId: Int) {
        self.goalId = goalId
    }

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response: GoalProgressResponse = try await apiClient.request(.getGoalProgress(id: goalId))
            progress = response
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "진행률을 불러오지 못했습니다."
        }
    }
}
