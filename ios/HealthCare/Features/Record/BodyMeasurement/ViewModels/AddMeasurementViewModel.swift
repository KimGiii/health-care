import Foundation

@MainActor
final class AddMeasurementViewModel: ObservableObject {
    // MARK: - 날짜
    @Published var measuredAt: Date = Date()

    // MARK: - 체성분
    @Published var weightKg: String = ""
    @Published var bodyFatPct: String = ""
    @Published var muscleMassKg: String = ""
    @Published var bmi: String = ""

    // MARK: - 둘레 (cm)
    @Published var chestCm: String = ""
    @Published var waistCm: String = ""
    @Published var hipCm: String = ""
    @Published var thighCm: String = ""
    @Published var armCm: String = ""

    // MARK: - 메모
    @Published var notes: String = ""

    // MARK: - 상태
    @Published var isSubmitting = false
    @Published var errorMessage: String?

    private let onSuccess: () -> Void

    init(onSuccess: @escaping () -> Void) {
        self.onSuccess = onSuccess
    }

    // MARK: - 제출 가능 여부

    var hasAnyValue: Bool {
        let compositionFilled = !weightKg.isEmpty || !bodyFatPct.isEmpty
            || !muscleMassKg.isEmpty || !bmi.isEmpty
        let circumferenceFilled = !chestCm.isEmpty || !waistCm.isEmpty
            || !hipCm.isEmpty || !thighCm.isEmpty || !armCm.isEmpty
        return compositionFilled || circumferenceFilled
    }

    // MARK: - 제출

    func submit(apiClient: APIClient) async {
        isSubmitting = true
        defer { isSubmitting = false }

        let dateStr = DateFormatter.localDate(from: measuredAt)
        let request = CreateMeasurementRequest(
            measuredAt: dateStr,
            weightKg: Double(weightKg),
            bodyFatPct: Double(bodyFatPct),
            muscleMassKg: Double(muscleMassKg),
            bmi: Double(bmi),
            chestCm: Double(chestCm),
            waistCm: Double(waistCm),
            hipCm: Double(hipCm),
            thighCm: Double(thighCm),
            armCm: Double(armCm),
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
}

private extension DateFormatter {
    static func localDate(from date: Date) -> String {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        return f.string(from: date)
    }
}
