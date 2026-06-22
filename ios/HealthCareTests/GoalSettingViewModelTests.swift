import XCTest
@testable import HealthCare

@MainActor
final class GoalSettingViewModelTests: XCTestCase {
    func testLoad_성공시활성목표를위젯에반영한다() async {
        let activeGoal = makeGoal(id: 10, status: .ACTIVE)
        let apiClient = MockGoalSettingManager(
            goals: [
                makeGoal(id: 9, status: .ABANDONED),
                activeGoal
            ]
        )
        let widgetPublisher = SpyGoalWidgetSnapshotPublisher()
        let vm = GoalSettingViewModel(widgetPublisher: widgetPublisher)

        await vm.load(apiClient: apiClient)

        XCTAssertEqual(vm.activeGoal?.goalId, activeGoal.goalId)
        XCTAssertEqual(widgetPublisher.publishedGoals.count, 1)
        if let publishedGoal = widgetPublisher.publishedGoals.first {
            XCTAssertEqual(publishedGoal?.goalId, activeGoal.goalId)
        }
    }

    func testAbandonGoal_삭제성공후목록재조회가실패해도위젯목표를비운다() async {
        let apiClient = MockGoalSettingManager(loadError: APIError.unknown)
        let widgetPublisher = SpyGoalWidgetSnapshotPublisher()
        let vm = GoalSettingViewModel(widgetPublisher: widgetPublisher)

        await vm.abandonGoal(id: 10, apiClient: apiClient)

        let deletedIDs = await apiClient.deletedGoalIDs()
        XCTAssertEqual(deletedIDs, [10])
        XCTAssertEqual(widgetPublisher.publishedGoals.count, 1)
        if let publishedGoal = widgetPublisher.publishedGoals.first {
            XCTAssertNil(publishedGoal)
        }
    }

    private func makeGoal(id: Int, status: GoalStatus) -> GoalSummary {
        GoalSummary(
            goalId: id,
            goalType: .WEIGHT_LOSS,
            targetValue: 70.0,
            targetUnit: "kg",
            targetDate: "2026-12-31",
            startDate: "2026-01-01",
            status: status,
            percentComplete: 45.0
        )
    }
}

private actor MockGoalSettingManager: GoalSettingManaging {
    private let goals: [GoalSummary]
    private let measurements: [MeasurementResponse]
    private let sessions: [SessionSummary]
    private let loadError: Error?
    private let abandonError: Error?
    private var deletedIDs: [Int] = []

    init(
        goals: [GoalSummary] = [],
        measurements: [MeasurementResponse] = [],
        sessions: [SessionSummary] = [],
        loadError: Error? = nil,
        abandonError: Error? = nil
    ) {
        self.goals = goals
        self.measurements = measurements
        self.sessions = sessions
        self.loadError = loadError
        self.abandonError = abandonError
    }

    func loadGoals() async throws -> GoalListResponse {
        if let loadError { throw loadError }
        return GoalListResponse(
            content: goals,
            pageNumber: 0,
            pageSize: 20,
            totalElements: goals.count,
            first: true,
            last: true
        )
    }

    func loadGoalWidgetMeasurements(from: String, to: String) async throws -> [MeasurementResponse] {
        if let loadError { throw loadError }
        return measurements
    }

    func loadGoalWidgetExerciseSessions() async throws -> SessionListResponse {
        if let loadError { throw loadError }
        return SessionListResponse(
            content: sessions,
            pageNumber: 0,
            pageSize: 30,
            totalElements: sessions.count,
            totalPages: 1,
            first: true,
            last: true
        )
    }

    func abandonGoal(id: Int) async throws {
        if let abandonError { throw abandonError }
        deletedIDs.append(id)
    }

    func deletedGoalIDs() -> [Int] {
        deletedIDs
    }
}

@MainActor
private final class SpyGoalWidgetSnapshotPublisher: GoalWidgetSnapshotPublishing {
    private(set) var publishedGoals: [GoalSummary?] = []

    func publish(goal: GoalSummary?) {
        publishedGoals.append(goal)
    }

    func publish(
        goal: GoalSummary?,
        recentMeasurements: [MeasurementResponse],
        recentSessions: [SessionSummary]
    ) {
        publishedGoals.append(goal)
    }

    func publish(recentMeasurements: [MeasurementResponse]) {}
}
