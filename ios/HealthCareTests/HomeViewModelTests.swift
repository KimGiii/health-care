import XCTest
@testable import Gainsy

@MainActor
final class HomeViewModelTests: XCTestCase {

    // MARK: - calorieProgress

    func testCalorieProgress_섭취칼로리없으면0이다() {
        let vm = HomeViewModel()
        XCTAssertEqual(vm.calorieProgress, 0.0)
    }

    func testCalorieProgress_섭취칼로리가목표의절반이면0점5이다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 1_000)]
        XCTAssertEqual(vm.calorieProgress, 0.5, accuracy: 0.001)
    }

    func testCalorieProgress_초과섭취해도1을넘지않는다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 5_000)]
        XCTAssertEqual(vm.calorieProgress, 1.0)
    }

    func testCalorieProgress_여러로그의칼로리를합산한다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 500), makeDietLog(calories: 500)]
        XCTAssertEqual(vm.calorieProgress, 0.5, accuracy: 0.001)
    }

    // MARK: - activityProgress

    func testActivityProgress_오늘운동없으면0이다() {
        let vm = HomeViewModel()
        vm.recentSessions = []
        XCTAssertEqual(vm.activityProgress, 0.0)
    }

    func testActivityProgress_오늘250kcal소모시0점5이다() {
        let vm = HomeViewModel()
        vm.recentSessions = [makeSession(date: vm.today, calories: 250)]
        XCTAssertEqual(vm.activityProgress, 0.5, accuracy: 0.001)
    }

    func testActivityProgress_과거세션은제외된다() {
        let vm = HomeViewModel()
        vm.recentSessions = [makeSession(date: "2000-01-01", calories: 999)]
        XCTAssertEqual(vm.activityProgress, 0.0)
    }

    func testActivityProgress_500kcal이상이면1로클램프된다() {
        let vm = HomeViewModel()
        vm.recentSessions = [makeSession(date: vm.today, calories: 1_000)]
        XCTAssertEqual(vm.activityProgress, 1.0)
    }

    // MARK: - todayBurnedCalories / todayDurationMinutes

    func testTodayBurnedCalories_오늘여러세션을합산한다() {
        let vm = HomeViewModel()
        vm.recentSessions = [
            makeSession(date: vm.today, calories: 200, duration: 20),
            makeSession(date: vm.today, calories: 150, duration: 30)
        ]
        XCTAssertEqual(vm.todayBurnedCalories, 350, accuracy: 0.001)
    }

    func testTodayBurnedCalories_과거세션은제외된다() {
        let vm = HomeViewModel()
        vm.recentSessions = [makeSession(date: "2000-01-01", calories: 500)]
        XCTAssertEqual(vm.todayBurnedCalories, 0.0)
    }

    func testTodayDurationMinutes_오늘여러세션시간을합산한다() {
        let vm = HomeViewModel()
        vm.recentSessions = [
            makeSession(date: vm.today, calories: 0, duration: 25),
            makeSession(date: vm.today, calories: 0, duration: 15)
        ]
        XCTAssertEqual(vm.todayDurationMinutes, 40)
    }

    // MARK: - 매크로 (todayProteinG / todayCarbsG / todayFatG)

    func testMacros_로그없으면모두0이다() {
        let vm = HomeViewModel()
        XCTAssertEqual(vm.todayProteinG, 0.0)
        XCTAssertEqual(vm.todayCarbsG,  0.0)
        XCTAssertEqual(vm.todayFatG,    0.0)
    }

    func testMacros_여러로그의매크로를합산한다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [
            makeDietLog(calories: 400, protein: 30, carbs: 50, fat: 10),
            makeDietLog(calories: 300, protein: 20, carbs: 40, fat:  8)
        ]
        XCTAssertEqual(vm.todayProteinG, 50, accuracy: 0.001)
        XCTAssertEqual(vm.todayCarbsG,   90, accuracy: 0.001)
        XCTAssertEqual(vm.todayFatG,     18, accuracy: 0.001)
    }

    // MARK: - 매크로 진행률

    func testProteinProgress_목표량달성시1이다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 0, protein: 150)]  // 목표 150g
        XCTAssertEqual(vm.proteinProgress, 1.0)
    }

    func testProteinProgress_초과달성해도1을넘지않는다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 0, protein: 300)]
        XCTAssertEqual(vm.proteinProgress, 1.0)
    }

    func testCarbsProgress_절반섭취시0점5이다() {
        let vm = HomeViewModel()
        vm.todayDietLogs = [makeDietLog(calories: 0, carbs: 100)]  // 목표 200g
        XCTAssertEqual(vm.carbsProgress, 0.5, accuracy: 0.001)
    }

    func testFatProgress_목표없으면0이다() {
        let vm = HomeViewModel()
        XCTAssertEqual(vm.fatProgress, 0.0)
    }

    // MARK: - streakDays

    func testStreakDays_기록없으면0이다() {
        let vm = HomeViewModel()
        // weekDietLogs, recentSessions 모두 비어있음
        XCTAssertEqual(vm.streakDays, 0)
    }

    func testStreakDays_오늘만기록있으면1이다() {
        let vm = HomeViewModel()
        vm.weekDietLogs = [makeDietLog(date: vm.today, calories: 500)]
        XCTAssertEqual(vm.streakDays, 1)
    }

    func testStreakDays_오늘기록없으면어제기록있어도0이다() {
        let vm = HomeViewModel()
        let yesterday = dayOffset(-1, from: vm.today)
        vm.weekDietLogs = [makeDietLog(date: yesterday, calories: 500)]
        XCTAssertEqual(vm.streakDays, 0)
    }

    func testStreakDays_오늘포함3일연속이면3이다() {
        let vm = HomeViewModel()
        let d0 = vm.today
        let d1 = dayOffset(-1, from: d0)
        let d2 = dayOffset(-2, from: d0)
        vm.weekDietLogs = [
            makeDietLog(date: d0, calories: 400),
            makeDietLog(date: d1, calories: 400),
            makeDietLog(date: d2, calories: 400)
        ]
        XCTAssertEqual(vm.streakDays, 3)
    }

    func testStreakDays_중간에빠진날이있으면끊긴다() {
        let vm = HomeViewModel()
        let d0 = vm.today
        let d2 = dayOffset(-2, from: d0)  // 어제(d1)은 비어있음
        vm.weekDietLogs = [
            makeDietLog(date: d0, calories: 400),
            makeDietLog(date: d2, calories: 400)
        ]
        XCTAssertEqual(vm.streakDays, 1)  // 오늘만 카운트
    }

    func testStreakDays_운동기록으로도연속일수를센다() {
        let vm = HomeViewModel()
        let d0 = vm.today
        let d1 = dayOffset(-1, from: d0)
        vm.recentSessions = [makeSession(date: d0, calories: 200)]
        vm.weekDietLogs   = [makeDietLog(date: d1, calories: 400)]
        XCTAssertEqual(vm.streakDays, 2)
    }

    func testStreakDays_최대7일로제한된다() {
        let vm = HomeViewModel()
        // 7일치 식단 기록
        let logs = (0...6).map { offset -> DietLogSummary in
            let date = dayOffset(-offset, from: vm.today)
            return makeDietLog(date: date, calories: 300)
        }
        vm.weekDietLogs = logs
        XCTAssertEqual(vm.streakDays, 7)
    }

    // MARK: - weeklyActivity

    func testWeeklyActivity_항상7개의포인트를반환한다() {
        let vm = HomeViewModel()
        XCTAssertEqual(vm.weeklyActivity.count, 7)
    }

    func testWeeklyActivity_마지막항목이오늘이다() {
        let vm = HomeViewModel()
        XCTAssertEqual(vm.weeklyActivity.last?.date, vm.today)
    }

    func testWeeklyActivity_첫항목이6일전이다() {
        let vm = HomeViewModel()
        let expected = dayOffset(-6, from: vm.today)
        XCTAssertEqual(vm.weeklyActivity.first?.date, expected)
    }

    func testWeeklyActivity_해당날칼로리가올바르게매핑된다() {
        let vm = HomeViewModel()
        vm.weekDietLogs = [makeDietLog(date: vm.today, calories: 1_800)]
        let todayPoint = try! XCTUnwrap(vm.weeklyActivity.last)
        XCTAssertEqual(todayPoint.caloriesIn, 1_800, accuracy: 0.001)
    }

    func testWeeklyActivity_해당날운동데이터가올바르게매핑된다() {
        let vm = HomeViewModel()
        let yesterday = dayOffset(-1, from: vm.today)
        vm.recentSessions = [makeSession(date: yesterday, calories: 320, duration: 45)]
        let yesterdayPoint = try! XCTUnwrap(vm.weeklyActivity.dropLast().last)
        XCTAssertEqual(yesterdayPoint.caloriesBurned, 320, accuracy: 0.001)
        XCTAssertEqual(yesterdayPoint.durationMinutes, 45)
    }

    func testWeeklyActivity_데이터없는날은모두0이다() {
        let vm = HomeViewModel()
        let point = vm.weeklyActivity.first!
        XCTAssertEqual(point.caloriesIn,    0.0)
        XCTAssertEqual(point.caloriesBurned, 0.0)
        XCTAssertEqual(point.durationMinutes, 0)
    }

    // MARK: - loadDashboard

    func testLoadDashboard_성공시데이터가채워진다() async throws {
        let loader = MockHomeDashboardLoader(
            dietLogs: [makeDietLog(date: HomeViewModel().today, calories: 300)],
            sessions: [makeSession(date: "2026-01-01", calories: 100)],
            goals: []
        )
        let vm = HomeViewModel()

        await vm.loadDashboard(apiClient: loader)

        XCTAssertFalse(vm.isLoading)
        XCTAssertEqual(vm.todayDietLogs.count, 1)   // 오늘 날짜 필터링
        XCTAssertEqual(vm.weekDietLogs.count, 1)    // 전체 주간 로그
        XCTAssertEqual(vm.recentSessions.count, 1)
        XCTAssertNil(vm.activeGoal)
    }

    func testLoadDashboard_과거날짜로그는todayDietLogs에포함안된다() async throws {
        let loader = MockHomeDashboardLoader(
            dietLogs: [makeDietLog(date: "2000-01-01", calories: 500)],
            sessions: [],
            goals: []
        )
        let vm = HomeViewModel()
        await vm.loadDashboard(apiClient: loader)

        XCTAssertEqual(vm.weekDietLogs.count, 1)    // weekDietLogs엔 있음
        XCTAssertEqual(vm.todayDietLogs.count, 0)   // todayDietLogs엔 없음
    }

    func testLoadDashboard_활성목표가있으면진행률을enriched한다() async throws {
        let goal = makeGoalSummary(id: 7, status: .ACTIVE, percentComplete: nil)
        let loader = MockHomeDashboardLoader(
            dietLogs: [],
            sessions: [],
            goals: [goal],
            goalProgress: makeGoalProgress(id: 7, percent: 42.0)
        )
        let vm = HomeViewModel()

        await vm.loadDashboard(apiClient: loader)

        XCTAssertEqual(vm.activeGoal?.percentComplete, 42.0)
    }

    func testLoadDashboard_오류발생시기존데이터가유지된다() async {
        let loader = MockHomeDashboardLoader(shouldFail: true)
        let vm = HomeViewModel()
        let existingLog = makeDietLog(calories: 100)
        vm.todayDietLogs = [existingLog]

        await vm.loadDashboard(apiClient: loader)

        XCTAssertFalse(vm.isLoading)
        XCTAssertEqual(vm.todayDietLogs.count, 1)
    }

    func testLoadDashboard_로딩중플래그가올바르게관리된다() async {
        let loader = MockHomeDashboardLoader(dietLogs: [], sessions: [], goals: [])
        let vm = HomeViewModel()

        XCTAssertFalse(vm.isLoading)
        let task = Task { await vm.loadDashboard(apiClient: loader) }
        await task.value
        XCTAssertFalse(vm.isLoading)
    }

    // MARK: - Helpers

    private func makeDietLog(
        date: String = "2026-01-01",
        calories: Double = 0,
        protein: Double = 0,
        carbs: Double = 0,
        fat: Double = 0
    ) -> DietLogSummary {
        DietLogSummary(
            dietLogId: Int.random(in: 1...10_000),
            logDate: date,
            mealType: .BREAKFAST,
            totalCalories: calories,
            totalProteinG: protein,
            totalCarbsG: carbs,
            totalFatG: fat,
            totalSugarsG: nil,
            totalDietaryFiberG: nil,
            totalSaturatedFatG: nil,
            totalTransFatG: nil,
            totalCholesterolMg: nil,
            totalSodiumMg: nil
        )
    }

    private func makeSession(
        date: String,
        calories: Double,
        duration: Int = 30
    ) -> SessionSummary {
        SessionSummary(
            sessionId: Int.random(in: 1...10_000),
            sessionDate: date,
            durationMinutes: duration,
            totalVolumeKg: nil,
            caloriesBurned: calories,
            calorieEstimateMethod: "MET",
            notes: nil
        )
    }

    /// today 문자열 기준으로 offset 일 이전 날짜 문자열 반환
    private func dayOffset(_ offset: Int, from dateStr: String) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "ko_KR")
        guard let date = formatter.date(from: dateStr),
              let shifted = Calendar.current.date(byAdding: .day, value: offset, to: date)
        else { return dateStr }
        return formatter.string(from: shifted)
    }

    private func makeGoalSummary(id: Int, status: GoalStatus, percentComplete: Double?) -> GoalSummary {
        GoalSummary(
            goalId: id,
            goalType: .WEIGHT_LOSS,
            targetValue: 70.0,
            targetUnit: "kg",
            targetDate: "2026-12-31",
            startDate: "2026-01-01",
            status: status,
            percentComplete: percentComplete
        )
    }

    private func makeGoalProgress(id: Int, percent: Double) -> GoalProgressResponse {
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

private actor MockHomeDashboardLoader: HomeDashboardLoading {
    private let dietLogs: [DietLogSummary]
    private let sessions: [SessionSummary]
    private let goals: [GoalSummary]
    private let goalProgress: GoalProgressResponse?
    private let userProfile: UserProfile?
    private let shouldFail: Bool

    init(
        dietLogs: [DietLogSummary] = [],
        sessions: [SessionSummary] = [],
        goals: [GoalSummary] = [],
        goalProgress: GoalProgressResponse? = nil,
        userProfile: UserProfile? = nil,
        shouldFail: Bool = false
    ) {
        self.dietLogs = dietLogs
        self.sessions = sessions
        self.goals = goals
        self.goalProgress = goalProgress
        self.userProfile = userProfile
        self.shouldFail = shouldFail
    }

    func loadDietLogs(from: String, to: String) async throws -> DietLogListResponse {
        if shouldFail { throw APIError.unknown }
        return DietLogListResponse(
            content: dietLogs,
            page: 0, size: 50,
            totalElements: dietLogs.count,
            totalPages: 1, first: true, last: true
        )
    }

    func loadExerciseSessions() async throws -> SessionListResponse {
        if shouldFail { throw APIError.unknown }
        return SessionListResponse(
            content: sessions,
            pageNumber: 0, pageSize: 30,
            totalElements: sessions.count,
            totalPages: 1, first: true, last: true
        )
    }

    func loadGoals() async throws -> GoalListResponse {
        if shouldFail { throw APIError.unknown }
        return GoalListResponse(
            content: goals,
            pageNumber: 0, pageSize: 20,
            totalElements: goals.count,
            first: true, last: true
        )
    }

    func loadGoalProgress(id: Int) async throws -> GoalProgressResponse {
        if let p = goalProgress { return p }
        throw APIError.unknown
    }

    func loadUserProfile() async throws -> UserProfile {
        if shouldFail { throw APIError.unknown }
        return userProfile ?? UserProfile(
            id: 1, email: "t@t.com", displayName: "Tester",
            sex: nil, dateOfBirth: nil, heightCm: nil, weightKg: nil,
            activityLevel: nil, onboardingCompleted: true, isPremium: false,
            calorieTarget: nil, proteinTargetG: nil, carbTargetG: nil, fatTargetG: nil
        )
    }
}
