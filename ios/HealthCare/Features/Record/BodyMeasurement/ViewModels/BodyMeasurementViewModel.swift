import Foundation

@MainActor
final class BodyMeasurementViewModel: ObservableObject {
    @Published var measurements: [MeasurementResponse] = []
    @Published var latestMeasurement: MeasurementResponse?
    @Published var trendPoints: [MeasurementTrendPoint] = []
    @Published var selectedRange: MeasurementTrendRange = .month1
    @Published var selectedMetric: MeasurementMetric = .weight
    @Published var isLoading = false
    @Published var isTrendLoading = false
    @Published var errorMessage: String?
    @Published var showAddSheet = false

    func load(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let listResponse: MeasurementListResponse = apiClient.request(.getBodyMeasurements(page: 0, size: 50))
            async let latestResponse: MeasurementResponse = apiClient.request(.getLatestBodyMeasurement)

            let (list, latest) = try await (listResponse, latestResponse)
            measurements = list.content
            latestMeasurement = latest
            await loadTrendData(apiClient: apiClient)
        } catch {
            if case APIError.serverError(let code, _) = error, code == 404 {
                measurements = []
                latestMeasurement = nil
                trendPoints = []
            } else {
                errorMessage = error.localizedDescription
            }
        }
    }

    func loadTrendData(apiClient: APIClient) async {
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

            let rangeResponse: [MeasurementResponse] = try await apiClient.request(
                .getBodyMeasurementsRange(from: from, to: to)
            )
            let baselineMeasurement: MeasurementResponse? = try? await apiClient.request(
                .getBodyMeasurementAtOrBefore(date: from)
            )

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

    func delete(id: Int, apiClient: APIClient) async {
        do {
            try await apiClient.requestVoid(.deleteBodyMeasurement(id: id))
            measurements.removeAll { $0.id == id }
            if latestMeasurement?.id == id {
                latestMeasurement = measurements.first
            }
            await loadTrendData(apiClient: apiClient)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func measurementAdded(apiClient: APIClient) async {
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
            return "최근 1주"
        case .month1:
            return "최근 1개월"
        case .month3:
            return "최근 3개월"
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
