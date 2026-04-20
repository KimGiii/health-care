import Foundation

@MainActor
final class GoalSettingViewModel: ObservableObject {
    @Published var activeGoal: GoalSummary?
    @Published var pastGoals: [GoalSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddGoal = false

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response: GoalListResponse = try await apiClient.request(.getGoals)
            activeGoal = response.content.first { $0.status == .ACTIVE }
            pastGoals = response.content.filter { $0.status != .ACTIVE }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "목표를 불러오지 못했습니다."
        }
    }

    func abandonGoal(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteGoal(id: id))
            await load(apiClient: apiClient)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "목표 포기 처리 중 오류가 발생했습니다."
        }
    }

    func goalCreated(apiClient: APIClient) async {
        showAddGoal = false
        await load(apiClient: apiClient)
    }
}
