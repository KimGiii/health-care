import XCTest
@testable import HealthCare

@MainActor
final class GoalProgressViewModelTests: XCTestCase {

    // MARK: - 성공 케이스

    func testLoad_성공시progress가채워진다() async {
        let loader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: 60.0))
        let vm = GoalProgressViewModel(goalId: 1)

        await vm.load(apiClient: loader)

        XCTAssertNotNil(vm.progress)
        XCTAssertEqual(vm.progress?.percentComplete, 60.0)
        XCTAssertNil(vm.errorMessage)
        XCTAssertFalse(vm.isLoading)
    }

    func testLoad_로딩완료후isLoading이false이다() async {
        let loader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: 0))
        let vm = GoalProgressViewModel(goalId: 1)

        await vm.load(apiClient: loader)

        XCTAssertFalse(vm.isLoading)
    }

    // MARK: - 에러 케이스

    func testLoad_APIError발생시errorMessage가설정된다() async {
        let loader = MockGoalProgressLoader(error: APIError.unauthorized)
        let vm = GoalProgressViewModel(goalId: 1)

        await vm.load(apiClient: loader)

        XCTAssertNil(vm.progress)
        XCTAssertNotNil(vm.errorMessage)
        XCTAssertFalse(vm.isLoading)
    }

    func testLoad_일반오류발생시기본메시지가설정된다() async {
        let loader = MockGoalProgressLoader(error: URLError(.notConnectedToInternet))
        let vm = GoalProgressViewModel(goalId: 1)

        await vm.load(apiClient: loader)

        XCTAssertEqual(vm.errorMessage, "진행률을 불러오지 못했습니다.")
    }

    // MARK: - 재시도

    func testLoad_재시도시이전에러가초기화된다() async {
        let failLoader = MockGoalProgressLoader(error: APIError.unknown)
        let vm = GoalProgressViewModel(goalId: 1)
        await vm.load(apiClient: failLoader)
        XCTAssertNotNil(vm.errorMessage)

        let successLoader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: 80.0))
        await vm.load(apiClient: successLoader)

        XCTAssertNil(vm.errorMessage)
        XCTAssertEqual(vm.progress?.percentComplete, 80.0)
    }

    // MARK: - progressRatio 계산

    func testProgressRatio_percentComplete가50이면0점5이다() async throws {
        let loader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: 50.0))
        let vm = GoalProgressViewModel(goalId: 1)
        await vm.load(apiClient: loader)

        let ratio = try XCTUnwrap(vm.progress).progressRatio
        XCTAssertEqual(ratio, 0.5, accuracy: 0.001)
    }

    func testProgressRatio_초과값은1로클램프된다() async throws {
        let loader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: 150.0))
        let vm = GoalProgressViewModel(goalId: 1)
        await vm.load(apiClient: loader)

        XCTAssertEqual(try XCTUnwrap(vm.progress).progressRatio, 1.0)
    }

    func testProgressRatio_nil이면0이다() async throws {
        let loader = MockGoalProgressLoader(progress: makeProgress(id: 1, percent: nil))
        let vm = GoalProgressViewModel(goalId: 1)
        await vm.load(apiClient: loader)

        XCTAssertEqual(try XCTUnwrap(vm.progress).progressRatio, 0.0)
    }

    // MARK: - Helpers

    private func makeProgress(id: Int, percent: Double?) -> GoalProgressResponse {
        GoalProgressResponse(
            goalId: id,
            goalType: .WEIGHT_LOSS,
            targetValue: 70.0,
            targetUnit: "kg",
            targetDate: "2026-12-31",
            startDate: "2026-01-01",
            startValue: 80.0,
            currentValue: 75.0,
            percentComplete: percent,
            daysRemaining: 200,
            projectedCompletionDate: nil,
            weeklyRateTarget: nil,
            isOnTrack: true,
            trackingStatus: "ON_TRACK",
            trackingColor: "green",
            checkpoints: nil
        )
    }
}

// MARK: - Mock

private actor MockGoalProgressLoader: GoalProgressLoading {
    private let progress: GoalProgressResponse?
    private let error: Error?

    init(progress: GoalProgressResponse? = nil, error: Error? = nil) {
        self.progress = progress
        self.error = error
    }

    func loadGoalProgress(id: Int) async throws -> GoalProgressResponse {
        if let error { throw error }
        return progress!
    }
}
