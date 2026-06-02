import Foundation

@MainActor
final class DiaryViewModel: ObservableObject {
    @Published var isLoading = false
    @Published var errorMessage: String?
    @Published var selectedDate = Date()
    @Published var exerciseSessions: [SessionSummary] = []
    @Published var dietLogs: [DietLogSummary] = []
    @Published var bodyMeasurements: [MeasurementResponse] = []

    private let calendar = Calendar.current
    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.locale = Locale(identifier: "ko_KR")
        return f
    }()

    var currentMonth: Date {
        calendar.date(from: calendar.dateComponents([.year, .month], from: selectedDate)) ?? selectedDate
    }

    var monthYearText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy년 M월"
        formatter.locale = Locale(identifier: "ko_KR")
        return formatter.string(from: selectedDate)
    }

    /// 현재 월의 날짜들 (이전/다음 월 포함하여 그리드 채우기)
    var calendarDays: [Date?] {
        guard let monthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: selectedDate)) else {
            return []
        }

        let firstWeekday = calendar.component(.weekday, from: monthStart)
        let offsetDays = firstWeekday - 1  // 일요일=1이므로 0부터 시작하게 조정

        guard let monthRange = calendar.range(of: .day, in: .month, for: monthStart) else {
            return []
        }

        var days: [Date?] = []

        // 이전 월 날짜로 빈 칸 채우기
        for _ in 0..<offsetDays {
            days.append(nil)
        }

        // 현재 월 날짜 추가
        for day in monthRange {
            if let date = calendar.date(byAdding: .day, value: day - 1, to: monthStart) {
                days.append(date)
            }
        }

        return days
    }

    /// 특정 날짜에 운동 기록이 있는지 확인
    func hasExerciseRecord(on date: Date) -> Bool {
        let dateString = dateFormatter.string(from: date)
        return exerciseSessions.contains { $0.sessionDate == dateString }
    }

    /// 특정 날짜에 식단 기록이 있는지 확인
    func hasDietRecord(on date: Date) -> Bool {
        let dateString = dateFormatter.string(from: date)
        return dietLogs.contains { $0.logDate == dateString }
    }

    /// 특정 날짜에 신체 측정 기록이 있는지 확인
    func hasMeasurementRecord(on date: Date) -> Bool {
        let dateString = dateFormatter.string(from: date)
        return bodyMeasurements.contains { $0.measuredAt == dateString }
    }

    /// 특정 날짜의 운동 세션 가져오기
    func exerciseSessions(on date: Date) -> [SessionSummary] {
        let dateString = dateFormatter.string(from: date)
        return exerciseSessions.filter { $0.sessionDate == dateString }
    }

    /// 특정 날짜의 식단 로그 가져오기
    func dietLogs(on date: Date) -> [DietLogSummary] {
        let dateString = dateFormatter.string(from: date)
        return dietLogs.filter { $0.logDate == dateString }
    }

    /// 특정 날짜의 신체 측정 가져오기
    func measurements(on date: Date) -> [MeasurementResponse] {
        let dateString = dateFormatter.string(from: date)
        return bodyMeasurements.filter { $0.measuredAt == dateString }
    }

    func previousMonth() {
        selectedDate = calendar.date(byAdding: .month, value: -1, to: selectedDate) ?? selectedDate
    }

    func nextMonth() {
        selectedDate = calendar.date(byAdding: .month, value: 1, to: selectedDate) ?? selectedDate
    }

    func load(apiClient: APIClient) async {
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        // 현재 월의 첫날과 마지막 날 계산
        guard let monthStart = calendar.date(from: calendar.dateComponents([.year, .month], from: selectedDate)),
              let monthEnd = calendar.date(byAdding: DateComponents(month: 1, day: -1), to: monthStart) else {
            return
        }

        let fromDate = dateFormatter.string(from: monthStart)
        let toDate = dateFormatter.string(from: monthEnd)

        // 운동·식단·신체 측정 기록을 병렬로 로드하되, 한 요청의 실패가 전체 캘린더를 비우지 않게 처리한다.
        async let exerciseResponse: SessionListResponse? = loadExerciseSessions(
            apiClient: apiClient,
            fromDate: fromDate,
            toDate: toDate
        )
        async let dietResponse: DietLogListResponse? = loadDietLogs(
            apiClient: apiClient,
            fromDate: fromDate,
            toDate: toDate
        )
        async let measurementResponse: [MeasurementResponse]? = loadMeasurements(
            apiClient: apiClient,
            fromDate: fromDate,
            toDate: toDate
        )

        let (exercises, diets, measurements) = await (exerciseResponse, dietResponse, measurementResponse)
        var failedSources: [String] = []

        if let exercises {
            exerciseSessions = exercises.content
        } else {
            failedSources.append("운동")
        }

        if let diets {
            dietLogs = diets.content
        } else {
            failedSources.append("식단")
        }

        if let measurements {
            bodyMeasurements = measurements
        } else {
            failedSources.append("신체 측정")
        }

        if !failedSources.isEmpty {
            errorMessage = "일부 기록을 불러오지 못했습니다: \(failedSources.joined(separator: ", "))"
        }
    }

    private func loadExerciseSessions(
        apiClient: APIClient,
        fromDate: String,
        toDate: String
    ) async -> SessionListResponse? {
        try? await apiClient.request(.getExerciseSessions(from: fromDate, to: toDate, page: 0, size: 100))
    }

    private func loadDietLogs(
        apiClient: APIClient,
        fromDate: String,
        toDate: String
    ) async -> DietLogListResponse? {
        try? await apiClient.request(.getDietLogs(from: fromDate, to: toDate, page: 0, size: 100))
    }

    private func loadMeasurements(
        apiClient: APIClient,
        fromDate: String,
        toDate: String
    ) async -> [MeasurementResponse]? {
        try? await apiClient.request(.getBodyMeasurementsRange(from: fromDate, to: toDate))
    }
}
