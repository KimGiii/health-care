import Foundation

protocol GoalSettingManaging: Sendable {
    func loadGoals() async throws -> GoalListResponse
    func loadGoalWidgetMeasurements(from: String, to: String) async throws -> [MeasurementResponse]
    func loadGoalWidgetExerciseSessions() async throws -> SessionListResponse
    func abandonGoal(id: Int) async throws
}

extension APIClient: GoalSettingManaging {
    func loadGoalWidgetMeasurements(from: String, to: String) async throws -> [MeasurementResponse] {
        try await request(.getBodyMeasurementsRange(from: from, to: to))
    }

    func loadGoalWidgetExerciseSessions() async throws -> SessionListResponse {
        try await request(.getExerciseSessions(from: nil, to: nil, page: 0, size: 30))
    }

    func abandonGoal(id: Int) async throws {
        try await requestVoid(.deleteGoal(id: id))
    }
}

@MainActor
final class GoalSettingViewModel: ObservableObject {
    @Published var activeGoal: GoalSummary?
    @Published var pastGoals: [GoalSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddGoal = false

    private let widgetPublisher: any GoalWidgetSnapshotPublishing

    init(widgetPublisher: any GoalWidgetSnapshotPublishing = AppGoalWidgetSnapshotPublisher()) {
        self.widgetPublisher = widgetPublisher
    }

    func load(apiClient: any GoalSettingManaging) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let response = try await apiClient.loadGoals()
            activeGoal = response.content.first { $0.status == .ACTIVE }
            pastGoals = response.content.filter { $0.status != .ACTIVE }
            await publishGoalWidgetSnapshot(apiClient: apiClient)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "goal.error.loadList")
        }
    }

    func abandonGoal(id: Int, apiClient: any GoalSettingManaging) async {
        do {
            try await apiClient.abandonGoal(id: id)
            widgetPublisher.publish(goal: nil)
            await load(apiClient: apiClient)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "goal.error.abandon")
        }
    }

    func goalCreated(apiClient: any GoalSettingManaging) async {
        showAddGoal = false
        await load(apiClient: apiClient)
    }

    private func publishGoalWidgetSnapshot(apiClient: any GoalSettingManaging) async {
        guard let activeGoal else {
            widgetPublisher.publish(goal: nil)
            return
        }

        switch activeGoal.goalType {
        case .ENDURANCE:
            let sessions = (try? await apiClient.loadGoalWidgetExerciseSessions())?.content ?? []
            widgetPublisher.publish(
                goal: activeGoal,
                recentMeasurements: [],
                recentSessions: sessions
            )
        case .WEIGHT_LOSS, .MUSCLE_GAIN, .BODY_RECOMPOSITION, .GENERAL_HEALTH:
            let measurements = (try? await apiClient.loadGoalWidgetMeasurements(
                from: Self.goalWidgetRangeStart,
                to: Self.today
            )) ?? []
            widgetPublisher.publish(
                goal: activeGoal,
                recentMeasurements: measurements,
                recentSessions: []
            )
        }
    }

    private static var today: String {
        dateFormatter.string(from: Date())
    }

    private static var goalWidgetRangeStart: String {
        let date = Calendar.current.date(byAdding: .day, value: -29, to: Date()) ?? Date()
        return dateFormatter.string(from: date)
    }

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()
}
