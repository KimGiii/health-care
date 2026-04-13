import Foundation

@MainActor
final class ExerciseRecordViewModel: ObservableObject {
    @Published var sessions: [SessionSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddSession = false

    // MARK: - 주간 목표 (MVP 고정값)
    static let weeklyVolumeGoal: Double  = 5_000   // kg
    static let weeklyCalorieGoal: Double = 2_500   // kcal

    // MARK: - 날짜 포매터
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    var fromDate: String {
        let d = Calendar.current.date(byAdding: .day, value: -30, to: Date()) ?? Date()
        return dateFormatter.string(from: d)
    }

    // MARK: - 이번 주 세션 (월요일 기준)
    var currentWeekSessions: [SessionSummary] {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 2   // 월요일 시작
        let startOfWeek = cal.date(
            from: cal.dateComponents([.yearForWeekOfYear, .weekOfYear], from: Date())
        ) ?? Date()
        return sessions.filter { s in
            guard let d = dateFormatter.date(from: s.sessionDate) else { return false }
            return d >= startOfWeek
        }
    }

    var weeklyVolume: Double {
        currentWeekSessions.compactMap(\.totalVolumeKg).reduce(0, +)
    }

    var weeklyCalories: Double {
        currentWeekSessions.compactMap(\.caloriesBurned).reduce(0, +)
    }

    var weeklyWorkoutDays: Int {
        Set(currentWeekSessions.map(\.sessionDate)).count
    }

    /// 0.0 ~ 1.0
    var volumeProgress: Double {
        min(weeklyVolume / Self.weeklyVolumeGoal, 1.0)
    }

    var calorieProgress: Double {
        min(weeklyCalories / Self.weeklyCalorieGoal, 1.0)
    }

    // MARK: - API

    func loadSessions(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let response: SessionListResponse = try await apiClient.request(
                .getExerciseSessions(from: fromDate, to: nil, page: 0, size: 50)
            )
            sessions = response.content
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "운동 기록을 불러오지 못했습니다."
        }
    }

    func deleteSession(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteExerciseSession(id: id))
            sessions.removeAll { $0.sessionId == id }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "삭제 중 오류가 발생했습니다."
        }
    }

    func sessionAdded(apiClient: APIClient) async {
        await loadSessions(apiClient: apiClient)
    }
}
