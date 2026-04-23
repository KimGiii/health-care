import Foundation

@MainActor
final class EditGoalViewModel: ObservableObject {
    @Published var targetValueText: String
    @Published var targetDate: Date
    @Published var weeklyRateText: String
    @Published var isLoading = false
    @Published var errorMessage: String?

    let goalId: Int
    let goalType: GoalType
    let targetUnit: String?

    init(progress: GoalProgressResponse) {
        goalId = progress.goalId
        goalType = progress.goalType
        targetUnit = progress.targetUnit

        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"

        if let tv = progress.targetValue {
            targetValueText = String(format: "%.1f", tv)
        } else {
            targetValueText = ""
        }

        if let td = progress.targetDate, let date = fmt.date(from: td) {
            targetDate = date
        } else {
            targetDate = Calendar.current.date(byAdding: .day, value: 84, to: Date()) ?? Date()
        }

        if let rate = progress.weeklyRateTarget {
            weeklyRateText = String(format: "%.2f", abs(rate))
        } else {
            weeklyRateText = ""
        }
    }

    var isValid: Bool {
        if goalType.requiresTargetValue {
            guard let v = Double(targetValueText), v > 0 else { return false }
        }
        return targetDate > Date()
    }

    func submit(apiClient: APIClient, onSuccess: @escaping () -> Void) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"

        let req = UpdateGoalRequest(
            targetDate: fmt.string(from: targetDate),
            targetValue: goalType.requiresTargetValue ? Double(targetValueText) : nil,
            weeklyRateTarget: goalType.normalizeWeeklyRate(Double(weeklyRateText))
        )

        do {
            let body = try JSONEncoder().encode(req)
            let _: GoalResponse = try await apiClient.request(.updateGoal(id: goalId, body: body))
            onSuccess()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "목표 수정 중 오류가 발생했습니다."
        }
    }
}
