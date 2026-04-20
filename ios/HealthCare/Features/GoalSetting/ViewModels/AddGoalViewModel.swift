import Foundation

@MainActor
final class AddGoalViewModel: ObservableObject {
    @Published var selectedType: GoalType = .WEIGHT_LOSS
    @Published var targetValueText: String = ""
    @Published var targetDate: Date = Calendar.current.date(byAdding: .day, value: 84, to: Date()) ?? Date()
    @Published var startValueText: String = ""
    @Published var weeklyRateText: String = ""
    @Published var isLoading = false
    @Published var errorMessage: String?

    var isValid: Bool {
        if selectedType.requiresTargetValue {
            guard let v = Double(targetValueText), v > 0 else { return false }
        }
        return targetDate > Date()
    }

    func submit(apiClient: APIClient, onSuccess: @escaping (GoalResponse) -> Void) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        let fmt = DateFormatter()
        fmt.dateFormat = "yyyy-MM-dd"

        let req = CreateGoalRequest(
            goalType: selectedType.rawValue,
            targetValue: selectedType.requiresTargetValue ? Double(targetValueText) : nil,
            targetUnit: selectedType.requiresTargetValue ? selectedType.defaultUnit : nil,
            targetDate: fmt.string(from: targetDate),
            startValue: Double(startValueText).flatMap { $0 > 0 ? $0 : nil },
            weeklyRateTarget: Double(weeklyRateText).flatMap { $0 > 0 ? $0 : nil }
        )

        do {
            let body = try JSONEncoder().encode(req)
            let response: GoalResponse = try await apiClient.request(.createGoal(body: body))
            onSuccess(response)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "목표 생성 중 오류가 발생했습니다."
        }
    }
}
