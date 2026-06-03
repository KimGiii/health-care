import Foundation

enum ActivityLevelOption {
    static var all: [(label: String, value: String)] {
        [
            (String(localized: "mypage.activity.sedentary"), "SEDENTARY"),
            (String(localized: "mypage.activity.light"),     "LIGHTLY_ACTIVE"),
            (String(localized: "mypage.activity.moderate"),  "MODERATELY_ACTIVE"),
            (String(localized: "mypage.activity.active"),    "VERY_ACTIVE"),
            (String(localized: "mypage.activity.extra"),     "EXTRA_ACTIVE"),
        ]
    }

    static func label(for value: String?) -> String {
        switch value {
        case "SEDENTARY":
            return String(localized: "mypage.activity.sedentary")
        case "LIGHTLY_ACTIVE", "LIGHT":
            return String(localized: "mypage.activity.light")
        case "MODERATELY_ACTIVE", "MODERATE":
            return String(localized: "mypage.activity.moderate")
        case "VERY_ACTIVE", "ACTIVE":
            return String(localized: "mypage.activity.active")
        case "EXTRA_ACTIVE":
            return String(localized: "mypage.activity.extra")
        default:
            return "-"
        }
    }
}

struct UpdateProfileRequest: Encodable {
    var displayName: String?
    var sex: String?
    var dateOfBirth: String?   // yyyy-MM-dd (백엔드 LocalDate)
    var heightCm: Double?
    var weightKg: Double?
    var activityLevel: String?
    var calorieTarget: Int?
}

protocol MyPageProfileManaging: Sendable {
    func loadProfile() async throws -> UserProfile
    func updateProfile(_ request: UpdateProfileRequest) async throws -> UserProfile
    func deleteAccount() async throws
}

extension APIClient: MyPageProfileManaging {
    func loadProfile() async throws -> UserProfile {
        try await request(.getProfile)
    }

    func updateProfile(_ request: UpdateProfileRequest) async throws -> UserProfile {
        let body = try encode(request)
        return try await self.request(.updateProfile(body: body))
    }

    func deleteAccount() async throws {
        try await requestVoid(.deleteAccount)
    }
}

@MainActor
final class MyPageViewModel: ObservableObject {
    @Published var profile: UserProfile?
    @Published var isLoading = false
    @Published var errorMessage: String?

    // 편집 시트용 임시 값
    @Published var editDisplayName = ""
    @Published var editSex = ""
    @Published var editDateOfBirth: Date = MyPageViewModel.defaultDateOfBirth
    @Published var editHeightCm = ""
    @Published var editWeightKg = ""
    @Published var editActivityLevel = ""

    /// 디폴트 생년월일 — 30년 전.
    private static let defaultDateOfBirth: Date = {
        Calendar.current.date(byAdding: .year, value: -30, to: Date()) ?? Date()
    }()

    /// 백엔드 LocalDate(yyyy-MM-dd) <-> Swift Date 변환에 사용.
    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "Asia/Seoul")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()

    var activityLevelLabel: String {
        ActivityLevelOption.label(for: profile?.activityLevel)
    }

    var sexLabel: String {
        switch profile?.sex {
        case "MALE":   return String(localized: "profile.sex.male")
        case "FEMALE": return String(localized: "profile.sex.female")
        default:       return "-"
        }
    }

    func load(apiClient: any MyPageProfileManaging, authState: AuthState? = nil) async {
        guard !isLoading else { return }
        isLoading = true
        errorMessage = nil
        defer { isLoading = false }
        do {
            let fetched = try await apiClient.loadProfile()
            profile = fetched
            authState?.updatePremiumStatus(fetched.premium)
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "mypage.error.profileLoad")
        }
    }

    func populateEditFields() {
        guard let p = profile else { return }
        editDisplayName  = p.displayName
        editSex          = p.sex ?? ""
        editDateOfBirth  = p.dateOfBirth
            .flatMap { Self.dateFormatter.date(from: $0) } ?? Self.defaultDateOfBirth
        editHeightCm     = p.heightCm.map { String($0) } ?? ""
        editWeightKg     = p.weightKg.map { String($0) } ?? ""
        editActivityLevel = p.activityLevel ?? ""
    }

    func saveProfile(apiClient: any MyPageProfileManaging) async {
        isLoading = true
        defer { isLoading = false }
        do {
            let req = UpdateProfileRequest(
                displayName:   editDisplayName.isEmpty ? nil : editDisplayName,
                sex:           editSex.isEmpty ? nil : editSex,
                dateOfBirth:   Self.dateFormatter.string(from: editDateOfBirth),
                heightCm:      Double(editHeightCm),
                weightKg:      Double(editWeightKg),
                activityLevel: editActivityLevel.isEmpty ? nil : editActivityLevel
            )
            let updated = try await apiClient.updateProfile(req)
            profile = updated
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "mypage.error.profileSave")
        }
    }

    func deleteAccount(apiClient: any MyPageProfileManaging, authState: AuthState) async {
        isLoading = true
        defer { isLoading = false }
        do {
            try await apiClient.deleteAccount()
            authState.setUnauthenticated()
        } catch let error as APIError {
            errorMessage = error.errorDescription
        } catch {
            errorMessage = String(localized: "mypage.error.deleteAccount")
        }
    }

    func logout(authState: AuthState) {
        authState.setUnauthenticated()
    }
}
