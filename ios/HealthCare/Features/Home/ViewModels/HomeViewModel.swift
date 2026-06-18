import Foundation

protocol HomeDashboardLoading: Sendable {
    func loadDietLogs(from: String, to: String) async throws -> DietLogListResponse
    func loadExerciseSessions() async throws -> SessionListResponse
    func loadGoals() async throws -> GoalListResponse
    func loadGoalProgress(id: Int) async throws -> GoalProgressResponse
    func loadUserProfile() async throws -> UserProfile
    func loadBodyMeasurements(from: String, to: String) async throws -> [MeasurementResponse]
}

extension APIClient: HomeDashboardLoading {
    func loadDietLogs(from: String, to: String) async throws -> DietLogListResponse {
        // size 50: 7일 × 4끼 = 최대 28개, 여유분 포함
        try await request(.getDietLogs(from: from, to: to, page: 0, size: 50))
    }

    func loadBodyMeasurements(from: String, to: String) async throws -> [MeasurementResponse] {
        try await request(.getBodyMeasurementsRange(from: from, to: to))
    }
    func loadExerciseSessions() async throws -> SessionListResponse {
        // size 30: 7일치 세션을 충분히 커버
        try await request(.getExerciseSessions(from: nil, to: nil, page: 0, size: 30))
    }
    func loadGoals() async throws -> GoalListResponse {
        try await request(.getGoals)
    }
    func loadGoalProgress(id: Int) async throws -> GoalProgressResponse {
        try await request(.getGoalProgress(id: id))
    }
    func loadUserProfile() async throws -> UserProfile {
        try await request(.getProfile)
    }
}

// MARK: - 주간 활동 데이터 포인트

struct DayActivity: Equatable {
    /// "yyyy-MM-dd" 형식 날짜
    let date: String
    /// 해당 날 총 섭취 칼로리
    let caloriesIn: Double
    /// 해당 날 운동 소모 칼로리
    let caloriesBurned: Double
    /// 해당 날 운동 시간 (분)
    let durationMinutes: Int
}

// MARK: - ViewModel

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String? = nil

    /// 오늘 식단 로그 (todayDietLogs 직접 설정은 테스트 전용)
    @Published var todayDietLogs: [DietLogSummary] = []
    /// 최근 7일 식단 로그 전체 (streak, 주간 추세 계산용)
    @Published var weekDietLogs: [DietLogSummary] = []
    @Published var recentSessions: [SessionSummary] = []
    @Published var activeGoal: GoalSummary? = nil
    /// 사용자 프로필 — 백엔드가 자동 계산한 권장 칼로리·매크로 표시에 사용
    @Published var userProfile: UserProfile? = nil
    /// 최근 7일 체중 측정 (목표 위젯 차트용). 화면 표시에는 사용하지 않는다.
    @Published var recentMeasurements: [MeasurementResponse] = []

    // MARK: - 날짜 유틸리티

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = LocaleManager.resolvedLocale
        return f
    }()

    var today: String { dateFormatter.string(from: Date()) }

    private var weekStart: String {
        let date = Calendar.current.date(byAdding: .day, value: -6, to: Date()) ?? Date()
        return dateFormatter.string(from: date)
    }

    /// 체중 측정 위젯용 — 매일 측정하지 않는 경우가 흔해 30일 창에서 가져온다.
    private var measurementRangeStart: String {
        let date = Calendar.current.date(byAdding: .day, value: -29, to: Date()) ?? Date()
        return dateFormatter.string(from: date)
    }

    // MARK: - 칼로리

    /// 오늘 총 섭취 칼로리
    var todayCalories: Double {
        todayDietLogs.compactMap(\.totalCalories).reduce(0, +)
    }

    /// 칼로리 일일 목표.
    /// 우선순위: 사용자 프로필의 백엔드 자동 계산값 → 기본 2,000 kcal fallback
    var dailyCalorieGoal: Double {
        if let target = userProfile?.calorieTarget, target > 0 { return Double(target) }
        return 2_000.0
    }

    /// 칼로리 진행률 (0~1, 초과 시 1로 클램프)
    var calorieProgress: Double {
        min(todayCalories / dailyCalorieGoal, 1.0)
    }

    // MARK: - 운동 (오늘)

    /// 오늘 운동으로 소모한 총 칼로리
    var todayBurnedCalories: Double {
        recentSessions
            .filter { $0.sessionDate == today }
            .compactMap(\.caloriesBurned)
            .reduce(0, +)
    }

    /// 오늘 총 운동 시간 (분)
    var todayDurationMinutes: Int {
        recentSessions
            .filter { $0.sessionDate == today }
            .compactMap(\.durationMinutes)
            .reduce(0, +)
    }

    /// 운동 소모 칼로리 → 500 kcal 기준 활동 진행률 (0~1)
    var activityProgress: Double {
        min(todayBurnedCalories / 500.0, 1.0)
    }

    // MARK: - 매크로 (오늘)

    var todayProteinG: Double {
        todayDietLogs.compactMap(\.totalProteinG).reduce(0, +)
    }

    var todayCarbsG: Double {
        todayDietLogs.compactMap(\.totalCarbsG).reduce(0, +)
    }

    var todayFatG: Double {
        todayDietLogs.compactMap(\.totalFatG).reduce(0, +)
    }

    /// 매크로 일일 목표.
    /// 우선순위: 사용자 프로필의 백엔드 자동 계산값 → 기본값 fallback
    var dailyProteinGoal: Double {
        if let g = userProfile?.proteinTargetG, g > 0 { return Double(g) }
        return 150.0
    }
    var dailyCarbsGoal: Double {
        if let g = userProfile?.carbTargetG, g > 0 { return Double(g) }
        return 200.0
    }
    var dailyFatGoal: Double {
        if let g = userProfile?.fatTargetG, g > 0 { return Double(g) }
        return 60.0
    }

    var proteinProgress: Double { min(todayProteinG / dailyProteinGoal, 1.0) }
    var carbsProgress: Double   { min(todayCarbsG   / dailyCarbsGoal,   1.0) }
    var fatProgress: Double     { min(todayFatG     / dailyFatGoal,     1.0) }

    // MARK: - 연속 기록일

    /// 오늘부터 거슬러 올라가며 식단 또는 운동 기록이 있는 연속 일수
    /// 주간 데이터(7일) 범위 내에서 계산하므로 최대 7일
    var streakDays: Int {
        let calendar = Calendar.current
        var streak = 0
        for offset in 0...6 {
            guard let date = calendar.date(byAdding: .day, value: -offset, to: Date()) else { break }
            let dateStr = dateFormatter.string(from: date)
            let hasDiet     = weekDietLogs.contains    { $0.logDate     == dateStr }
            let hasExercise = recentSessions.contains  { $0.sessionDate == dateStr }
            if hasDiet || hasExercise {
                streak += 1
            } else {
                break   // 연속이 끊기면 중지
            }
        }
        return streak
    }

    // MARK: - 7일 추세

    /// 오늘 기준 최근 7일(오래된 날부터) 일별 활동 배열
    var weeklyActivity: [DayActivity] {
        let calendar = Calendar.current
        return (0...6).reversed().compactMap { offset -> DayActivity? in
            guard let date = calendar.date(byAdding: .day, value: -offset, to: Date()) else { return nil }
            let dateStr = dateFormatter.string(from: date)

            let caloriesIn = weekDietLogs
                .filter { $0.logDate == dateStr }
                .compactMap(\.totalCalories)
                .reduce(0, +)
            let caloriesBurned = recentSessions
                .filter { $0.sessionDate == dateStr }
                .compactMap(\.caloriesBurned)
                .reduce(0, +)
            let durationMinutes = recentSessions
                .filter { $0.sessionDate == dateStr }
                .compactMap(\.durationMinutes)
                .reduce(0, +)

            return DayActivity(
                date: dateStr,
                caloriesIn: caloriesIn,
                caloriesBurned: caloriesBurned,
                durationMinutes: durationMinutes
            )
        }
    }

    // MARK: - API

    func loadDashboard(apiClient: any HomeDashboardLoading) async {
        // 중복 호출 가드 — 탭 재진입 등으로 onAppear가 짧은 간격에 두 번 호출돼도
        // in-flight 중이면 두 번째는 즉시 무시한다. MainActor isolated이라 race-free.
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // 4개 병렬 호출 — 일부 실패가 전체 실패로 전파되지 않도록 개별 Task 로 격리한다.
        // `try await (a, b, c, d)` 패턴은 a 가 실패하면 b/c/d 결과가 모두 버려지므로
        // 화면이 빈 상태로 전환되어 "처음 진입 시 에러, 두 번째 시 성공" 패턴을 만든다.
        async let dietResult        = result { try await apiClient.loadDietLogs(from: weekStart, to: today) }
        async let exerciseResult    = result { try await apiClient.loadExerciseSessions() }
        async let goalResult        = result { try await apiClient.loadGoals() }
        async let profileResult     = result { try await apiClient.loadUserProfile() }
        async let measurementResult = result { try await apiClient.loadBodyMeasurements(from: measurementRangeStart, to: today) }

        let (dietR, exerciseR, goalR, profileR, measurementR) =
            await (dietResult, exerciseResult, goalResult, profileResult, measurementResult)

        var failedSources: [String] = []

        switch dietR {
        case .success(let diet):
            weekDietLogs   = diet.content
            todayDietLogs  = diet.content.filter { $0.logDate == today }
        case .failure(let err):
            failedSources.append(String(format: String(localized: "home.error.source.diet"), diagnose(err)))
        }

        switch exerciseR {
        case .success(let exercise): recentSessions = exercise.content
        case .failure(let err):
            failedSources.append(String(format: String(localized: "home.error.source.exercise"), diagnose(err)))
        }

        switch profileR {
        case .success(let profile): userProfile = profile
        case .failure(let err):
            failedSources.append(String(format: String(localized: "home.error.source.profile"), diagnose(err)))
        }

        switch goalR {
        case .success(let goals):
            if let goal = goals.content.first(where: { $0.status == .ACTIVE }) {
                activeGoal = await enrichedActiveGoal(goal, apiClient: apiClient)
            } else {
                activeGoal = nil
            }
        case .failure(let err):
            failedSources.append(String(format: String(localized: "home.error.source.goal"), diagnose(err)))
        }

        // 체중 측정은 화면에 직접 안 보여서 실패해도 위젯만 빈 차트로 처리. 에러 메시지에 포함 X.
        if case .success(let measurements) = measurementR {
            recentMeasurements = measurements
        }

        if !failedSources.isEmpty {
            errorMessage = String(format: String(localized: "home.error.partialLoad"),
                                  failedSources.joined(separator: ", "))
        }

        // 위젯 스냅샷 동기화 — 다이어트 데이터가 정상 로드된 경우에만.
        // 위젯은 절대 네트워크 호출을 하지 않고, 앱이 저장해둔 스냅샷만 읽는다.
        publishCalorieWidgetSnapshot()
        publishGoalWidgetSnapshot()
        publishStreakWidgetSnapshot()
    }

    /// 현재 ViewModel 상태로부터 위젯용 스냅샷을 만들어 App Group에 저장하고 위젯 갱신을 요청한다.
    func publishCalorieWidgetSnapshot() {
        guard let store = WidgetDataStore() else { return }
        let snapshot = CalorieWidgetSnapshot(
            date: Date(),
            consumedKcal: Int(todayCalories.rounded()),
            targetKcal: Int(dailyCalorieGoal.rounded()),
            proteinG: todayProteinG,
            proteinTargetG: dailyProteinGoal,
            carbsG: todayCarbsG,
            carbsTargetG: dailyCarbsGoal,
            fatG: todayFatG,
            fatTargetG: dailyFatGoal
        )
        store.saveCalorie(snapshot)
        WidgetReloadCenter.reloadCalorie()
    }

    /// 활성 목표 + 목표 타입별 최근 지표를 위젯 스냅샷으로 변환해 App Group에 저장.
    func publishGoalWidgetSnapshot() {
        AppGoalWidgetSnapshotPublisher().publish(
            goal: activeGoal,
            recentMeasurements: recentMeasurements,
            recentSessions: recentSessions
        )
    }

    /// 연속 기록 일수 + 오늘 식단/운동 체크 상태를 위젯에 저장.
    func publishStreakWidgetSnapshot() {
        guard let store = WidgetDataStore() else { return }
        let snapshot = StreakWidgetSnapshot(
            streakDays: streakDays,
            cap: 7,                                          // 현재 streak 계산 윈도우와 동일
            didLogDietToday: !todayDietLogs.isEmpty,
            didLogExerciseToday: recentSessions.contains { $0.sessionDate == today }
        )
        store.saveStreak(snapshot)
        WidgetReloadCenter.reloadStreak()
    }

    /// 비동기 throw 호출을 Result 로 감싸 일부 실패가 다른 결과를 휘말리게 하지 않도록 한다.
    private nonisolated func result<T>(_ op: @Sendable () async throws -> T) async -> Result<T, Error> {
        do { return .success(try await op()) }
        catch { return .failure(error) }
    }

    private nonisolated func diagnose(_ error: Error) -> String {
        if let apiError = error as? APIError {
            return apiError.errorDescription ?? String(localized: "home.error.apiGeneric")
        }
        return String(describing: type(of: error))
    }

    private func enrichedActiveGoal(_ goal: GoalSummary, apiClient: any HomeDashboardLoading) async -> GoalSummary {
        do {
            let progress = try await apiClient.loadGoalProgress(id: goal.goalId)
            return goal.withPercentComplete(progress.percentComplete)
        } catch {
            return goal
        }
    }
}
