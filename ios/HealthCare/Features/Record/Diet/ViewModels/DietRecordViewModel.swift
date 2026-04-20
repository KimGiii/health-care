import Foundation

@MainActor
final class DietRecordViewModel: ObservableObject {
    @Published var logs: [DietLogSummary] = []
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

    // MARK: - 목표 대비 진행률 (MVP 고정값)
    static let dailyCalorieGoal: Double = 2_000
    static let dailyProteinGoal: Double = 60
    static let dailyCarbsGoal: Double   = 250
    static let dailyFatGoal: Double     = 65

    var calorieProgress: Double { min(todayCalories / Self.dailyCalorieGoal, 1.0) }
    var proteinProgress: Double { min(todayProteinG / Self.dailyProteinGoal, 1.0) }
    var carbsProgress: Double   { min(todayCarbsG   / Self.dailyCarbsGoal,   1.0) }
    var fatProgress: Double     { min(todayFatG     / Self.dailyFatGoal,     1.0) }

    // MARK: - 오늘 식사 기록 (식사 유형 순 정렬)
    var todaySortedLogs: [DietLogSummary] {
        todayLogs.sorted { $0.mealType.rawValue < $1.mealType.rawValue }
    }

    // MARK: - API

    func loadLogs(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let response: DietLogListResponse = try await apiClient.request(
                .getDietLogs(from: today, to: today, page: 0, size: 50)
            )
            logs = response.content
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
