import Foundation

extension Notification.Name {
    /// 신체 측정 기록의 생성/수정/삭제가 일어난 직후 발행.
    /// 마이페이지 등 사용자 프로필을 표시하는 화면이 최신 weightKg를 다시 가져오도록 함.
    static let bodyMeasurementDidChange = Notification.Name("com.healthcare.bodyMeasurementDidChange")
}

protocol BodyMeasurementManaging: Sendable {
    func loadMeasurementList(page: Int, size: Int) async throws -> MeasurementListResponse
    func loadLatestMeasurement() async throws -> MeasurementResponse
    func loadBodyMeasurementGoals() async throws -> GoalListResponse
    func loadMeasurementRange(from: String, to: String) async throws -> [MeasurementResponse]
    func loadBaselineMeasurement(onOrBefore date: String) async throws -> MeasurementResponse
    func deleteMeasurement(id: Int) async throws
}

extension APIClient: BodyMeasurementManaging {
    func loadMeasurementList(page: Int, size: Int) async throws -> MeasurementListResponse {
        try await request(.getBodyMeasurements(page: page, size: size))
    }

    func loadLatestMeasurement() async throws -> MeasurementResponse {
        try await request(.getLatestBodyMeasurement)
    }

    func loadBodyMeasurementGoals() async throws -> GoalListResponse {
        try await request(.getGoals)
    }

    func loadMeasurementRange(from: String, to: String) async throws -> [MeasurementResponse] {
        try await request(.getBodyMeasurementsRange(from: from, to: to))
    }

    func loadBaselineMeasurement(onOrBefore date: String) async throws -> MeasurementResponse {
        try await request(.getBodyMeasurementAtOrBefore(date: date))
    }

    func deleteMeasurement(id: Int) async throws {
        try await requestVoid(.deleteBodyMeasurement(id: id))
    }
}

@MainActor
final class BodyMeasurementViewModel: ObservableObject {
    @Published var measurements: [MeasurementResponse] = []
    @Published var latestMeasurement: MeasurementResponse?
    @Published var trendPoints: [MeasurementTrendPoint] = []
    @Published var selectedRange: MeasurementTrendRange = .month1
    @Published var selectedMetric: MeasurementMetric = .weight
    @Published var activeGoal: GoalSummary?
    @Published var isLoading = false
    @Published var isTrendLoading = false
    @Published var errorMessage: String?
    @Published var showAddSheet = false

    private let widgetPublisher: any GoalWidgetSnapshotPublishing

    init(widgetPublisher: any GoalWidgetSnapshotPublishing = AppGoalWidgetSnapshotPublisher()) {
        self.widgetPublisher = widgetPublisher
    }

    func load(apiClient: any BodyMeasurementManaging) async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let listResponse = apiClient.loadMeasurementList(page: 0, size: 50)
            async let latestResponse = loadLatestMeasurement(apiClient: apiClient)

            let list = try await listResponse
            measurements = list.content
            latestMeasurement = await latestResponse
            await loadActiveGoal(apiClient: apiClient)
            await loadTrendData(apiClient: apiClient)
            widgetPublisher.publish(recentMeasurements: measurements)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func loadLatestMeasurement(apiClient: any BodyMeasurementManaging) async -> MeasurementResponse? {
        try? await apiClient.loadLatestMeasurement()
    }

    private func loadActiveGoal(apiClient: any BodyMeasurementManaging) async {
        let response = try? await apiClient.loadBodyMeasurementGoals()
        activeGoal = response?.content.first { $0.status == .ACTIVE }
    }

    func loadTrendData(apiClient: any BodyMeasurementManaging) async {
        guard latestMeasurement != nil || !measurements.isEmpty else {
            trendPoints = []
            return
        }

        isTrendLoading = true
        defer { isTrendLoading = false }

        do {
            let today = Date()
            let fromDate = Calendar.current.date(byAdding: .day, value: -(selectedRange.days - 1), to: today) ?? today
            let from = Self.apiDateFormatter.string(from: fromDate)
            let to = Self.apiDateFormatter.string(from: today)

            let rangeResponse = try await apiClient.loadMeasurementRange(from: from, to: to)
            let baselineMeasurement = try? await apiClient.loadBaselineMeasurement(onOrBefore: from)

            var points = rangeResponse.compactMap { measurement -> MeasurementTrendPoint? in
                guard let date = measurement.parsedDate,
                      let value = selectedMetric.value(from: measurement) else {
                    return nil
                }
                return MeasurementTrendPoint(
                    measurementId: measurement.id,
                    date: date,
                    label: measurement.shortDate,
                    value: value
                )
            }
            .sorted { $0.date < $1.date }

            if let baselineMeasurement,
               let baselineDate = baselineMeasurement.parsedDate,
               let baselineValue = selectedMetric.value(from: baselineMeasurement),
               !points.contains(where: { Calendar.current.isDate($0.date, inSameDayAs: baselineDate) }) {
                points.insert(
                    MeasurementTrendPoint(
                        measurementId: -baselineMeasurement.id,
                        date: baselineDate,
                        label: baselineMeasurement.shortDate,
                        value: baselineValue
                    ),
                    at: 0
                )
            }

            trendPoints = points
        } catch {
            trendPoints = []
        }
    }

    func delete(id: Int, apiClient: any BodyMeasurementManaging) async {
        do {
            try await apiClient.deleteMeasurement(id: id)
            measurements.removeAll { $0.id == id }
            if latestMeasurement?.id == id {
                latestMeasurement = measurements.first
            }
            await loadTrendData(apiClient: apiClient)
            widgetPublisher.publish(recentMeasurements: measurements)
            NotificationCenter.default.post(name: .bodyMeasurementDidChange, object: nil)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    /// AddMeasurement에서 측정을 추가한 직후 호출되는 콜백.
    /// `bodyMeasurementDidChange` 알림은 AddMeasurementViewModel이 직접 발행하므로 여기서는 발행하지 않음(중복 방지).
    func measurementAdded(apiClient: any BodyMeasurementManaging) async {
        await load(apiClient: apiClient)
    }

    var displayTrendPoints: [MeasurementTrendPoint] {
        trendPoints.sorted { $0.date < $1.date }
    }

    var hasTrendData: Bool {
        displayTrendPoints.count >= 2
    }

    var currentMetricUnit: String {
        selectedMetric.unit
    }

    var yAxisDomain: ClosedRange<Double> {
        selectedMetric.yAxisDomain(
            base: baseValue(for: selectedMetric),
            goalTarget: goalTargetValue(for: selectedMetric)
        )
    }

    private func baseValue(for metric: MeasurementMetric) -> Double? {
        guard let latest = latestMeasurement else { return nil }
        return metric.value(from: latest)
    }

    private func goalTargetValue(for metric: MeasurementMetric) -> Double? {
        guard let goal = activeGoal,
              let target = goal.targetValue else { return nil }
        let unit = goal.targetUnit?.lowercased() ?? ""

        switch metric {
        case .weight:
            guard unit == "kg", goal.goalType != .MUSCLE_GAIN else { return nil }
            return target
        case .bodyFat:
            guard unit == "%" else { return nil }
            return target
        case .muscleMass:
            guard unit == "kg", goal.goalType == .MUSCLE_GAIN else { return nil }
            return target
        case .waist:
            guard unit == "cm" else { return nil }
            return target
        }
    }

    var latestTrendValueText: String? {
        guard let latest = displayTrendPoints.last else { return nil }
        return Self.valueFormatter.string(from: NSNumber(value: latest.value))
    }

    var trendChangeText: String? {
        guard let first = displayTrendPoints.first,
              let last = displayTrendPoints.last else {
            return nil
        }

        let delta = last.value - first.value
        let sign = delta > 0 ? "+" : ""
        let formatted = Self.valueFormatter.string(from: NSNumber(value: delta)) ?? String(format: "%.1f", delta)
        return "\(sign)\(formatted)\(selectedMetric.unit)"
    }

    var trendSummaryText: String {
        switch selectedRange {
        case .week7:
            return String(localized: "body.range.recent.1w")
        case .month1:
            return String(localized: "body.range.recent.1m")
        case .month3:
            return String(localized: "body.range.recent.3m")
        }
    }

    static let apiDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static let valueFormatter: NumberFormatter = {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.minimumFractionDigits = 1
        formatter.maximumFractionDigits = 1
        return formatter
    }()
}
