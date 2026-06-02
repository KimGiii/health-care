import Foundation

@MainActor
final class DietRecordViewModel: ObservableObject {
    @Published var logs: [DietLogSummary] = []
    @Published var userProfile: UserProfile? = nil
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddLog = false

    // MARK: - 오늘 날짜 문자열
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    var today: String { dateFormatter.string(from: Date()) }

    // MARK: - 오늘 식사 기록 (날짜별 필터)
    var todayLogs: [DietLogSummary] {
        logs.filter { $0.logDate == today }
    }

    // MARK: - 오늘 영양 합계
    var todayCalories: Double {
        todayLogs.compactMap(\.totalCalories).reduce(0, +)
    }

    var todayProteinG: Double {
        todayLogs.compactMap(\.totalProteinG).reduce(0, +)
    }

    var todayCarbsG: Double {
        todayLogs.compactMap(\.totalCarbsG).reduce(0, +)
    }

    var todayFatG: Double {
        todayLogs.compactMap(\.totalFatG).reduce(0, +)
    }

    // MARK: - 목표 (사용자 프로필 우선, 없거나 0이면 fallback)
    var dailyCalorieGoal: Double {
        if let t = userProfile?.calorieTarget, t > 0 { return Double(t) }
        return 2_000
    }
    var dailyProteinGoal: Double {
        if let g = userProfile?.proteinTargetG, g > 0 { return Double(g) }
        return 60
    }
    var dailyCarbsGoal: Double {
        if let g = userProfile?.carbTargetG, g > 0 { return Double(g) }
        return 250
    }
    var dailyFatGoal: Double {
        if let g = userProfile?.fatTargetG, g > 0 { return Double(g) }
        return 65
    }

    var calorieProgress: Double { min(todayCalories / dailyCalorieGoal, 1.0) }
    var proteinProgress: Double { min(todayProteinG / dailyProteinGoal, 1.0) }
    var carbsProgress: Double   { min(todayCarbsG   / dailyCarbsGoal,   1.0) }
    var fatProgress: Double     { min(todayFatG     / dailyFatGoal,     1.0) }

    /// 권장 - 섭취 (음수면 초과 섭취량).
    var remainingCalories: Double { dailyCalorieGoal - todayCalories }

    // MARK: - 오늘 식사 기록 (식사 유형 순 정렬)
    var todaySortedLogs: [DietLogSummary] {
        todayLogs.sorted { $0.mealType.rawValue < $1.mealType.rawValue }
    }

    // MARK: - API

    func loadLogs(apiClient: APIClient) async {
        // 중복 호출 가드 — onAppear가 짧은 간격에 두 번 호출돼도 in-flight면 무시.
        guard !isLoading else { return }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            async let logsRequest: DietLogListResponse = apiClient.request(
                .getDietLogs(from: today, to: today, page: 0, size: 50)
            )
            async let profileRequest: UserProfile = apiClient.request(.getProfile)

            let logsResponse = try await logsRequest
            logs = logsResponse.content
            if let profile = try? await profileRequest {
                userProfile = profile
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "식단 기록을 불러오지 못했습니다."
        }
    }

    func deleteLog(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteDietLog(id: id))
            logs.removeAll { $0.dietLogId == id }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "삭제 중 오류가 발생했습니다."
        }
    }

    func logAdded(apiClient: APIClient) async {
        await loadLogs(apiClient: apiClient)
    }
}
