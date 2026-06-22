import Testing
import Foundation
@testable import HealthCare

// MARK: - Cycle 1: 레거시 JSON 디코딩 동작 검증 (리팩토링 전 안전망)

/// init(from decoder:) 안에 혼재된 마이그레이션 로직을
/// migrate(from:) 로 분리하기 전에, 현재 동작을 테스트로 고정한다.
struct GoalWidgetSnapshotMigrationTests {

    // MARK: end-to-end decode (legacy format)

    @Test("recentWeights 포맷 JSON이 metricKind .weight 와 값 일치 metricPoints 로 디코딩된다")
    func legacyRecentWeights_decodesAsWeightMetricPoints() throws {
        let date = Date(timeIntervalSinceReferenceDate: 0)
        let legacyData = try makeLegacyJSON(weights: [
            .init(date: date, weightKg: 72.4),
            .init(date: date.addingTimeInterval(86400), weightKg: 71.8)
        ])

        let snapshot = try JSONDecoder().decode(GoalWidgetSnapshot.self, from: legacyData)

        #expect(snapshot.metricKind == .weight)
        #expect(snapshot.metricPoints.map(\.value) == [72.4, 71.8])
    }

    @Test("recentWeights 가 빈 배열이면 metricPoints 도 빈 배열이 된다")
    func legacyRecentWeights_empty_producesEmptyMetricPoints() throws {
        let legacyData = try makeLegacyJSON(weights: [])

        let snapshot = try JSONDecoder().decode(GoalWidgetSnapshot.self, from: legacyData)

        #expect(snapshot.metricKind == .weight)
        #expect(snapshot.metricPoints.isEmpty)
    }

    @Test("recentWeights 키 자체가 없으면 빈 metricPoints 로 폴백된다")
    func legacyRecentWeights_keyAbsent_fallsBackToEmpty() throws {
        let json = #"{"updatedAt":0}"#.data(using: .utf8)!

        let snapshot = try JSONDecoder().decode(GoalWidgetSnapshot.self, from: json)

        #expect(snapshot.metricKind == .weight)
        #expect(snapshot.metricPoints.isEmpty)
    }

    @Test("신규 metricKind·metricPoints 포맷은 마이그레이션 경로를 타지 않는다")
    func modernFormat_skipsMigrationPath() throws {
        let original = GoalWidgetSnapshot(
            goal: nil,
            metricKind: .muscleMass,
            metricPoints: [.init(date: Date(timeIntervalSinceReferenceDate: 0), value: 31.5)]
        )
        let modernData = try JSONEncoder().encode(original)

        let decoded = try JSONDecoder().decode(GoalWidgetSnapshot.self, from: modernData)

        #expect(decoded.metricKind == .muscleMass)
        #expect(decoded.metricPoints.map(\.value) == [31.5])
    }

    // MARK: - Cycle 2: migrate(from:) 직접 호출 (함수 추출 후 RED → GREEN)

    @Test("migrate(from:) 는 weightKg 를 value 로 변환하고 metricKind .weight 를 반환한다")
    func migrate_convertsWeightKgToValue() {
        let date = Date(timeIntervalSinceReferenceDate: 0)
        let weights: [GoalWidgetSnapshot.WeightPoint] = [
            .init(date: date, weightKg: 72.4),
            .init(date: date.addingTimeInterval(86400), weightKg: 71.8)
        ]

        let (kind, points) = GoalWidgetSnapshot.migrate(from: weights)

        #expect(kind == .weight)
        #expect(points.count == 2)
        #expect(points[0].value == 72.4)
        #expect(points[1].value == 71.8)
        #expect(points[0].date == date)
    }

    @Test("migrate(from:) 빈 배열은 빈 metricPoints 를 반환한다")
    func migrate_empty_returnsEmpty() {
        let (kind, points) = GoalWidgetSnapshot.migrate(from: [])

        #expect(kind == .weight)
        #expect(points.isEmpty)
    }

    @Test("migrate(from:) 각 포인트의 date 가 보존된다")
    func migrate_preservesDates() {
        let d1 = Date(timeIntervalSinceReferenceDate: 1_000_000)
        let d2 = Date(timeIntervalSinceReferenceDate: 2_000_000)
        let weights: [GoalWidgetSnapshot.WeightPoint] = [
            .init(date: d1, weightKg: 70.0),
            .init(date: d2, weightKg: 69.5)
        ]

        let (_, points) = GoalWidgetSnapshot.migrate(from: weights)

        #expect(points[0].date == d1)
        #expect(points[1].date == d2)
    }

    // MARK: - helpers

    private func makeLegacyJSON(weights: [GoalWidgetSnapshot.WeightPoint]) throws -> Data {
        let encoder = JSONEncoder()
        let weightsJSON = try encoder.encode(weights)
        let weightsString = String(data: weightsJSON, encoding: .utf8)!
        let json = #"{"updatedAt":0,"recentWeights":\#(weightsString)}"#
        return json.data(using: .utf8)!
    }
}
