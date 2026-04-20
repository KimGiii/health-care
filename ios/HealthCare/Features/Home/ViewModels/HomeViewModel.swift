import Foundation

@MainActor
final class HomeViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var todayDietLogs: [DietLogSummary] = []
    @Published var recentSessions: [SessionSummary] = []
    @Published var activeGoal: GoalSummary? = nil

    // MARK: - 날짜 포매터

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    var today: String { dateFormatter.string(from: Date()) }

    // MARK: - 진행률 계산

    /// 오늘 총 섭취 칼로리
    var todayCalories: Double {
        todayDietLogs.compactMap(\.totalCalories).reduce(0, +)
    }

    /// 칼로리 목표 (목표 설정 시 MacroTargets 연동, 기본 2000kcal)
    var dailyCalorieGoal: Double { 2_000.0 }

    /// 칼로리 진행률 (0~1)
    var calorieProgress: Double {
        min(todayCalories / dailyCalorieGoal, 1.0)
    }

    /// 오늘 운동 소모 칼로리 → 500kcal 기준 활동 진행률
    var activityProgress: Double {
        let burned = recentSessions
            .filter { $0.sessionDate == today }
            .compactMap(\.caloriesBurned)
            .reduce(0, +)
        return min(burned / 500.0, 1.0)
    }

    // MARK: - API

    func loadDashboard(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }

        do {
            async let dietResponse: DietLogListResponse = apiClient.request(
                .getDietLogs(from: today, to: today, page: 0, size: 10)
            )
            async let exerciseResponse: SessionListResponse = apiClient.request(
                .getExerciseSessions(from: nil, to: nil, page: 0, size: 5)
            )
            async let goalResponse: GoalListResponse = apiClient.request(.getGoals)

            let (diet, exercise, goals) = try await (dietResponse, exerciseResponse, goalResponse)
            todayDietLogs  = diet.content
            recentSessions = exercise.content
            activeGoal     = goals.content.first { $0.status == .ACTIVE }
        } catch {
            // 대시보드 로딩 실패는 조용히 처리 — 빈 상태 유지
        }
    }
}
