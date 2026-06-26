import XCTest
@testable import HealthCare

final class GoalWidgetSnapshotFactoryTests: XCTestCase {
    func testMake_체중감량은체중차트를사용한다() {
        let snapshot = makeSnapshot(goalType: .WEIGHT_LOSS)

        XCTAssertEqual(snapshot.metricKind, .weight)
        XCTAssertEqual(snapshot.metricPoints.map(\.value), [72.4, 72.0])
    }

    func testMake_근육증가는골격근량차트를사용한다() {
        let snapshot = makeSnapshot(goalType: .MUSCLE_GAIN)

        XCTAssertEqual(snapshot.metricKind, .muscleMass)
        XCTAssertEqual(snapshot.metricPoints.map(\.value), [31.2, 31.7])
    }

    func testMake_체형개선은체지방률차트를사용한다() {
        let snapshot = makeSnapshot(goalType: .BODY_RECOMPOSITION)

        XCTAssertEqual(snapshot.metricKind, .bodyFat)
        XCTAssertEqual(snapshot.metricPoints.map(\.value), [23.5, 22.9])
    }

    func testMake_지구력향상은일별운동시간차트를사용한다() {
        let snapshot = GoalWidgetSnapshotFactory.make(
            goal: makeGoal(goalType: .ENDURANCE),
            recentMeasurements: makeMeasurements(),
            recentSessions: [
                makeSession(id: 1, date: "2026-06-16", duration: 30),
                makeSession(id: 2, date: "2026-06-16", duration: 20),
                makeSession(id: 3, date: "2026-06-17", duration: 45)
            ],
            previousSnapshot: nil
        )

        XCTAssertEqual(snapshot.metricKind, .enduranceMinutes)
        XCTAssertEqual(snapshot.metricPoints.map(\.value), [50.0, 45.0])
    }

    func testMake_전반적건강은체중차트를사용한다() {
        let snapshot = makeSnapshot(goalType: .GENERAL_HEALTH)

        XCTAssertEqual(snapshot.metricKind, .weight)
        XCTAssertEqual(snapshot.metricPoints.map(\.value), [72.4, 72.0])
    }

    private func makeSnapshot(goalType: GoalType) -> GoalWidgetSnapshot {
        GoalWidgetSnapshotFactory.make(
            goal: makeGoal(goalType: goalType),
            recentMeasurements: makeMeasurements(),
            recentSessions: [],
            previousSnapshot: nil
        )
    }

    private func makeGoal(goalType: GoalType) -> GoalSummary {
        GoalSummary(
            goalId: 10,
            goalType: goalType,
            targetValue: 70.0,
            targetUnit: goalType.apiUnit,
            targetDate: "2026-12-31",
            startDate: "2026-06-01",
            status: .ACTIVE,
            percentComplete: 40.0
        )
    }

    private func makeMeasurements() -> [MeasurementResponse] {
        [
            makeMeasurement(
                id: 1,
                measuredAt: "2026-06-16",
                weightKg: 72.4,
                bodyFatPct: 23.5,
                muscleMassKg: 31.2
            ),
            makeMeasurement(
                id: 2,
                measuredAt: "2026-06-17",
                weightKg: 72.0,
                bodyFatPct: 22.9,
                muscleMassKg: 31.7
            )
        ]
    }

    private func makeMeasurement(
        id: Int,
        measuredAt: String,
        weightKg: Double?,
        bodyFatPct: Double?,
        muscleMassKg: Double?
    ) -> MeasurementResponse {
        MeasurementResponse(
            id: id,
            measuredAt: measuredAt,
            weightKg: weightKg,
            bodyFatPct: bodyFatPct,
            muscleMassKg: muscleMassKg,
            bmi: nil,
            chestCm: nil,
            waistCm: nil,
            hipCm: nil,
            thighCm: nil,
            armCm: nil,
            notes: nil
        )
    }

    private func makeSession(id: Int, date: String, duration: Int) -> SessionSummary {
        SessionSummary(
            sessionId: id,
            sessionDate: date,
            durationMinutes: duration,
            totalVolumeKg: nil,
            caloriesBurned: nil,
            calorieEstimateMethod: "MET",
            notes: nil
        )
    }
}
