import Foundation

@MainActor
final class ExerciseRecordViewModel: ObservableObject {
    @Published var sessions: [SessionSummary] = []
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var showAddSession = false

    @Published var userProfile: UserProfile? = nil
    @Published var activeGoal: GoalSummary? = nil

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

    // MARK: - 개인화 주간 목표

    /// 목표 유형 + 체중 기반 주간 소모 칼로리 목표
    /// 계산 기준: 1회 평균 소모 칼로리(체중 비례) × 주간 권장 운동 횟수
    var weeklyCalorieGoal: Double {
        let weight = userProfile?.weightKg ?? 70.0

        if let goalType = activeGoal?.goalType {
            switch goalType {
            case .WEIGHT_LOSS:
                // 체중 감량: 고강도 5회/주, 회당 약 6.5 kcal/kg
                return (weight * 6.5 * 5).rounded()
            case .MUSCLE_GAIN:
                // 근육 증가: 중강도 4회/주, 회당 약 4.5 kcal/kg
                return (weight * 4.5 * 4).rounded()
            case .BODY_RECOMPOSITION:
                // 체성분 개선: 중고강도 5회/주, 회당 약 5.5 kcal/kg
                return (weight * 5.5 * 5).rounded()
            case .ENDURANCE:
                // 지구력: 유산소 중심 5회/주, 회당 약 7.0 kcal/kg
                return (weight * 7.0 * 5).rounded()
            case .GENERAL_HEALTH:
                // 전반적 건강: 중강도 3회/주, 회당 약 4.5 kcal/kg
                return (weight * 4.5 * 3).rounded()
            }
        }

        // 활동 수준 기반 fallback
        switch userProfile?.activityLevel {
        case "SEDENTARY":         return (weight * 15).rounded()
        case "LIGHTLY_ACTIVE":    return (weight * 20).rounded()
        case "MODERATELY_ACTIVE": return (weight * 27).rounded()
        case "VERY_ACTIVE":       return (weight * 35).rounded()
        default:                  return 2_500
        }
    }

    /// 목표 유형 + 체중 기반 주간 볼륨 목표
    var weeklyVolumeGoal: Double {
        let weight = userProfile?.weightKg ?? 70.0

        if let goalType = activeGoal?.goalType {
            switch goalType {
            case .WEIGHT_LOSS:        return (weight * 45).rounded()
            case .MUSCLE_GAIN:        return (weight * 80).rounded()
            case .BODY_RECOMPOSITION: return (weight * 60).rounded()
            case .ENDURANCE:          return (weight * 40).rounded()
            case .GENERAL_HEALTH:     return (weight * 35).rounded()
            }
        }

        return (weight * 55).rounded()  // 기본 fallback
    }

    /// 0.0 ~ 1.0
    var volumeProgress: Double {
        min(weeklyVolume / max(weeklyVolumeGoal, 1), 1.0)
    }

    var calorieProgress: Double {
        min(weeklyCalories / max(weeklyCalorieGoal, 1), 1.0)
    }

    // MARK: - API

    func loadSessions(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            // 세 요청을 동시에 시작
            async let sessionFetch: SessionListResponse = apiClient.request(
                .getExerciseSessions(from: fromDate, to: nil, page: 0, size: 50)
            )
            async let profileFetch: UserProfile = apiClient.request(.getProfile)
            async let goalsFetch: GoalListResponse = apiClient.request(.getGoals)

            // 세션은 핵심 데이터 — 실패 시 에러 표시
            let response = try await sessionFetch
            sessions = response.content

            // 프로필·목표는 보조 데이터 — 실패해도 세션 표시는 유지
            if let profile = try? await profileFetch { userProfile = profile }
            if let goals = try? await goalsFetch {
                activeGoal = goals.content.first { $0.status == .ACTIVE }
            }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "exercise.error.loadList")
        }
    }

    func deleteSession(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteExerciseSession(id: id))
            sessions.removeAll { $0.sessionId == id }
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "exercise.error.delete")
        }
    }

    func sessionAdded(apiClient: APIClient) async {
        await loadSessions(apiClient: apiClient)
    }
}
