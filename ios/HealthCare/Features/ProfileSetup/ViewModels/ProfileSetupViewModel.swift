import Foundation

@MainActor
final class ProfileSetupViewModel: ObservableObject {
    @Published var sex: String? = nil          // "MALE" | "FEMALE" | "OTHER"
    @Published var dateOfBirth: Date = ProfileSetupViewModel.defaultDateOfBirth
    @Published var heightText = ""
    @Published var weightText = ""
    @Published var activityLevel: String? = nil
    @Published var isLoading = false
    @Published var errorMessage: String?

    /// 디폴트 생년월일: 30년 전 (사용자가 안 건드리면 30대로 가정 — BMR 계산용)
    private static let defaultDateOfBirth: Date = {
        Calendar.current.date(byAdding: .year, value: -30, to: Date()) ?? Date()
    }()

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    var canProceedStep1: Bool {
        sex != nil
            && Double(heightText) != nil
            && Double(weightText) != nil
    }

    var canSubmit: Bool {
        activityLevel != nil
    }

    func submit(apiClient: APIClient, authState: AuthState) async {
        guard let height = Double(heightText), let weight = Double(weightText) else {
            errorMessage = String(localized: "profile.error.heightWeightInvalid")
            return
        }
        guard dateOfBirth < Date() else {
            errorMessage = String(localized: "profile.error.dobFuture")
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let req = ProfileSetupRequest(
                sex: sex,
                dateOfBirth: Self.dateFormatter.string(from: dateOfBirth),
                heightCm: height,
                weightKg: weight,
                activityLevel: activityLevel,
                onboardingCompleted: true
            )
            let body = try apiClient.encode(req)
            let _: UserProfile = try await apiClient.request(.updateProfile(body: body))
            authState.completeProfileSetup()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "profile.error.general")
        }
    }
}
