import XCTest
@testable import HealthCare

@MainActor
final class BodyMeasurementViewModelTests: XCTestCase {
    func testLoad_성공시체중목록을목표위젯차트에반영한다() async {
        let measurements = [
            makeMeasurement(id: 1, measuredAt: "2026-06-15", weightKg: 72.4),
            makeMeasurement(id: 2, measuredAt: "2026-06-16", weightKg: 72.1),
            makeMeasurement(id: 3, measuredAt: "2026-06-17", weightKg: 71.8)
        ]
        let apiClient = MockBodyMeasurementManager(measurements: measurements)
        let widgetPublisher = SpyGoalWidgetSnapshotPublisher()
        let vm = BodyMeasurementViewModel(widgetPublisher: widgetPublisher)

        await vm.load(apiClient: apiClient)

        XCTAssertEqual(vm.measurements.map(\.id), [1, 2, 3])
        XCTAssertEqual(widgetPublisher.publishedMeasurements.map { $0.map(\.id) }, [[1, 2, 3]])
    }

    func testDelete_성공시삭제된체중을제외하고목표위젯차트에반영한다() async {
        let measurements = [
            makeMeasurement(id: 1, measuredAt: "2026-06-15", weightKg: 72.4),
            makeMeasurement(id: 2, measuredAt: "2026-06-16", weightKg: 72.1),
            makeMeasurement(id: 3, measuredAt: "2026-06-17", weightKg: 71.8)
        ]
        let apiClient = MockBodyMeasurementManager(measurements: measurements)
        let widgetPublisher = SpyGoalWidgetSnapshotPublisher()
        let vm = BodyMeasurementViewModel(widgetPublisher: widgetPublisher)
        await vm.load(apiClient: apiClient)

        await vm.delete(id: 2, apiClient: apiClient)

        XCTAssertEqual(vm.measurements.map(\.id), [1, 3])
        XCTAssertEqual(widgetPublisher.publishedMeasurements.map { $0.map(\.id) }, [[1, 2, 3], [1, 3]])
    }

    private func makeMeasurement(id: Int, measuredAt: String, weightKg: Double?) -> MeasurementResponse {
        MeasurementResponse(
            id: id,
            measuredAt: measuredAt,
            weightKg: weightKg,
            bodyFatPct: nil,
            muscleMassKg: nil,
            bmi: nil,
            chestCm: nil,
            waistCm: nil,
            hipCm: nil,
            thighCm: nil,
            armCm: nil,
            notes: nil
        )
    }
}

private actor MockBodyMeasurementManager: BodyMeasurementManaging {
    private let measurements: [MeasurementResponse]
    private let error: Error?
    private var deletedIDs: [Int] = []

    init(measurements: [MeasurementResponse] = [], error: Error? = nil) {
        self.measurements = measurements
        self.error = error
    }

    func loadMeasurementList(page: Int, size: Int) async throws -> MeasurementListResponse {
        if let error { throw error }
        return MeasurementListResponse(
            content: measurements,
            pageNumber: page,
            pageSize: size,
            totalElements: measurements.count,
            first: true,
            last: true
        )
    }

    func loadLatestMeasurement() async throws -> MeasurementResponse {
        if let error { throw error }
        return measurements.last!
    }

    func loadBodyMeasurementGoals() async throws -> GoalListResponse {
        if let error { throw error }
        return GoalListResponse(
            content: [],
            pageNumber: 0,
            pageSize: 20,
            totalElements: 0,
            first: true,
            last: true
        )
    }

    func loadMeasurementRange(from: String, to: String) async throws -> [MeasurementResponse] {
        if let error { throw error }
        return measurements
    }

    func loadBaselineMeasurement(onOrBefore date: String) async throws -> MeasurementResponse {
        if let error { throw error }
        return measurements.first!
    }

    func deleteMeasurement(id: Int) async throws {
        if let error { throw error }
        deletedIDs.append(id)
    }
}

@MainActor
private final class SpyGoalWidgetSnapshotPublisher: GoalWidgetSnapshotPublishing {
    private(set) var publishedMeasurements: [[MeasurementResponse]] = []

    func publish(goal: GoalSummary?) {}

    func publish(
        goal: GoalSummary?,
        recentMeasurements: [MeasurementResponse],
        recentSessions: [SessionSummary]
    ) {}

    func publish(recentMeasurements: [MeasurementResponse]) {
        publishedMeasurements.append(recentMeasurements)
    }
}
