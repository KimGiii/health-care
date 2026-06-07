import Combine
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

    // MARK: - 프로필 키 (BMI 자동 계산용)
    private(set) var userHeightCm: Double? = nil
    var isBMIAutoCalculated: Bool { userHeightCm != nil }

    private let onSuccess: () -> Void
    private var cancellables = Set<AnyCancellable>()

    init(initialDate: Date = Date(), onSuccess: @escaping () -> Void) {
        measuredAt = min(initialDate, Date())
        self.onSuccess = onSuccess

        $weightKg
            .sink { [weak self] newWeight in
                self?.autoCalculateBMI(weightStr: newWeight)
            }
            .store(in: &cancellables)
    }

    // MARK: - 프로필 로드 (키 기반 BMI 자동 계산)

    func loadUserProfile(apiClient: APIClient) async {
        do {
            let profile: UserProfile = try await apiClient.request(.getProfile)
            userHeightCm = profile.heightCm
            autoCalculateBMI(weightStr: weightKg)
        } catch {
            // 키 정보가 없으면 BMI 직접 입력 허용
        }
    }

    // MARK: - BMI 자동 계산

    private func autoCalculateBMI(weightStr: String) {
        guard let heightCm = userHeightCm,
              heightCm > 0,
              let weight = Double(weightStr),
              weight > 0 else { return }
        let heightM = heightCm / 100.0
        let calculated = weight / (heightM * heightM)
        bmi = String(format: "%.1f", calculated)
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
            // 신체 측정 생성 → 백엔드가 User.weightKg를 동기화하므로 마이페이지 등이 새로고침되도록 알림.
            NotificationCenter.default.post(name: .bodyMeasurementDidChange, object: nil)
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
