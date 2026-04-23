import Foundation

enum ActivityLevelOption {
    static let all: [(label: String, value: String)] = [
        ("거의 안 움직임", "SEDENTARY"),
        ("가벼운 활동", "LIGHTLY_ACTIVE"),
        ("보통 활동", "MODERATELY_ACTIVE"),
        ("활발한 활동", "VERY_ACTIVE"),
        ("매우 활발", "EXTRA_ACTIVE"),
    ]

    static func label(for value: String?) -> String {
        switch value {
        case "SEDENTARY":
            return "거의 안 움직임"
        case "LIGHTLY_ACTIVE", "LIGHT":
            return "가벼운 활동"
        case "MODERATELY_ACTIVE", "MODERATE":
            return "보통 활동"
        case "VERY_ACTIVE", "ACTIVE":
            return "활발한 활동"
        case "EXTRA_ACTIVE":
            return "매우 활발"
        default:
            return "-"
        }
    }
}

struct UpdateProfileRequest: Encodable {
    var displayName: String?
    var sex: String?
    var heightCm: Double?
    var weightKg: Double?
    var activityLevel: String?
    var calorieTarget: Int?
}

@MainActor
final class MyPageViewModel: ObservableObject {
    @Published var profile: UserProfile?
    @Published var isLoading = false
    @Published var errorMessage: String?

    // 편집 시트용 임시 값
    @Published var editDisplayName = ""
    @Published var editSex = ""
    @Published var editHeightCm = ""
    @Published var editWeightKg = ""
    @Published var editActivityLevel = ""

    var activityLevelLabel: String {
        ActivityLevelOption.label(for: profile?.activityLevel)
    }

    var sexLabel: String {
        switch profile?.sex {
        case "MALE":   return "남성"
        case "FEMALE": return "여성"
        default:       return "-"
        }
    }

    func load(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let fetched: UserProfile = try await apiClient.request(.getProfile)
            profile = fetched
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "프로필을 불러오지 못했습니다."
        }
    }

    func populateEditFields() {
        guard let p = profile else { return }
        editDisplayName  = p.displayName
        editSex          = p.sex ?? ""
        editHeightCm     = p.heightCm.map { String($0) } ?? ""
        editWeightKg     = p.weightKg.map { String($0) } ?? ""
        editActivityLevel = p.activityLevel ?? ""
    }

    func saveProfile(apiClient: APIClient) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let req = UpdateProfileRequest(
                displayName:   editDisplayName.isEmpty ? nil : editDisplayName,
                sex:           editSex.isEmpty ? nil : editSex,
                heightCm:      Double(editHeightCm),
                weightKg:      Double(editWeightKg),
                activityLevel: editActivityLevel.isEmpty ? nil : editActivityLevel
            )
            let body = try apiClient.encode(req)
            let updated: UserProfile = try await apiClient.request(.updateProfile(body: body))
            profile = updated
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "프로필 수정 중 오류가 발생했습니다."
        }
    }

    func deleteAccount(apiClient: APIClient, authState: AuthState) async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await apiClient.requestVoid(.deleteAccount)
            authState.setUnauthenticated()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = "계정 삭제 중 오류가 발생했습니다."
        }
    }

    func logout(authState: AuthState) {
        authState.setUnauthenticated()
    }
}
