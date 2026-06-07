import Foundation

struct ProfileSetupRequest: Encodable {
    let sex: String?
    let dateOfBirth: String?   // yyyy-MM-dd (백엔드 LocalDate)
    let heightCm: Double
    let weightKg: Double
    let activityLevel: String?
    let onboardingCompleted: Bool
}

struct UserProfile: Decodable {
    let id: Int
    let email: String
    let displayName: String
    let sex: String?
    let dateOfBirth: String?   // yyyy-MM-dd
    let heightCm: Double?
    let weightKg: Double?
    let activityLevel: String?
    let onboardingCompleted: Bool
    let isPremium: Bool?  // 백엔드 미응답 시 false 취급

    // 백엔드 NutritionCalculator가 자동 계산해 저장하는 일일 권장량.
    // 회원가입 직후, 프로필/목표 변경 시 자동 갱신됨.
    let calorieTarget: Int?
    let proteinTargetG: Int?
    let carbTargetG: Int?
    let fatTargetG: Int?

    var premium: Bool { isPremium ?? false }
}
