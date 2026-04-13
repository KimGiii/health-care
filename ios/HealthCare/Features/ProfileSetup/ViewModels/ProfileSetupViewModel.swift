import Foundation

@MainActor
final class ProfileSetupViewModel: ObservableObject {
    @Published var sex: String? = nil          // "MALE" | "FEMALE" | "OTHER"
    @Published var heightText = ""
    @Published var weightText = ""
    @Published var activityLevel: String? = nil
    @Published var isLoading = false
    @Published var errorMessage: String?

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
            errorMessage = "키와 몸무게를 올바르게 입력해주세요."
            return
        }

        isLoading = true
        errorMessage = nil
        defer { isLoading = false }

        do {
            let req = ProfileSetupRequest(
                sex: sex,
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
            errorMessage = "오류가 발생했습니다."
        }
    }
}
