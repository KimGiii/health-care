import Foundation

@MainActor
final class AddMeasurementViewModel: ObservableObject {
    @Published var measuredAt: Date = Date()
    @Published var weightKg: String = ""
    @Published var bodyFatPct: String = ""
    @Published var muscleMassKg: String = ""
    @Published var bmi: String = ""
    @Published var waistCm: String = ""
    @Published var notes: String = ""

    @Published var isSubmitting = false
    @Published var errorMessage: String?

    private let onSuccess: () -> Void

    init(onSuccess: @escaping () -> Void) {
        self.onSuccess = onSuccess
    }

    func submit(apiClient: APIClient) async {
        isSubmitting = true
        defer { isSubmitting = false }

        let dateStr = ISO8601DateFormatter.localDate(from: measuredAt)
        let request = CreateMeasurementRequest(
            measuredAt: dateStr,
            weightKg: Double(weightKg),
            bodyFatPct: Double(bodyFatPct),
            muscleMassKg: Double(muscleMassKg),
            bmi: Double(bmi),
            waistCm: Double(waistCm),
            notes: notes.isEmpty ? nil : notes
        )

        do {
            let body = try apiClient.encode(request)
            let _: MeasurementResponse = try await apiClient.request(.createBodyMeasurement(body: body))
            onSuccess()
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    var hasAnyValue: Bool {
        !weightKg.isEmpty || !bodyFatPct.isEmpty || !muscleMassKg.isEmpty ||
        !bmi.isEmpty || !waistCm.isEmpty
    }
}

private extension ISO8601DateFormatter {
    static func localDate(from date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }
}
